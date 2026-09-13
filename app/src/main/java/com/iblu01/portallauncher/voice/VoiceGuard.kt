package com.iblu01.portallauncher.voice

/**
 * What the assistant is not allowed to do on a voice alone.
 *
 * A wall panel hears whoever is in the room — and whoever is outside an open window, and the
 * television. Everything else in this house is recoverable by saying the opposite sentence; a
 * lock, a garage door and an alarm are not. The panel's touch controls already ask for a code on
 * the alarm, and voice must not be the way around that.
 *
 * So a guarded action is never refused outright (that would make the assistant useless for the
 * one thing people want on a panel by the door) — it is escalated to a physical tap on the
 * screen, which proves someone is actually standing there. See [VoiceAssistantController].
 */
object VoiceGuard {
    val DOMAINS = setOf("lock", "alarm_control_panel")

    /**
     * A cover is usually a blind, which nobody needs to confirm. These classes are the exceptions
     * that give access to the house.
     */
    val DEVICE_CLASSES = setOf("garage", "gate", "door")

    fun isGuardedDomain(domain: String): Boolean = domain.trim().lowercase() in DOMAINS

    fun isGuardedEntity(entityId: String, deviceClass: String? = null): Boolean =
        isGuardedDomain(entityId.substringBefore('.')) ||
            deviceClass?.trim()?.lowercase() in DEVICE_CLASSES

    /**
     * Whether an intent call needs confirmation, judged on what the model asked for rather than
     * on what Home Assistant will resolve it to.
     *
     * The `domain`/`device_class` slots catch the explicit case. [guardedNames] is the rest: the
     * names of this home's own guarded entities, so "ouvre la porte d'entrée" is caught even
     * though the model passed no domain. Matching is substring-on-both-sides because the user
     * says "la porte" for an entity called "Porte d'entrée" and vice versa.
     */
    fun intentNeedsConfirmation(
        domain: String?,
        deviceClass: String?,
        name: String?,
        /**
         * Lazy on purpose: reading it costs a full Home Assistant state download, and most calls
         * are decided by the domain alone. Passing it eagerly cost 8 s of mid-conversation
         * silence on the first named intent of every session.
         */
        guardedNames: () -> Collection<String>,
    ): Boolean {
        if (domain != null && isGuardedDomain(domain)) return true
        if (deviceClass?.trim()?.lowercase() in DEVICE_CLASSES) return true
        val wanted = name?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
        return guardedNames().any { guarded ->
            val known = guarded.lowercase()
            known.isNotEmpty() && (known.contains(wanted) || wanted.contains(known))
        }
    }
}
