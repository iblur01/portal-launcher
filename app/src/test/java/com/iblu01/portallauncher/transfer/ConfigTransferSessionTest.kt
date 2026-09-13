package com.iblu01.portallauncher.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigTransferSessionTest {
    private val now = 1_787_911_500_000L
    private val peerKey = ConfigTransferCrypto.encodePublicKey(ConfigTransferCrypto.generateKeyPair().public)

    private fun offered(at: Long = now): ConfigTransferSession =
        ConfigTransferSession.create(at).offer(peerKey, at).getOrThrow()

    private fun failureOf(result: Result<ConfigTransferSession>): ConfigTransferException =
        result.exceptionOrNull() as ConfigTransferException

    @Test fun `new session is created with a random 128 bit id`() {
        val session = ConfigTransferSession.create(now)
        assertEquals(ConfigTransferState.CREATED, session.state)
        assertEquals(0, session.offers)
        assertNull(session.peerPublicKey)
        assertEquals(ConfigTransferSession.ID_SIZE_BYTES * 2, session.id.length)
        assertTrue(session.id.matches(Regex("[0-9a-f]+")))
        assertNotEquals(session.id, ConfigTransferSession.create(now).id)
    }

    @Test fun `session id never reaches toString`() {
        val session = ConfigTransferSession.create(now)
        assertFalse(session.toString().contains(session.id))
    }

    @Test fun `id comparison is constant time and exact`() {
        val session = ConfigTransferSession.create(now)
        assertTrue(session.matchesId(session.id))
        assertFalse(session.matchesId(session.id.dropLast(1) + "0" + "0"))
        assertFalse(session.matchesId(""))
    }

    @Test fun `offer establishes the peer key and the aad is the session id`() {
        val session = offered()
        assertEquals(ConfigTransferState.OFFERED, session.state)
        assertEquals(1, session.offers)
        assertTrue(ConfigTransferCrypto.constantTimeEquals(peerKey, session.peerPublicKey!!))
        assertTrue(ConfigTransferCrypto.constantTimeEquals(session.id.toByteArray(), session.aad))
    }

    @Test fun `offers are capped at five and the sixth fails the session`() {
        var session = ConfigTransferSession.create(now)
        repeat(ConfigTransferSession.MAX_OFFERS) {
            session = session.offer(peerKey, now).getOrThrow()
        }
        assertEquals(ConfigTransferSession.MAX_OFFERS, session.offers)

        val failure = failureOf(session.offer(peerKey, now))
        assertEquals(ConfigTransferState.FAILED, failure.session.state)
        assertTrue(failure.message!!.contains("quota"))
    }

    @Test fun `consume applies exactly once and the replay is rejected`() {
        val session = offered()
        val consumed = session.consume(now).getOrThrow()
        assertEquals(ConfigTransferState.CONSUMED, consumed.state)

        val replay = failureOf(consumed.consume(now + 1))
        assertEquals(ConfigTransferState.CONSUMED, replay.session.state)
        assertTrue(replay.message!!.contains("terminal"))
    }

    @Test fun `consume requires an offer first`() {
        val failure = failureOf(ConfigTransferSession.create(now).consume(now))
        assertEquals(ConfigTransferState.FAILED, failure.session.state)
    }

    @Test fun `a consumed session accepts no further offer`() {
        val consumed = offered().consume(now).getOrThrow()
        assertEquals(ConfigTransferState.CONSUMED, failureOf(consumed.offer(peerKey, now)).session.state)
    }

    @Test fun `a failed session is terminal for every transition`() {
        val failed = ConfigTransferSession.create(now).failed()
        assertEquals(ConfigTransferState.FAILED, failed.state)
        assertEquals(ConfigTransferState.FAILED, failureOf(failed.offer(peerKey, now)).session.state)
        assertEquals(ConfigTransferState.FAILED, failureOf(failed.consume(now)).session.state)
        assertSame(failed, failed.expired())
    }

    @Test fun `session expires five minutes after creation`() {
        val session = offered()
        val edge = now + ConfigTransferSession.TTL_MS - 1
        assertFalse(session.isExpiredAt(edge))
        assertEquals(ConfigTransferState.OFFERED, session.stateAt(edge))

        val past = now + ConfigTransferSession.TTL_MS
        assertTrue(session.isExpiredAt(past))
        assertEquals(ConfigTransferState.EXPIRED, session.stateAt(past))
        assertEquals(ConfigTransferState.EXPIRED, session.expired().state)
    }

    @Test fun `expired session refuses offer and consume`() {
        val past = now + ConfigTransferSession.TTL_MS
        val created = ConfigTransferSession.create(now)
        assertEquals(ConfigTransferState.EXPIRED, failureOf(created.offer(peerKey, past)).session.state)

        val expiredConsume = failureOf(offered().consume(past))
        assertEquals(ConfigTransferState.EXPIRED, expiredConsume.session.state)
        assertTrue(expiredConsume.message!!.contains("expired"))
    }

    @Test fun `a consumed session no longer expires`() {
        val consumed = offered().consume(now).getOrThrow()
        val past = now + ConfigTransferSession.TTL_MS
        assertFalse(consumed.isExpiredAt(past))
        assertEquals(ConfigTransferState.CONSUMED, consumed.stateAt(past))
    }

    @Test fun `transitions never mutate the previous session`() {
        val created = ConfigTransferSession.create(now)
        val session = created.offer(peerKey, now).getOrThrow()
        assertEquals(ConfigTransferState.CREATED, created.state)
        assertEquals(0, created.offers)
        assertEquals(created.id, session.id)
    }
}
