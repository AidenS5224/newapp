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
        route = "chat/{conversationId}/{conversationTitle}/{conversationType}",
        label = "Chat",
        symbol = ""
    ) {
        fun createRoute(
            conversationId: String,
            conversationTitle: String,
            conversationType: String
        ): String {
            return "chat/$conversationId/$conversationTitle/$conversationType"
        }
    }

    data object GroupDetails : AppDestination(
        route = "group-details/{conversationId}",
        label = "Group details",
        symbol = ""
    ) {
        fun createRoute(
            conversationId: String
        ): String {
            return "group-details/$conversationId"
        }
    }

    data object Profile : AppDestination(
        route = "profile",
        label = "Profile",
        symbol = "♙"
    )

    data object PlayerProfileDetails : AppDestination(
        route = "player-profile/{profileId}",
        label = "Player profile",
        symbol = ""
    ) {
        fun createRoute(
            profileId: String
        ): String {
            return "player-profile/$profileId"
        }
    }

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
