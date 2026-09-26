package com.liquidglass.messages.ui.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.liquidglass.messages.ui.chat.ChatScreen
import com.liquidglass.messages.ui.contactinfo.ContactInfoScreen
import com.liquidglass.messages.ui.conversations.ConversationListScreen
import com.liquidglass.messages.ui.settings.SettingsScreen

/** Sentinel thread id for a chat opened before its Telephony thread exists. */
private const val NO_THREAD_ID: Long = -1L

/**
 * The app's single navigation graph: conversation list -> chat / new-message.
 *
 * @param navController      hoisted controller (defaults to a fresh one).
 * @param startWithRecipient when non-null (a deep link from ACTION_SENDTO /
 *                           ACTION_SEND), the graph opens straight into a chat
 *                           with this recipient on first composition.
 * @param startWithBody      optional pre-filled message body for that deep link.
 *                           Carried through so the chat composer can pre-populate
 *                           it; included in the deep-link key so a new share with
 *                           the same recipient but different body re-navigates.
 */
@Composable
fun MessagesNavGraph(
    navController: NavHostController = rememberNavController(),
    startWithRecipient: String? = null,
    startWithBody: String? = null,
) {
    // Deep-link handling: when launched via SENDTO/SEND we jump to the chat for
    // the supplied recipient. Keyed on recipient+body so a fresh intent with new
    // values re-triggers, but recomposition alone does not double-navigate.
    if (!startWithRecipient.isNullOrBlank()) {
        LaunchedEffect(startWithRecipient, startWithBody) {
            navController.navigate(Routes.chat(NO_THREAD_ID, startWithRecipient)) {
                // Keep the conversation list beneath so Back returns to it.
                launchSingleTop = true
            }
        }
    }

    // iOS navigation-stack motion: the new page slides in from the right edge
    // while the old one drifts a third of the way left (and back on pop).
    val push = tween<IntOffset>(durationMillis = 380, easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f))
    NavHost(
        navController = navController,
        startDestination = Routes.conversations,
        enterTransition = { slideInHorizontally(push) { it } },
        exitTransition = { slideOutHorizontally(push) { -it / 3 } },
        popEnterTransition = { slideInHorizontally(push) { -it / 3 } },
        popExitTransition = { slideOutHorizontally(push) { it } },
    ) {
        // ---- Conversation list ----
        composable(Routes.conversations) {
            ConversationListScreen(
                onConversationClick = { threadId, address ->
                    navController.navigate(Routes.chat(threadId, address))
                },
                onOpenSettings = { navController.navigate(Routes.settings) },
                onOpenRecentlyDeleted = { navController.navigate(Routes.recentlyDeleted) },
            )
        }

        // ---- Chat (single thread) ----
        composable(
            route = Routes.chatPattern,
            arguments = listOf(
                navArgument(Routes.argThreadId) {
                    type = NavType.LongType
                    defaultValue = NO_THREAD_ID
                },
                navArgument(Routes.argAddress) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            val threadId = args?.getLong(Routes.argThreadId) ?: NO_THREAD_ID
            val address = Routes.decodeAddress(args?.getString(Routes.argAddress))

            ChatScreen(
                threadId = threadId,
                address = address,
                // Only the deep-linked chat gets the shared body.
                initialText = startWithBody.takeIf { address == startWithRecipient },
                onBack = { navController.popBackStack() },
                onOpenInfo = { id, addr -> navController.navigate(Routes.contactInfo(id, addr)) },
            )
        }

        // ---- Contact info sheet ----
        composable(
            route = Routes.contactInfoPattern,
            arguments = listOf(
                navArgument(Routes.argThreadId) {
                    type = NavType.LongType
                    defaultValue = NO_THREAD_ID
                },
                navArgument(Routes.argAddress) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            val threadId = args?.getLong(Routes.argThreadId) ?: NO_THREAD_ID
            val address = Routes.decodeAddress(args?.getString(Routes.argAddress))
            ContactInfoScreen(
                threadId = threadId,
                address = address,
                onBack = { navController.popBackStack() },
                // The chat underneath no longer exists — go straight back to the list.
                onConversationDeleted = { navController.popBackStack(Routes.conversations, inclusive = false) },
            )
        }

        // ---- Recently Deleted ----
        composable(Routes.recentlyDeleted) {
            com.liquidglass.messages.ui.deleted.RecentlyDeletedScreen(onBack = { navController.popBackStack() })
        }

        // ---- Settings ----
        composable(Routes.settings) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
