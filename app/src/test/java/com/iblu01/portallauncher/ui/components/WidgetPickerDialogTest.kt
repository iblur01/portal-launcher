package com.iblu01.portallauncher.ui.components

import android.content.ComponentName
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.iblu01.portallauncher.ui.apps.GridSpan
import com.iblu01.portallauncher.ui.apps.WidgetOffer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The widget picker: what it shows and what it hands back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WidgetPickerDialogTest {

    @get:Rule val rule = createComposeRule()

    private val offer = WidgetOffer(
        provider = ComponentName("com.ha", "com.ha.Widget"),
        label = "Interrupteur",
        appLabel = "Home Assistant",
        preview = null,
        minSpan = GridSpan(2, 1),
    )

    @Test
    fun `an entry says which app it comes from and how many cells it needs`() {
        rule.setContent { WidgetPickerDialog(offers = listOf(offer), onPick = {}, onDismiss = {}) }

        rule.onNodeWithText("Interrupteur").assertIsDisplayed()
        // The size is what decides whether it fits on a page at all, so it is not hidden away.
        rule.onNodeWithText("Home Assistant · 2×1").assertIsDisplayed()
    }

    @Test
    fun `picking an entry reports that offer`() {
        val picked = mutableListOf<WidgetOffer>()
        rule.setContent { WidgetPickerDialog(offers = listOf(offer), onPick = { picked.add(it) }, onDismiss = {}) }

        rule.onNodeWithText("Interrupteur").performClick()
        rule.waitForIdle()

        assertEquals(listOf(offer), picked)
    }

    @Test
    fun `a device with no widgets says so instead of showing an empty panel`() {
        rule.setContent { WidgetPickerDialog(offers = emptyList(), onPick = {}, onDismiss = {}) }

        rule.onNodeWithText("No widgets available").assertIsDisplayed()
    }

    @Test
    fun `no offers loaded yet means no dialog`() {
        rule.setContent { WidgetPickerDialog(offers = null, onPick = {}, onDismiss = {}) }

        rule.onNodeWithText("Add a widget").assertDoesNotExist()
    }
}
