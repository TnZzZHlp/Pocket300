package com.yamibo.pocket300.data.download

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

sealed interface StoredPostDownload {
    data object Missing : StoredPostDownload

    data class Complete(val download: DownloadedPost) : StoredPostDownload

    data class Invalid(val reason: String) : StoredPostDownload
}

class PostDownloadStaging internal constructor(
    val key: PostDownloadKey,
    val directory: File,
) {
    val imageDirectory: File = File(directory, IMAGE_DIRECTORY_NAME)

    fun imageFile(index: Int): File {
        require(index >= 0) { "Image index must not be negative" }
        return File(imageDirectory, "%04d.img".format(index + 1))
    }

    internal companion object {
        const val IMAGE_DIRECTORY_NAME = "images"
    }
}

fun interface PostDownloadImageDecoderValidator {
    fun canDecode(file: File): Boolean
}

/**
 * Pure file-system store. A final directory is readable only when its manifest and every image
 * pass size, digest, path-containment, and image-signature validation.
 */
class PostDownloadFileStore(
    val rootDirectory: File,
    private val codec: PostDownloadManifestCodec = PostDownloadManifestCodec(),
    private val decoderValidator: PostDownloadImageDecoderValidator =
        PostDownloadImageDecoderValidator { true },
) {
    private val queueDirectory = File(rootDirectory, QUEUE_DIRECTORY_NAME)
    private val stagingDirectory = File(rootDirectory, STAGING_DIRECTORY_NAME)

    init {
        ensureDirectory(rootDirectory)
        check(ensureSpecialDirectory(queueDirectory)) {
            "Could not prepare the download queue directory"
        }
        check(ensureSpecialDirectory(stagingDirectory)) {
            "Could not prepare the download staging directory"
        }
    }

    @Synchronized
    fun persistRequest(request: PostDownloadRequest) {
        persistRequest(request, PostDownloadRequestState.PENDING)
    }

    @Synchronized
    fun persistFailedRequest(request: PostDownloadRequest) {
        persistRequest(request, PostDownloadRequestState.FAILED)
    }

    @Synchronized
    fun loadQueuedRequest(key: PostDownloadKey): PostDownloadRequest? {
        return loadStoredRequest(key)
            ?.takeIf { it.state == PostDownloadRequestState.PENDING }
            ?.request
    }

    @Synchronized
    fun loadQueuedRequests(): List<PostDownloadRequest> =
        loadRequests(PostDownloadRequestState.PENDING)

    @Synchronized
    fun loadFailedRequests(): List<PostDownloadRequest> =
        loadRequests(PostDownloadRequestState.FAILED)

    private fun loadRequests(state: PostDownloadRequestState): List<PostDownloadRequest> =
        requireDirectoryFiles(queueDirectory)
        .asSequence()
        .filter { it.isFile && it.extension == QUEUE_FILE_EXTENSION }
        .sortedBy { it.name }
        .mapNotNull { file ->
            val stored = runCatching {
                codec.decodeStoredRequest(file.readText(StandardCharsets.UTF_8))
            }.getOrNull()
            if (
                stored == null ||
                queueFile(stored.request.key).canonicalFile != file.canonicalFile
            ) {
                check(file.delete()) { "Could not remove invalid download queue file" }
                null
            } else {
                stored
                    .takeIf { it.state == state }
                    ?.request
            }
        }
        .distinctBy(PostDownloadRequest::key)
        .sortedBy(PostDownloadRequest::requestedAt)
        .toList()

    private fun loadStoredRequest(key: PostDownloadKey): StoredPostDownloadRequest? {
        val file = queueFile(key)
        if (!file.isFile) return null
        return runCatching {
            codec.decodeStoredRequest(file.readText(StandardCharsets.UTF_8))
        }
            .getOrNull()
            ?.takeIf { it.request.key == key }
    }

    private fun persistRequest(
        request: PostDownloadRequest,
        state: PostDownloadRequestState,
    ) {
        writeUtf8Atomically(queueFile(request.key), codec.encodeRequest(request, state))
    }

    @Synchronized
    fun removeQueuedRequest(key: PostDownloadKey): Boolean {
        val file = queueFile(key)
        return !file.exists() || file.delete()
    }

    @Synchronized
    fun createStaging(key: PostDownloadKey): PostDownloadStaging {
        val directory = File(
            stagingDirectory,
            "${key.threadId}-${key.postId}-${UUID.randomUUID()}",
        )
        check(directory.mkdirs()) { "Could not create download staging directory" }
        return try {
            PostDownloadStaging(key, directory).also {
                ensureDirectory(it.imageDirectory)
            }
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        }
    }

    /**
     * Writes the manifest only after every image is present and valid, validates the complete
     * staging tree, then renames that tree into its final location.
     */
    @Synchronized
    fun commit(
        staging: PostDownloadStaging,
        request: PostDownloadRequest,
        images: List<PostDownloadImage>,
        completedAt: Long = System.currentTimeMillis(),
    ): DownloadedPost {
        require(staging.key == request.key) { "Staging directory belongs to another post" }
        requireIsDirectChild(stagingDirectory, staging.directory)
        require(images.map(PostDownloadImage::remoteUrl) == request.remoteImageUrls) {
            "Every requested image must be downloaded exactly once and in reading order"
        }
        images.forEach { validateStagedImage(staging, it) }

        val manifest = PostDownloadManifest(
            snapshot = request.snapshot,
            hasText = request.hasText,
            images = images,
            requestedAt = request.requestedAt,
            completedAt = completedAt.coerceAtLeast(request.requestedAt),
        )
        val manifestFile = File(staging.directory, MANIFEST_FILE_NAME)
        check(!manifestFile.exists()) { "Staging manifest must be written exactly once" }
        writeUtf8Atomically(manifestFile, codec.encodeManifest(manifest))

        val staged = validateDirectory(staging.directory, request.key)
        check(staged is StoredPostDownload.Complete) {
            (staged as? StoredPostDownload.Invalid)?.reason ?: "Staged download is incomplete"
        }

        val finalDirectory = postDirectory(request.key)
        when (val current = inspect(request.key)) {
            is StoredPostDownload.Complete -> {
                staging.directory.deleteRecursively()
                return current.download
            }

            is StoredPostDownload.Invalid -> {
                check(finalDirectory.deleteRecursively()) {
                    "Could not remove invalid previous download: ${current.reason}"
                }
            }

            StoredPostDownload.Missing -> Unit
        }
        ensureDirectory(finalDirectory.parentFile)
        moveDirectoryAtomically(staging.directory, finalDirectory)
        return when (val committed = inspect(request.key)) {
            is StoredPostDownload.Complete -> committed.download
            is StoredPostDownload.Invalid -> error(
                "Committed download failed validation: ${committed.reason}",
            )

            StoredPostDownload.Missing -> error("Committed download disappeared")
        }
    }

    @Synchronized
    fun inspect(key: PostDownloadKey): StoredPostDownload =
        validateDirectory(postDirectory(key), key)

    @Synchronized
    fun read(key: PostDownloadKey): DownloadedPost? =
        (inspect(key) as? StoredPostDownload.Complete)?.download

    @Synchronized
    fun listCompleted(): List<DownloadedPost> =
        scanCompleted(cleanInvalid = false).downloads

    @Synchronized
    fun listCompletedAndCleanupInvalid(): List<DownloadedPost> {
        val scan = scanCompleted(cleanInvalid = true)
        check(scan.cleanupSucceeded) { "Could not remove invalid post downloads" }
        return scan.downloads
    }

    @Synchronized
    fun cleanupInvalidDownloads(): Boolean =
        scanCompleted(cleanInvalid = true).cleanupSucceeded

    private fun scanCompleted(cleanInvalid: Boolean): CompletedDownloadScan {
        var success = true
        val downloads = mutableListOf<DownloadedPost>()
        requireDirectoryFiles(rootDirectory)
            .filter { it.name.toIntOrNull()?.let { id -> id > 0 } == true }
            .forEach threadLoop@{ threadDirectory ->
                if (!threadDirectory.isDirectory) {
                    if (cleanInvalid && !threadDirectory.deleteRecursively()) success = false
                    return@threadLoop
                }
                val postCandidates = threadDirectory.listFiles()
                if (postCandidates == null) {
                    if (!cleanInvalid || !threadDirectory.deleteRecursively()) success = false
                    return@threadLoop
                }
                postCandidates
                    .filter { it.name.toIntOrNull()?.let { id -> id > 0 } == true }
                    .forEach candidateLoop@{ candidate ->
                        if (!candidate.isDirectory) {
                            if (cleanInvalid && !candidate.deleteRecursively()) success = false
                            return@candidateLoop
                        }
                        val key = PostDownloadKey(
                            threadDirectory.name.toInt(),
                            candidate.name.toInt(),
                        )
                        when (val stored = validateDirectory(candidate, key)) {
                            is StoredPostDownload.Complete -> downloads += stored.download
                            is StoredPostDownload.Invalid -> if (
                                cleanInvalid &&
                                !candidate.deleteRecursively()
                            ) {
                                success = false
                            }
                            StoredPostDownload.Missing -> Unit
                        }
                    }
                threadDirectory
                    .takeIf { it.isDirectory && it.list().isNullOrEmpty() }
                    ?.delete()
            }
        return CompletedDownloadScan(
            downloads = downloads.sortedByDescending { it.manifest.completedAt },
            cleanupSucceeded = success,
        )
    }

    @Synchronized
    fun delete(key: PostDownloadKey): Boolean {
        val finalDirectory = postDirectory(key)
        val deletedDownload = !finalDirectory.exists() || finalDirectory.deleteRecursively()
        val deletedQueue = removeQueuedRequest(key)
        val deletedStaging = deleteStaging(key)
        finalDirectory.parentFile?.takeIf { it.isDirectory && it.list().isNullOrEmpty() }?.delete()
        return deletedDownload && deletedQueue && deletedStaging
    }

    @Synchronized
    fun deleteAll(): Boolean {
        var success = true
        val children = rootDirectory.listFiles() ?: return false
        children.forEach { child ->
            if (!child.deleteRecursively()) success = false
        }
        if (!ensureSpecialDirectory(queueDirectory)) success = false
        if (!ensureSpecialDirectory(stagingDirectory)) success = false
        return success
    }

    @Synchronized
    fun discard(staging: PostDownloadStaging) {
        requireIsDirectChild(stagingDirectory, staging.directory)
        staging.directory.deleteRecursively()
    }

    @Synchronized
    fun cleanupStaging(): Boolean {
        if (!ensureSpecialDirectory(stagingDirectory)) return false
        var success = true
        val children = stagingDirectory.listFiles() ?: return false
        children.forEach { child ->
            if (!child.deleteRecursively()) success = false
        }
        return success
    }

    @Synchronized
    fun cleanupQueueArtifacts(): Boolean {
        if (!ensureSpecialDirectory(queueDirectory)) return false
        val children = queueDirectory.listFiles() ?: return false
        var success = true
        children
            .filterNot { it.isFile && it.extension == QUEUE_FILE_EXTENSION }
            .forEach { child ->
                if (!child.deleteRecursively()) success = false
            }
        return success
    }

    private fun validateDirectory(directory: File, expectedKey: PostDownloadKey): StoredPostDownload {
        if (!directory.exists()) return StoredPostDownload.Missing
        if (!directory.isDirectory) return StoredPostDownload.Invalid("Download path is not a directory")
        return try {
            val manifestFile = File(directory, MANIFEST_FILE_NAME)
            require(manifestFile.isFile) { "Download manifest is missing" }
            val manifest = codec.decodeManifest(manifestFile.readText(StandardCharsets.UTF_8))
            require(manifest.key == expectedKey) { "Download manifest belongs to another post" }
            manifest.images.forEach { image ->
                val file = resolveContainedFile(directory, image.relativePath)
                require(file.isFile) { "Downloaded image is missing: ${image.relativePath}" }
                require(file.length() == image.byteCount) {
                    "Downloaded image size differs from its manifest: ${image.relativePath}"
                }
                require(fileSha256(file) == image.sha256) {
                    "Downloaded image digest differs from its manifest: ${image.relativePath}"
                }
                require(hasSupportedImageSignature(file)) {
                    "Downloaded image has an unsupported or invalid signature: ${image.relativePath}"
                }
                require(runCatching { decoderValidator.canDecode(file) }.getOrDefault(false)) {
                    "Downloaded image cannot be decoded: ${image.relativePath}"
                }
            }
            StoredPostDownload.Complete(DownloadedPost(manifest, directory))
        } catch (error: Exception) {
            StoredPostDownload.Invalid(error.message ?: "Download validation failed")
        }
    }

    private fun validateStagedImage(staging: PostDownloadStaging, image: PostDownloadImage) {
        val file = resolveContainedFile(staging.directory, image.relativePath)
        require(file.isFile) { "Staged image is missing: ${image.relativePath}" }
        require(file.length() == image.byteCount) {
            "Staged image size differs from its metadata: ${image.relativePath}"
        }
        require(fileSha256(file) == image.sha256) {
            "Staged image digest differs from its metadata: ${image.relativePath}"
        }
        require(hasSupportedImageSignature(file)) {
            "Staged image has an unsupported or invalid signature: ${image.relativePath}"
        }
        require(runCatching { decoderValidator.canDecode(file) }.getOrDefault(false)) {
            "Staged image cannot be decoded: ${image.relativePath}"
        }
    }

    private fun postDirectory(key: PostDownloadKey): File = resolveContainedFile(
        rootDirectory,
        "${key.threadId}${File.separator}${key.postId}",
    )

    private fun queueFile(key: PostDownloadKey): File =
        File(queueDirectory, "${key.threadId}-${key.postId}.$QUEUE_FILE_EXTENSION")

    private fun deleteStaging(key: PostDownloadKey): Boolean {
        if (!stagingDirectory.isDirectory) return false
        val prefix = "${key.threadId}-${key.postId}-"
        var success = true
        val children = stagingDirectory.listFiles() ?: return false
        children
            .filter { it.name.startsWith(prefix) }
            .forEach { child ->
                if (!child.deleteRecursively()) success = false
            }
        return success
    }

    private companion object {
        const val MANIFEST_FILE_NAME = "manifest.json"
        const val QUEUE_DIRECTORY_NAME = ".queue"
        const val STAGING_DIRECTORY_NAME = ".staging"
        const val QUEUE_FILE_EXTENSION = "json"
    }

    private data class CompletedDownloadScan(
        val downloads: List<DownloadedPost>,
        val cleanupSucceeded: Boolean,
    )
}

