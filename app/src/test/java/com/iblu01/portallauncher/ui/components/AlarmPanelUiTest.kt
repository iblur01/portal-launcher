package com.iblu01.portallauncher.ui.components

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.CallService
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.LocalHaStates
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w640dp-h900dp")
class AlarmPanelUiTest {
    @get:Rule val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val noOpService = object : CallService {
        override fun invoke(
            domain: String,
            service: String,
            entityId: String?,
            data: Map<String, Any>?,
        ) = Unit
    }

    private fun show(state: String, code: Boolean = true) {
        val entity = HaEntity(
            entityId = "alarm_control_panel.home",
            state = state,
            attributes = JSONObject()
                .put("friendly_name", "Alarm")
                .put("supported_features", AlarmFeature.ARM_HOME or AlarmFeature.ARM_AWAY)
                .apply { if (code) put("code_format", "number") },
        )
        val chip = LauncherChip(
            id = entity.entityId,
            icon = "shield",
            label = "Alarm",
            value = state,
            entityId = entity.entityId,
            kind = PillKind.SAFETY,
            deviceState = state,
        )
        rule.setContent {
            CompositionLocalProvider(
                LocalCallService provides noOpService,
                LocalHaStates provides mapOf(entity.entityId to entity),
            ) {
                AlarmControl(chip)
            }
        }
    }

    @Test fun `disarmed screen has arming choices but no selected disabled mode`() {
        show("disarmed")

        rule.onNodeWithText(context.getString(R.string.alarm_choose_mode_title)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.alarm_mode_off)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.alarm_mode_away)).performClick()
        rule.onNodeWithText(
            context.getString(
                R.string.alarm_code_to_arm_title,
                context.getString(R.string.alarm_mode_away),
            ),
        ).assertIsDisplayed()
    }

    @Test fun `armed screen immediately exposes the disarm keypad`() {
        show("armed_away")

        rule.onNodeWithText(context.getString(R.string.alarm_armed_title)).assertIsDisplayed()
        val prompt = rule.onNodeWithText(context.getString(R.string.alarm_disarm_prompt))
        prompt.assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.alarm_choose_mode_title)).assertDoesNotExist()

        val one = rule.onNodeWithContentDescription(
            context.getString(R.string.keypad_key_desc_format, "1"),
        )
        val keyTopBeforeEntry = one.getUnclippedBoundsInRoot().top
        one.performClick()
        rule.waitForIdle()

        prompt.assertDoesNotExist()
        assertEquals(keyTopBeforeEntry, one.getUnclippedBoundsInRoot().top)
    }

    @Test fun `triggered event uses its critical screen and keeps the keypad`() {
        show("triggered")

        rule.onNodeWithText(context.getString(R.string.alarm_triggered_title)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.alarm_triggered_subtitle)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.alarm_disarm_prompt)).assertIsDisplayed()
    }
}
