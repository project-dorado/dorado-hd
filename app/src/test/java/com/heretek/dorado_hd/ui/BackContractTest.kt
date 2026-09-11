package com.heretek.dorado_hd.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.heretek.dorado_hd.TestDoradoApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Hardware-back contract for mini-app sub-screens: the system back action must
 * follow the same path as the cropped header, not exit the whole app. This is
 * the regression gate for H-01 (back previously popped `MiniApp` from every
 * screen) and for sub-screens that forgot to pass their `onBack` down.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestDoradoApp::class)
class BackContractTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun pressBack() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()
    }

    @Test
    fun `checkers records screen returns to the menu on system back`() {
        compose.setContent { MiniAppHarness("checkers", HarnessMode.PORTRAIT) }
        compose.waitForIdle()

        compose.onNodeWithText("records", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("single player", substring = true).assertDoesNotExist()

        compose.waitForIdle()
        pressBack()
        compose.onNodeWithText("single player", substring = true).assertExists()
    }

    @Test
    fun `hexic difficulty screen returns to the mode menu on system back`() {
        compose.setContent { MiniAppHarness("hexic", HarnessMode.PORTRAIT) }
        compose.waitForIdle()

        compose.onNodeWithText("marathon", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("survival", substring = true).assertDoesNotExist()

        compose.waitForIdle()
        pressBack()
        compose.onNodeWithText("survival", substring = true).assertExists()
    }
}
