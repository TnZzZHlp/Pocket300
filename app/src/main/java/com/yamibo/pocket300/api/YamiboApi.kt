package com.yamibo.pocket300.api

/**
 * Application-scoped API graph. All modules must share this client so login,
 * logout, check-in, forum, thread, search, and message requests use the same HttpOnly cookies.
 */
class YamiboApi(client: YamiboClient = YamiboClient()) {
    val auth = YamiboAuthApi(client)
    val dailyCheckIn = YamiboDailyCheckInApi(client)
    val favorites = YamiboFavoritesApi(client)
    val forums = YamiboForumsApi(client)
    val messages = YamiboMessagesApi(client)
    val posts = YamiboPostsApi(client)
    val search = YamiboSearchApi(client)
    val threads = YamiboThreadsApi(client)
}
