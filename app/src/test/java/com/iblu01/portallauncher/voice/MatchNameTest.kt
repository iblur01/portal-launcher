package com.iblu01.portallauncher.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchNameTest {

    private val entities = listOf("Nicky ménage", "Plafond", "Lumières TV", "Store de la chambre")

    /**
     * The failure seen on the panel: Home Assistant matches an entity name exactly, so the
     * model's "Nicky" reached nothing and the vacuum never came home.
     */
    @Test
    fun `a partial spoken name resolves to the full entity name`() {
        assertEquals("Nicky ménage", matchName(entities, "Nicky"))
        assertEquals("Nicky ménage", matchName(entities, "nicky menage"))
        assertEquals("Store de la chambre", matchName(entities, "store"))
    }

    @Test
    fun `a name nobody has resolves to nothing`() {
        assertNull(matchName(entities, "aspirateur du garage"))
        assertNull(matchName(entities, ""))
        assertNull(matchName(emptyList(), "plafond"))
    }

    /** Home Assistant's internal errors have to become something the model can act on. */
    @Test
    fun `match failures are translated into an instruction`() {
        val duplicate = "<MatchFailedError result=MatchTargetsResult(is_match=False, " +
            "no_match_reason=<MatchFailedReason.DUPLICATE_NAME: 11>, no_match_name='Plafond'"
        assertEquals(
            "several entities share that name; say which room it is in, or pass area and domain",
            explainMatchFailure(duplicate),
        )
        assertEquals("this home has no such area", explainMatchFailure("MatchFailedReason.AREA: 3"))
        // A plain sentence from Home Assistant is left alone: it is already the best answer.
        assertNull(explainMatchFailure("J'ai allumé 2 lumières"))
    }
}
