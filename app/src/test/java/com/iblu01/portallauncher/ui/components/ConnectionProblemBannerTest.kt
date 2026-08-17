package com.iblu01.portallauncher.ui.components

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.iblu01.portallauncher.ui.theme.PortalTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ConnectionProblemBannerTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun `an initial mdns failure explains the local ip workaround and opens settings`() {
        var clicked = false
        rule.setContent {
            PortalTheme {
                ConnectionProblemBanner(
                    lastUpdateAt = 0L,
                    usesMdnsAddress = true,
                    onClick = { clicked = true },
                )
            }
        }

        rule.onNodeWithTag("connectionProblemBanner")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        rule.onNodeWithText("Home Assistant is unreachable").assertIsDisplayed()
        rule.onNodeWithText("This .local address may not resolve", substring = true).assertIsDisplayed()
        rule.runOnIdle { assertTrue(clicked) }
    }

    @Test
    fun `a dropped connection reports frozen information`() {
        rule.setContent {
            PortalTheme {
                ConnectionProblemBanner(
                    lastUpdateAt = System.currentTimeMillis() - 5_000L,
                    usesMdnsAddress = false,
                    onClick = {},
                )
            }
        }

        rule.onNodeWithText("Offline: info frozen since", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Check the address and local network", substring = true).assertIsDisplayed()
    }
}
