package com.iblu01.portallauncher.voice

import com.iblu01.portallauncher.ui.model.PanelKind

/**
 * The multi-step side of the assistant: what it announced it was going to do, and where it is in
 * that list.
 *
 * There is deliberately no task engine here. The model is the loop — it already chains tool calls
 * inside one turn — so the panel's job is to make that chain *visible* and to keep it honest: a
 * plan on screen is how someone standing in front of a wall panel knows an assistant that went
 * quiet for four seconds is working through step two of four rather than broken.
 *
 * Pure and unit-tested; the state lives in [VoiceUiState.plan].
 */
enum class VoiceTaskStatus { PENDING, RUNNING, DONE, FAILED }

data class VoiceTask(val title: String, val status: VoiceTaskStatus = VoiceTaskStatus.PENDING)

data class VoicePlan(val tasks: List<VoiceTask> = emptyList()) {
    val isEmpty: Boolean get() = tasks.isEmpty()

    /** Index of the step being executed, or null once every step is settled. */
    val runningIndex: Int? get() = tasks.indexOfFirst { it.status == VoiceTaskStatus.RUNNING }.takeIf { it >= 0 }

    val isFinished: Boolean
        get() = tasks.isNotEmpty() && tasks.none {
            it.status == VoiceTaskStatus.PENDING || it.status == VoiceTaskStatus.RUNNING
        }
}

/**
 * A plan longer than this is not a plan a spoken exchange can hold, and on a wall panel it is a
 * wall of text nobody reads. Extra steps are dropped rather than rendered off-screen.
 */
const val MAX_VOICE_TASKS = 8

/** Builds a plan from the titles the model announced, with the first step already running. */
fun voicePlanOf(titles: List<String>): VoicePlan {
    val cleaned = titles.map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_VOICE_TASKS)
    if (cleaned.isEmpty()) return VoicePlan()
    return VoicePlan(
        cleaned.mapIndexed { index, title ->
            VoiceTask(title, if (index == 0) VoiceTaskStatus.RUNNING else VoiceTaskStatus.PENDING)
        },
    )
}

/**
 * Settles the running step and starts the next one.
 *
 * The running step is taken from the plan, not from an index the model supplies: it drifts (it
 * skips, it renumbers, it retries a step) and a checklist that disagrees with what is happening
 * is worse than no checklist. When nothing is running any more this is a no-op, so a duplicate
 * `complete_task` cannot walk past the end of the list.
 */
fun VoicePlan.completeCurrent(failed: Boolean = false): VoicePlan {
    val index = runningIndex ?: return this
    val settled = if (failed) VoiceTaskStatus.FAILED else VoiceTaskStatus.DONE
    val next = tasks.toMutableList()
    next[index] = next[index].copy(status = settled)
    next.indexOfFirst { it.status == VoiceTaskStatus.PENDING }
        .takeIf { it >= 0 }
        ?.let { next[it] = next[it].copy(status = VoiceTaskStatus.RUNNING) }
    return VoicePlan(next)
}

/**
 * What the assistant can ask the launcher itself to show. Panel targets reuse [PanelKind] rather
 * than inventing a second vocabulary: the launcher's own router is an exhaustive `when` over it,
 * so anything the user can open by tapping is reachable by voice for free.
 */
sealed interface PortalCommand {
    data class ShowPanel(val kind: PanelKind) : PortalCommand
    data object ClosePanel : PortalCommand
}

/**
 * `GENERIC_DETAILS` is not a destination anyone can name out loud (it is the fallback shell for a
 * chip with no dedicated panel), so it is left out of what the model is offered.
 */
val PORTAL_PANEL_TARGETS: List<String> =
    PanelKind.entries.filter { it != PanelKind.GENERIC_DETAILS }.map { it.name.lowercase() }

const val PORTAL_CLOSE_TARGET = "close"

/** Maps one `portal_show` argument onto a command, or null when the model invented a target. */
fun portalCommandOf(target: String): PortalCommand? {
    val wanted = target.trim().lowercase()
    if (wanted == PORTAL_CLOSE_TARGET) return PortalCommand.ClosePanel
    return PanelKind.entries
        .firstOrNull { it != PanelKind.GENERIC_DETAILS && it.name.lowercase() == wanted }
        ?.let(PortalCommand::ShowPanel)
}
