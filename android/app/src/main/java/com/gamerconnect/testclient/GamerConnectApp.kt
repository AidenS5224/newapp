package com.gamerconnect.testclient

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gamerconnect.testclient.feature.discovery.DiscoveryScreen
import com.gamerconnect.testclient.feature.feed.FeedScreen
import com.gamerconnect.testclient.feature.groups.GroupsScreen
import com.gamerconnect.testclient.feature.messages.MessagesScreen
import com.gamerconnect.testclient.feature.profile.ProfileScreen
import com.gamerconnect.testclient.navigation.AppDestination
import android.net.Uri
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.gamerconnect.testclient.feature.messages.ChatScreen

@Composable
fun GamerConnectApp(
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val destinations = AppDestination.bottomNavigationItems
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = currentBackStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF070D18)
            ) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(
                                    navController.graph.findStartDestination().id
                                ) {
                                    saveState = true
                                }

                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Text(
                                text = destination.symbol,
                                fontSize = 20.sp
                            )
                        },
                        label = {
                            Text(
                                text = destination.label,
                                fontSize = 10.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = Color(0xFF211044),
                            unselectedIconColor = Color(0xFF8D94A3),
                            unselectedTextColor = Color(0xFF8D94A3)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Feed.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppDestination.Groups.route) {
                GroupsScreen()
            }

            composable(AppDestination.Discovery.route) {
                DiscoveryScreen()
            }

            composable(AppDestination.Feed.route) {
                FeedScreen()
            }

            composable(AppDestination.Messages.route) {
                MessagesScreen(
                    onConversationClick = { conversationId, conversationTitle ->
                        navController.navigate(
                            AppDestination.Chat.createRoute(
                                conversationId = conversationId,
                                conversationTitle = Uri.encode(conversationTitle)
                            )
                        )
                    }
                )
            }

            composable(
                route = AppDestination.Chat.route,
                arguments = listOf(
                    navArgument("conversationId") {
                        type = NavType.StringType
                    },
                    navArgument("conversationTitle") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val conversationId =
                    backStackEntry.arguments?.getString("conversationId")
                        ?: return@composable

                val conversationTitle = Uri.decode(
                    backStackEntry.arguments?.getString("conversationTitle")
                        ?: "Chat"
                )

                ChatScreen(
                    conversationId = conversationId,
                    conversationTitle = conversationTitle,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(AppDestination.Profile.route) {
                ProfileScreen(
                    onSignOut = onSignOut
                )
            }
        }
    }
}

