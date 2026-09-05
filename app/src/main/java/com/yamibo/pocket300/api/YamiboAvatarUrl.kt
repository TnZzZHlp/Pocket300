package com.yamibo.pocket300.api

import java.net.URI

internal fun yamiboAvatarUrl(uid: Int, size: String = "small"): String {
    require(uid > 0)
    require(size in setOf("small", "middle", "big"))
    val id = uid.toString().padStart(9, '0')
    return "$YAMIBO_ORIGIN/uc_server/data/avatar/${id.dropLast(6)}/" +
        "${id.takeLast(6).take(2)}/${id.takeLast(4).take(2)}/${id.takeLast(2)}_avatar_$size.jpg"
}

// The legacy PHP endpoint is blocked by the site; browsers use static avatar files.
internal fun resolveYamiboAvatarUrl(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    if (uri.host != URI(YAMIBO_ORIGIN).host || uri.path != "/uc_server/avatar.php") return url
    val parameters = uri.rawQuery.orEmpty().split('&').associate {
        it.substringBefore('=') to it.substringAfter('=', "")
    }
    val uid = parameters["uid"]?.toIntOrNull()?.takeIf { it > 0 } ?: return url
    val size = parameters["size"]?.takeIf { it in setOf("small", "middle", "big") } ?: "small"
    return yamiboAvatarUrl(uid, size)
}
