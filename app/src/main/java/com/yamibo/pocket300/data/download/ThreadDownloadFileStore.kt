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

sealed interface StoredThreadDownload {
    data object Missing : StoredThreadDownload

    data class Complete(val download: DownloadedThread) : StoredThreadDownload

    data class Invalid(val reason: String) : StoredThreadDownload
}

class ThreadDownloadStaging internal constructor(
    val key: ThreadDownloadKey,
    val directory: File,
) {
    val imageDirectory: File = File(directory, IMAGE_DIRECTORY_NAME)

    fun imageFile(index: Int): File {
        require(index >= 0) { "Image index must not be negative" }
        return File(imageDirectory, "%05d.img".format(index + 1))
    }

    internal companion object {
        const val IMAGE_DIRECTORY_NAME = "images"
    }
}

fun interface ThreadDownloadImageDecoderValidator {
    fun canDecode(file: File): Boolean
}

/**
 * Pure file-system store for complete thread snapshots.
 *
 * Each commit becomes an immutable version directory. A tiny atomically replaced `current` file is
 * the only mutable publication point, so refreshing a thread never destroys its readable previous
 * version before the replacement has passed full validation.
 */
class ThreadDownloadFileStore(
    val rootDirectory: File,
    private val codec: ThreadDownloadManifestCodec = ThreadDownloadManifestCodec(),
    private val decoderValidator: ThreadDownloadImageDecoderValidator =
        ThreadDownloadImageDecoderValidator { true },
) {
    private val queueDirectory = File(rootDirectory, QUEUE_DIRECTORY_NAME)
    private val queuePausedFile = File(queueDirectory, QUEUE_PAUSED_FILE_NAME)
    private val stagingDirectory = File(rootDirectory, STAGING_DIRECTORY_NAME)

    init {
        ensureDirectory(rootDirectory)
        check(ensureSpecialDirectory(queueDirectory)) {
            "Could not prepare the thread download queue directory"
        }
        check(ensureSpecialDirectory(stagingDirectory)) {
            "Could not prepare the thread download staging directory"
        }
    }

    @Synchronized
    fun persistRequest(
        request: ThreadDownloadRequest,
        queueOrder: Long = request.requestedAt,
    ) {
        persistRequest(request, ThreadDownloadRequestState.PENDING, queueOrder)
    }

    @Synchronized
    fun persistFailedRequest(request: ThreadDownloadRequest) {
        persistRequest(request, ThreadDownloadRequestState.FAILED, request.requestedAt)
    }

    @Synchronized
    fun loadQueuedRequest(key: ThreadDownloadKey): ThreadDownloadRequest? =
        loadStoredRequest(key)
            ?.takeIf { it.state == ThreadDownloadRequestState.PENDING }
            ?.request

    @Synchronized
    fun loadQueuedRequests(): List<ThreadDownloadRequest> =
        loadQueuedEntries().map(ThreadDownloadQueueEntry::request)

    @Synchronized
    internal fun loadQueuedEntries(): List<ThreadDownloadQueueEntry> =
        loadRequests(ThreadDownloadRequestState.PENDING).map { stored ->
            ThreadDownloadQueueEntry(
                request = stored.request,
                order = stored.queueOrder,
            )
        }

    @Synchronized
    fun loadFailedRequests(): List<ThreadDownloadRequest> =
        loadRequests(ThreadDownloadRequestState.FAILED).map(StoredThreadDownloadRequest::request)

    @Synchronized
    fun isQueuePaused(): Boolean = queuePausedFile.isFile

    @Synchronized
    fun setQueuePaused(paused: Boolean): Boolean {
        if (paused) {
            if (queuePausedFile.exists() && !queuePausedFile.isFile) {
                if (!queuePausedFile.deleteRecursively()) return false
            }
            writeUtf8Atomically(queuePausedFile, QUEUE_PAUSED_MARKER)
            return true
        }
        return !queuePausedFile.exists() || queuePausedFile.deleteRecursively()
    }

    private fun loadRequests(state: ThreadDownloadRequestState): List<StoredThreadDownloadRequest> =
        requireDirectoryFiles(queueDirectory)
            .asSequence()
            .filter { it.isFile && it.extension == QUEUE_FILE_EXTENSION }
            .sortedBy(File::getName)
            .mapNotNull { file ->
                val stored = runCatching {
                    codec.decodeStoredRequest(file.readText(StandardCharsets.UTF_8))
                }.getOrNull()
                if (
                    stored == null ||
                    queueFile(stored.request.key).canonicalFile != file.canonicalFile
                ) {
                    check(file.delete()) { "Could not remove invalid thread download queue file" }
                    null
                } else {
                    stored.takeIf { it.state == state }
                }
            }
            .distinctBy { it.request.key }
            .sortedWith(
                compareBy<StoredThreadDownloadRequest>(StoredThreadDownloadRequest::queueOrder)
                    .thenBy { it.request.requestedAt }
                    .thenBy { it.request.key.threadId },
            )
            .toList()

    private fun loadStoredRequest(key: ThreadDownloadKey): StoredThreadDownloadRequest? {
        val file = queueFile(key)
        if (!file.isFile) return null
        return runCatching {
            codec.decodeStoredRequest(file.readText(StandardCharsets.UTF_8))
        }.getOrNull()?.takeIf { it.request.key == key }
    }

    private fun persistRequest(
        request: ThreadDownloadRequest,
        state: ThreadDownloadRequestState,
        queueOrder: Long,
    ) {
        require(queueOrder >= 0) { "Thread download queue order must not be negative" }
        writeUtf8Atomically(queueFile(request.key), codec.encodeRequest(request, state, queueOrder))
    }

    @Synchronized
    fun removeQueuedRequest(key: ThreadDownloadKey): Boolean {
        val file = queueFile(key)
        return !file.exists() || file.delete()
    }

    @Synchronized
    fun createStaging(key: ThreadDownloadKey): ThreadDownloadStaging {
        val directory = File(
            stagingDirectory,
            "${key.threadId}-${UUID.randomUUID()}",
        )
        check(directory.mkdirs()) { "Could not create thread download staging directory" }
        return try {
            ThreadDownloadStaging(key, directory).also {
                ensureDirectory(it.imageDirectory)
            }
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        }
    }

    @Synchronized
    fun commit(
        staging: ThreadDownloadStaging,
        request: ThreadDownloadRequest,
        snapshot: ThreadDownloadSnapshot,
        images: List<ThreadDownloadImage>,
        completedAt: Long = System.currentTimeMillis(),
    ): DownloadedThread {
        require(staging.key == request.key) { "Staging directory belongs to another thread" }
        require(snapshot.key == request.key) { "Snapshot belongs to another thread" }
        requireIsDirectChild(stagingDirectory, staging.directory)
        require(images.map(ThreadDownloadImage::remoteUrl) == threadImageUrls(snapshot.posts)) {
            "Every thread image must be downloaded exactly once and in reading order"
        }
        images.forEach { validateStagedImage(staging, it) }

        val manifest = ThreadDownloadManifest(
            snapshot = snapshot,
            images = images,
            requestedAt = request.requestedAt,
            completedAt = completedAt.coerceAtLeast(request.requestedAt),
        )
        val manifestFile = File(staging.directory, MANIFEST_FILE_NAME)
        check(!manifestFile.exists()) { "Staging manifest must be written exactly once" }
        writeUtf8Atomically(manifestFile, codec.encodeManifest(manifest))

        val staged = validateVersionDirectory(staging.directory, request.key)
        check(staged is StoredThreadDownload.Complete) {
            (staged as? StoredThreadDownload.Invalid)?.reason ?: "Staged thread is incomplete"
        }

        val threadDirectory = threadDirectory(request.key)
        val versionsDirectory = File(threadDirectory, VERSIONS_DIRECTORY_NAME)
        ensureDirectory(versionsDirectory)
        val generation = UUID.randomUUID().toString()
        val versionDirectory = File(versionsDirectory, generation)
        moveDirectoryAtomically(staging.directory, versionDirectory)

        val moved = validateVersionDirectory(versionDirectory, request.key)
        if (moved !is StoredThreadDownload.Complete) {
            versionDirectory.deleteRecursively()
            error(
                (moved as? StoredThreadDownload.Invalid)?.reason
                    ?: "Moved thread download disappeared",
            )
        }

        val pointer = File(threadDirectory, CURRENT_POINTER_FILE_NAME)
        val previousGeneration = readGeneration(pointer)
        try {
            writeUtf8Atomically(pointer, generation)
            val committed = inspect(request.key)
            check(committed is StoredThreadDownload.Complete) {
                (committed as? StoredThreadDownload.Invalid)?.reason
                    ?: "Published thread download disappeared"
            }
            cleanupOldVersions(versionsDirectory, generation)
            return committed.download
        } catch (error: Exception) {
            if (previousGeneration == null) {
                pointer.delete()
            } else {
                runCatching { writeUtf8Atomically(pointer, previousGeneration) }
            }
            versionDirectory.deleteRecursively()
            throw error
        }
    }

    @Synchronized
    fun inspect(key: ThreadDownloadKey): StoredThreadDownload =
        validateThreadDirectory(threadDirectory(key), key)

    @Synchronized
    fun read(key: ThreadDownloadKey): DownloadedThread? =
        (inspect(key) as? StoredThreadDownload.Complete)?.download

    @Synchronized
    fun listCompleted(): List<DownloadedThread> =
        scanCompleted(cleanInvalid = false).downloads

    @Synchronized
    fun listCompletedAndCleanupInvalid(): List<DownloadedThread> {
        val scan = scanCompleted(cleanInvalid = true)
        check(scan.cleanupSucceeded) { "Could not remove invalid thread downloads" }
        return scan.downloads
    }

    @Synchronized
    fun cleanupInvalidDownloads(): Boolean =
        scanCompleted(cleanInvalid = true).cleanupSucceeded

    private fun scanCompleted(cleanInvalid: Boolean): CompletedDownloadScan {
        var success = true
        val downloads = mutableListOf<DownloadedThread>()
        requireDirectoryFiles(rootDirectory)
            .filter { it.name.toIntOrNull()?.let { id -> id > 0 } == true }
            .forEach { candidate ->
                if (!candidate.isDirectory) {
                    if (cleanInvalid && !candidate.deleteRecursively()) success = false
                    return@forEach
                }
                val key = ThreadDownloadKey(candidate.name.toInt())
                when (val stored = validateThreadDirectory(candidate, key)) {
                    is StoredThreadDownload.Complete -> {
                        downloads += stored.download
                        if (cleanInvalid) {
                            val generation = readGeneration(
                                File(candidate, CURRENT_POINTER_FILE_NAME),
                            )
                            val versions = File(candidate, VERSIONS_DIRECTORY_NAME)
                            if (
                                generation == null ||
                                !cleanupOldVersions(versions, generation)
                            ) {
                                success = false
                            }
                        }
                    }

                    is StoredThreadDownload.Invalid -> if (
                        cleanInvalid &&
                        !candidate.deleteRecursively()
                    ) {
                        success = false
                    }

                    StoredThreadDownload.Missing -> Unit
                }
            }
        return CompletedDownloadScan(
            downloads = downloads.sortedByDescending { it.manifest.completedAt },
            cleanupSucceeded = success,
        )
    }

    @Synchronized
    fun delete(key: ThreadDownloadKey): Boolean {
        val deletedDownload =
            !threadDirectory(key).exists() || threadDirectory(key).deleteRecursively()
        val deletedQueue = removeQueuedRequest(key)
        val deletedStaging = deleteStaging(key)
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
    fun discard(staging: ThreadDownloadStaging) {
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
            .filterNot {
                it.isFile &&
                    (it.extension == QUEUE_FILE_EXTENSION || it.name == QUEUE_PAUSED_FILE_NAME)
            }
            .forEach { child ->
                if (!child.deleteRecursively()) success = false
            }
        return success
    }

    private fun validateThreadDirectory(
        directory: File,
        expectedKey: ThreadDownloadKey,
    ): StoredThreadDownload {
        if (!directory.exists()) return StoredThreadDownload.Missing
        if (!directory.isDirectory) {
            return StoredThreadDownload.Invalid("Thread download path is not a directory")
        }
        return try {
            val generation = requireNotNull(
                readGeneration(File(directory, CURRENT_POINTER_FILE_NAME)),
            ) { "Current thread download pointer is missing or invalid" }
            val versionsDirectory = File(directory, VERSIONS_DIRECTORY_NAME)
            require(versionsDirectory.isDirectory) {
                "Thread download versions directory is missing"
            }
            val versionDirectory = resolveContainedFile(versionsDirectory, generation)
            require(
                requireNotNull(versionDirectory.parentFile).canonicalFile ==
                    versionsDirectory.canonicalFile,
            ) {
                "Current thread download pointer escapes the versions directory"
            }
            when (val version = validateVersionDirectory(versionDirectory, expectedKey)) {
                StoredThreadDownload.Missing ->
                    StoredThreadDownload.Invalid("Current thread download version is missing")
                else -> version
            }
        } catch (error: Exception) {
            StoredThreadDownload.Invalid(error.message ?: "Thread download validation failed")
        }
    }

    private fun validateVersionDirectory(
        directory: File,
        expectedKey: ThreadDownloadKey,
    ): StoredThreadDownload {
        if (!directory.exists()) return StoredThreadDownload.Missing
        if (!directory.isDirectory) {
            return StoredThreadDownload.Invalid("Thread version path is not a directory")
        }
        return try {
            val manifestFile = File(directory, MANIFEST_FILE_NAME)
            require(manifestFile.isFile) { "Thread download manifest is missing" }
            val manifest = codec.decodeManifest(manifestFile.readText(StandardCharsets.UTF_8))
            require(manifest.key == expectedKey) {
                "Thread download manifest belongs to another thread"
            }
            require(
                manifest.images.map(ThreadDownloadImage::remoteUrl) ==
                    threadImageUrls(manifest.snapshot.posts),
            ) { "Thread download manifest does not cover every post image" }
            manifest.images.forEach { image ->
                validateImageFile(directory, image)
            }
            StoredThreadDownload.Complete(DownloadedThread(manifest, directory))
        } catch (error: Exception) {
            StoredThreadDownload.Invalid(error.message ?: "Thread download validation failed")
        }
    }

    private fun validateStagedImage(
        staging: ThreadDownloadStaging,
        image: ThreadDownloadImage,
    ) {
        validateImageFile(staging.directory, image)
    }

    private fun validateImageFile(directory: File, image: ThreadDownloadImage) {
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

    private fun threadDirectory(key: ThreadDownloadKey): File =
        resolveContainedFile(rootDirectory, key.threadId.toString())

    private fun queueFile(key: ThreadDownloadKey): File =
        File(queueDirectory, "${key.threadId}.$QUEUE_FILE_EXTENSION")

    private fun deleteStaging(key: ThreadDownloadKey): Boolean {
        if (!stagingDirectory.isDirectory) return false
        val prefix = "${key.threadId}-"
        var success = true
        val children = stagingDirectory.listFiles() ?: return false
        children.filter { it.name.startsWith(prefix) }.forEach { child ->
            if (!child.deleteRecursively()) success = false
        }
        return success
    }

    private fun cleanupOldVersions(versionsDirectory: File, currentGeneration: String): Boolean {
        if (!versionsDirectory.isDirectory) return false
        val children = versionsDirectory.listFiles() ?: return false
        var success = true
        children.filter { it.name != currentGeneration }.forEach { child ->
            if (!child.deleteRecursively()) success = false
        }
        return success
    }

    private fun readGeneration(pointer: File): String? {
        if (!pointer.isFile) return null
        return runCatching { pointer.readText(StandardCharsets.UTF_8).trim() }
            .getOrNull()
            ?.takeIf { GENERATION_PATTERN.matches(it) }
    }

    private companion object {
        const val MANIFEST_FILE_NAME = "manifest.json"
        const val CURRENT_POINTER_FILE_NAME = "current"
        const val VERSIONS_DIRECTORY_NAME = "versions"
        const val QUEUE_DIRECTORY_NAME = ".queue"
        const val STAGING_DIRECTORY_NAME = ".staging"
        const val QUEUE_FILE_EXTENSION = "json"
        const val QUEUE_PAUSED_FILE_NAME = "paused"
        const val QUEUE_PAUSED_MARKER = "paused"
        val GENERATION_PATTERN = Regex("[0-9a-fA-F-]{36}")
    }

    private data class CompletedDownloadScan(
        val downloads: List<DownloadedThread>,
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
    require(relativePath.isNotBlank()) { "Downloaded file path is blank" }
    val relative = File(relativePath)
    require(!relative.isAbsolute) { "Downloaded file path must be relative" }
    val canonicalRoot = root.canonicalFile
    val resolved = File(canonicalRoot, relativePath).canonicalFile
    val rootPrefix = canonicalRoot.path + File.separator
    require(resolved.path.startsWith(rootPrefix)) {
        "Downloaded file path escapes its thread directory"
    }
    return resolved
}

private fun requireIsDirectChild(parent: File, child: File) {
    require(child.canonicalFile.parentFile == parent.canonicalFile) {
        "Staging directory is outside the thread download staging root"
    }
}

private fun ensureDirectory(directory: File?) {
    requireNotNull(directory) { "Directory must have a parent" }
    check(directory.isDirectory || directory.mkdirs()) {
        "Could not create ${directory.path}"
    }
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
    expected.indices.all { index ->
        index < size && this[index].toInt() and 0xFF == expected[index]
    }

private fun ByteArray.asciiStartsWith(value: String): Boolean = asciiAt(0, value)

private fun ByteArray.asciiAt(offset: Int, value: String): Boolean {
    if (offset + value.length > size) return false
    return value.indices.all { index ->
        this[offset + index].toInt().toChar() == value[index]
    }
}
