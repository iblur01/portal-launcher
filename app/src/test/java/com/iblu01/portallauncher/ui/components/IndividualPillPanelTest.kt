package com.iblu01.portallauncher.ui.components

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.CallService
import com.iblu01.portallauncher.ui.LocalAreas
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.HaStates
import com.iblu01.portallauncher.ui.LocalHaStates
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w900dp-h600dp")
class IndividualPillPanelTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `individual light opens its detail directly without one-item browser`() {
        val entity = HaEntity(
            entityId = "light.kitchen",
            state = "on",
            attributes = JSONObject().put("friendly_name", "Cuisine"),
        )
        val chip = LauncherChip(
            id = entity.entityId,
            icon = "light",
            label = "Cuisine",
            value = "Allumée",
            entityId = entity.entityId,
            kind = PillKind.LIGHTS,
            deviceState = entity.state,
        )
        val noOpService = object : CallService {
            override fun invoke(domain: String, service: String, entityId: String?, data: Map<String, Any>?) = Unit
        }

        rule.setContent {
            CompositionLocalProvider(
                LocalCallService provides noOpService,
                LocalHaStates provides remember { HaStates().also { s -> s.apply(mapOf(entity.entityId to entity)) } },
                LocalAreas provides mapOf(entity.entityId to "Cuisine"),
            ) {
                ChipActionsPanel(chip = chip, onDismiss = {})
            }
        }

        // The device detail is the first and only page: there is no intermediate row also named
        // Cuisine, and its navigation affordance closes the panel instead of going back to a list.
        rule.onAllNodesWithText("Cuisine").assertCountEquals(1)
        val context = ApplicationProvider.getApplicationContext<Context>()
        rule.onNodeWithContentDescription(context.getString(R.string.side_panel_close_desc)).assertExists()
    }

    @Test fun `individual switch panel exposes its control instead of toggling on tray tap`() {
        val entity = HaEntity(
            entityId = "switch.desk",
            state = "on",
            attributes = JSONObject().put("friendly_name", "Bureau"),
        )
        val chip = LauncherChip(
            id = entity.entityId,
            icon = "switch",
            label = "Bureau",
            value = "Activé",
            entityId = entity.entityId,
            kind = PillKind.SWITCH,
            deviceState = entity.state,
        )
        val noOpService = object : CallService {
            override fun invoke(domain: String, service: String, entityId: String?, data: Map<String, Any>?) = Unit
        }

        rule.setContent {
            CompositionLocalProvider(
                LocalCallService provides noOpService,
                LocalHaStates provides remember { HaStates().also { s -> s.apply(mapOf(entity.entityId to entity)) } },
                LocalAreas provides emptyMap(),
            ) {
                ChipActionsPanel(chip = chip, onDismiss = {})
            }
        }

        rule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch) and hasClickAction(),
        ).assertIsOn()
    }

    @Test fun `plain fan shows running state once and exposes horizontal oscillation choices`() {
        val entity = HaEntity(
            entityId = "fan.desk",
            state = "on",
            attributes = JSONObject()
                .put("friendly_name", "Ventilateur")
                .put("supported_features", FanFeature.OSCILLATE)
                .put("oscillating", false),
        )
        showPanel(entity, PillKind.FAN, "fan", "En marche")

        val context = ApplicationProvider.getApplicationContext<Context>()
        rule.onAllNodesWithText(context.getString(R.string.fan_state_on)).assertCountEquals(1)
        rule.onNodeWithText(context.getString(R.string.fan_oscillation_off)).assertExists()
        rule.onNodeWithText(context.getString(R.string.fan_oscillation_on)).assertExists()
    }

    @Test fun `simple cover omits unsupported stop action`() {
        val entity = HaEntity(
            entityId = "cover.simple",
            state = "open",
            attributes = JSONObject()
                .put("friendly_name", "Volet simple")
                .put("supported_features", CoverFeature.OPEN or CoverFeature.CLOSE),
        )
        showPanel(entity, PillKind.COVER, "cover", "Ouvert")

        val context = ApplicationProvider.getApplicationContext<Context>()
        rule.onNodeWithContentDescription(context.getString(R.string.cover_button_close)).assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.cover_button_open)).assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.cover_button_stop)).assertDoesNotExist()
    }

    @Test fun `read only thermostat promotes current temperature instead of generic detail row`() {
        val entity = HaEntity(
            entityId = "climate.ventilation",
            state = "fan_only",
            attributes = JSONObject()
                .put("friendly_name", "Ventilation")
                .put("current_temperature", 23.0)
                .put("hvac_modes", JSONArray(listOf("off", "fan_only"))),
        )
        showPanel(entity, PillKind.THERMOSTAT, "temperature", "23 °")

        val context = ApplicationProvider.getApplicationContext<Context>()
        rule.onNodeWithText("23.0").assertExists()
        rule.onNodeWithText(context.getString(R.string.thermostat_room_temperature)).assertDoesNotExist()
    }

    private fun showPanel(entity: HaEntity, kind: PillKind, icon: String, value: String) {
        val chip = LauncherChip(
            id = entity.entityId,
            icon = icon,
            label = entity.name,
            value = value,
            entityId = entity.entityId,
            kind = kind,
            deviceState = entity.state,
        )
        val noOpService = object : CallService {
            override fun invoke(domain: String, service: String, entityId: String?, data: Map<String, Any>?) = Unit
        }
        rule.setContent {
            CompositionLocalProvider(
                LocalCallService provides noOpService,
                LocalHaStates provides remember { HaStates().also { s -> s.apply(mapOf(entity.entityId to entity)) } },
                LocalAreas provides emptyMap(),
            ) {
                ChipActionsPanel(chip = chip, onDismiss = {})
            }
        }
    }
}