internal fun fileSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun writeUtf8Atomically(destination: File, content: String) {
    ensureDirectory(destination.parentFile)
    val temporary = File(
        destination.parentFile,
        ".${destination.name}.${UUID.randomUUID()}.tmp",
    )
    try {
        FileOutputStream(temporary).use { output ->
            output.write(content.toByteArray(StandardCharsets.UTF_8))
            output.fd.sync()
        }
        moveFileAtomically(temporary, destination)
    } finally {
        temporary.delete()
    }
}

private fun moveFileAtomically(source: File, destination: File) {
    try {
        Files.move(
            source.toPath(),
            destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        throw IOException("Atomic file replacement is not supported")
    }
}

private fun moveDirectoryAtomically(source: File, destination: File) {
    require(!destination.exists()) { "Destination directory already exists" }
    try {
        Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        throw IOException("Atomic directory move is not supported")
    }
}

private fun resolveContainedFile(root: File, relativePath: String): File {
    require(relativePath.isNotBlank()) { "Downloaded image path is blank" }
    val relative = File(relativePath)
    require(!relative.isAbsolute) { "Downloaded image path must be relative" }
    val canonicalRoot = root.canonicalFile
    val resolved = File(canonicalRoot, relativePath).canonicalFile
    val rootPrefix = canonicalRoot.path + File.separator
    require(resolved.path.startsWith(rootPrefix)) { "Downloaded image path escapes its post directory" }
    return resolved
}

private fun requireIsDirectChild(parent: File, child: File) {
    require(child.canonicalFile.parentFile == parent.canonicalFile) {
        "Staging directory is outside the download staging root"
    }
}

private fun ensureDirectory(directory: File?) {
    requireNotNull(directory) { "Directory must have a parent" }
    check(directory.isDirectory || directory.mkdirs()) { "Could not create ${directory.path}" }
}

private fun ensureSpecialDirectory(directory: File): Boolean {
    if (directory.isDirectory) return true
    if (directory.exists() && !directory.deleteRecursively()) return false
    return directory.mkdirs()
}

private fun requireDirectoryFiles(directory: File): Array<File> {
    check(directory.isDirectory) { "${directory.name} is not a directory" }
    return checkNotNull(directory.listFiles()) {
        "Could not list ${directory.name}"
    }
}

private fun hasSupportedImageSignature(file: File): Boolean {
    val header = ByteArray(16)
    val count = file.inputStream().use { it.read(header) }
    if (count < 4) return false
    return header.startsWith(0xFF, 0xD8, 0xFF) ||
        header.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) ||
        header.asciiStartsWith("GIF87a") ||
        header.asciiStartsWith("GIF89a") ||
        header.asciiStartsWith("BM") ||
        (header.asciiStartsWith("RIFF") && header.asciiAt(8, "WEBP"))
}

private fun ByteArray.startsWith(vararg expected: Int): Boolean =
    expected.indices.all { index -> index < size && this[index].toInt() and 0xFF == expected[index] }

private fun ByteArray.asciiStartsWith(value: String): Boolean = asciiAt(0, value)

private fun ByteArray.asciiAt(offset: Int, value: String): Boolean {
    if (offset + value.length > size) return false
    return value.indices.all { index -> this[offset + index].toInt().toChar() == value[index] }
}
