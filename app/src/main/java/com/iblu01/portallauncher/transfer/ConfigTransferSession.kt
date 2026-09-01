package com.iblu01.portallauncher.transfer

/**
 * One-shot state machine for a configuration transfer.
 *
 * The flow is sender-approved only: the sender picks the target and confirms, the receiver never
 * has to confirm a code. So the session itself is what limits abuse — it lives five minutes, accepts
 * a bounded number of offers, and its payload can be applied exactly once. Every replay of an
 * already-consumed session is rejected.
 *
 * Pure and immutable: transitions return a new session (or an error) and the clock is a parameter,
 * so this is testable without any Android or time dependency.
 */
enum class ConfigTransferState {
    /** Session created, waiting for the peer's public key. */
    CREATED,

    /** Peer key received, payload may be consumed once. */
    OFFERED,

    /** Payload applied. Terminal. */
    CONSUMED,

    /** TTL elapsed before consumption. Terminal. */
    EXPIRED,

    /** Aborted (bad state, quota exhausted, explicit failure). Terminal. */
    FAILED,
    ;

    val terminal: Boolean get() = this == CONSUMED || this == EXPIRED || this == FAILED
}

class ConfigTransferSession private constructor(
    val id: String,
    val createdAtMs: Long,
    val state: ConfigTransferState,
    val offers: Int,
    val peerPublicKey: ByteArray?,
) {
    /** Associated data for the AEAD: ties every ciphertext to this session id. */
    val aad: ByteArray get() = id.toByteArray(Charsets.UTF_8)

    fun isExpiredAt(nowMs: Long): Boolean =
        !state.terminal && nowMs - createdAtMs >= TTL_MS

    /** State as observed at [nowMs] — a live session flips to EXPIRED once its TTL elapses. */
    fun stateAt(nowMs: Long): ConfigTransferState =
        if (isExpiredAt(nowMs)) ConfigTransferState.EXPIRED else state

    /**
     * Records a peer public key offer. Callable from CREATED or OFFERED (the peer may retry with a
     * new key), up to [MAX_OFFERS] times; the offer that would exceed the quota fails the session.
     */
    fun offer(peerPublicKey: ByteArray, nowMs: Long): Result<ConfigTransferSession> {
        expiredOrTerminal(nowMs)?.let { return it }
        if (state != ConfigTransferState.CREATED && state != ConfigTransferState.OFFERED) {
            return reject("cannot offer in state $state")
        }
        if (offers >= MAX_OFFERS) {
            return Result.failure(
                ConfigTransferException(failed(), "offer quota exhausted ($MAX_OFFERS)"),
            )
        }
        return Result.success(
            ConfigTransferSession(
                id = id,
                createdAtMs = createdAtMs,
                state = ConfigTransferState.OFFERED,
                offers = offers + 1,
                peerPublicKey = peerPublicKey.copyOf(),
            ),
        )
    }

    /**
     * The single consume/apply transition. Succeeds once from OFFERED; any later call — the replay
     * case — fails against the CONSUMED terminal state.
     */
    fun consume(nowMs: Long): Result<ConfigTransferSession> {
        expiredOrTerminal(nowMs)?.let { return it }
        if (state != ConfigTransferState.OFFERED) return reject("cannot consume in state $state")
        return Result.success(
            ConfigTransferSession(
                id = id,
                createdAtMs = createdAtMs,
                state = ConfigTransferState.CONSUMED,
                offers = offers,
                peerPublicKey = peerPublicKey,
            ),
        )
    }

    /** Explicit abort: crypto failure, user cancel, transport error. */
    fun failed(): ConfigTransferSession = terminalCopy(ConfigTransferState.FAILED)

    /** Materialises the TTL so an expired session can be stored as terminal. */
    fun expired(): ConfigTransferSession = terminalCopy(ConfigTransferState.EXPIRED)

    /** Session-id check that does not leak the mismatch position through timing. */
    fun matchesId(candidate: String): Boolean = ConfigTransferCrypto.constantTimeEquals(id, candidate)

    private fun terminalCopy(next: ConfigTransferState) =
        if (state.terminal) this
        else ConfigTransferSession(id, createdAtMs, next, offers, peerPublicKey)

    private fun expiredOrTerminal(nowMs: Long): Result<ConfigTransferSession>? = when {
        isExpiredAt(nowMs) ->
            Result.failure(ConfigTransferException(expired(), "session expired"))
        state.terminal ->
            Result.failure(ConfigTransferException(this, "session is terminal ($state)"))
        else -> null
    }

    private fun reject(message: String): Result<ConfigTransferSession> =
        Result.failure(ConfigTransferException(failed(), message))

    /** Redacted on purpose: the session id is a capability, it never reaches a log. */
    override fun toString(): String = "ConfigTransferSession(state=$state, offers=$offers)"

    companion object {
        const val TTL_MS = 5 * 60 * 1000L
        const val MAX_OFFERS = 5
        const val ID_SIZE_BYTES = 16

        /** New session with a random 128-bit id, rendered as lowercase hex. */
        fun create(nowMs: Long): ConfigTransferSession = ConfigTransferSession(
            id = ConfigTransferCrypto.randomBytes(ID_SIZE_BYTES)
                .joinToString("") { "%02x".format(it) },
            createdAtMs = nowMs,
            state = ConfigTransferState.CREATED,
            offers = 0,
            peerPublicKey = null,
        )
    }
}

/** Carries the session as it stands after the rejected transition, so callers can persist it. */
class ConfigTransferException(
    val session: ConfigTransferSession,
    message: String,
) : IllegalStateException(message)
