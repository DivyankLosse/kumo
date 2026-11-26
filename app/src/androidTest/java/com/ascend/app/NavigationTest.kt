package com.ascend.app

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launches the real app and walks every tab and secondary module, failing on any crash. */
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    companion object {
        init {
            AscendApp.riveEnabled = false
        }
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun tab(label: String) =
        rule.onNode(hasText(label) and hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()

    /** CI emulators render slower than local ones, so wait for the text instead of asserting immediately. */
    private fun waitForText(text: String) =
        rule.waitUntil(10_000) { rule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun allTabsRender() {
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Focus with", substring = true).fetchSemanticsNodes().isNotEmpty() }
        tab("Planner"); waitForText("Add block")
        tab("Career"); waitForText("Applied AI/ML Engineer by December 2027")
        tab("Fitness"); waitForText("Training calendar")
        tab("Profile"); waitForText("Modules")
        tab("Home"); waitForText("Start 45-minute session")
    }

    @Test
    fun everySecondaryModuleOpensAndReturns() {
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Profile").fetchSemanticsNodes().isNotEmpty() }
        val modules = listOf("Academics", "Nutrition", "Communication", "Grooming", "Posture", "Social confidence", "Analytics", "Companion", "AI Coach", "Settings")
        modules.forEach { name ->
            tab("Profile")
            rule.onNode(hasScrollAction()).performScrollToNode(hasText(name) and hasClickAction())
            rule.onNode(hasText(name) and hasClickAction()).performClick()
            rule.waitForIdle()
            rule.onNodeWithContentDescription("Back").assertExists().performClick()
            rule.waitForIdle()
        }
    }

    @Test
    fun focusSessionSetupOpensFromHome() {
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Start 45-minute session").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Start 45-minute session").performClick()
        rule.onNodeWithText("Focus session").assertExists()
        rule.onNodeWithText("Start 45 minutes").performClick()
        rule.onNodeWithText("of 45 min").assertExists()
        rule.onNodeWithText("Pause").performClick()
        rule.onNodeWithText("Paused").assertExists()
    }
}
