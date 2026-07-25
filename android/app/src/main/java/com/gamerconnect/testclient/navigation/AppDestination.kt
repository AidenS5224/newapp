package com.gamerconnect.testclient.navigation

sealed class AppDestination(
    val route: String,
    val label: String,
    val symbol: String
) {
    data object Groups : AppDestination(
        route = "groups",
        label = "Groups",
        symbol = "♟"
    )

    data object Discovery : AppDestination(
        route = "discovery",
        label = "Discovery",
        symbol = "⌕"
    )

    data object Feed : AppDestination(
        route = "feed",
        label = "Feed",
        symbol = "▣"
    )

    data object Messages : AppDestination(
        route = "messages",
        label = "Messages",
        symbol = "▰"
    )

    data object Chat : AppDestination(
        route = "chat/{conversationId}/{conversationTitle}",
        label = "Chat",
        symbol = ""
    ) {
        fun createRoute(
            conversationId: String,
            conversationTitle: String
        ): String {
            return "chat/$conversationId/$conversationTitle"
        }
    }

    data object Profile : AppDestination(
        route = "profile",
        label = "Profile",
        symbol = "♙"
    )

    companion object {
        val bottomNavigationItems = listOf(
            Groups,
            Discovery,
            Feed,
            Messages,
            Profile
        )
    }
}
