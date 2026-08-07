package com.iblu01.portallauncher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.iblu01.portallauncher.ui.components.controls.ThermostatActivity
import com.iblu01.portallauncher.ui.components.controls.ThermostatArc
import com.iblu01.portallauncher.ui.components.controls.ThermostatMode

/** Backend-neutral temperature dial shared by climate and water-heater entities. */
@Composable
fun TemperatureArcControl(
    target: Float,
    current: Float?,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    unit: String,
    onTargetChange: (Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
    mode: ThermostatMode = ThermostatMode.HEAT,
    activity: ThermostatActivity? = null,
    lowTarget: Float = target,
    highTarget: Float = target,
    onRangeChange: (Float, Float) -> Unit = { _, _ -> },
) = ThermostatArc(
    mode = mode, activity = activity, target = target, onTargetChange = onTargetChange,
    lowTarget = lowTarget, highTarget = highTarget, onRangeChange = onRangeChange,
    valueRange = valueRange, step = step, current = current, unit = unit,
    modifier = modifier, onCommit = onCommit,
)
