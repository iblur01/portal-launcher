package com.iblu01.portallauncher.ui.camera

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowingBufferTest {
    private val delimiter = "--frame".toByteArray()

    private fun GrowingBuffer.feed(text: String) {
        val bytes = text.toByteArray()
        append(bytes, bytes.size)
    }

    @Test fun `nothing is yielded before a complete part arrived`() {
        val buffer = GrowingBuffer()
        buffer.feed("--framehead")

        assertNull(buffer.takePart(delimiter))
    }

    @Test fun `the preamble before the first delimiter is never mistaken for a frame`() {
        val buffer = GrowingBuffer()
        buffer.feed("junk--frameAAA--frame")

        assertArrayEquals("AAA".toByteArray(), buffer.takePart(delimiter))
    }

    @Test fun `consecutive parts are yielded in order`() {
        val buffer = GrowingBuffer()
        buffer.feed("--frameAAA--frameBBB--frame")

        assertArrayEquals("AAA".toByteArray(), buffer.takePart(delimiter))
        assertArrayEquals("BBB".toByteArray(), buffer.takePart(delimiter))
        assertNull(buffer.takePart(delimiter))
    }

    @Test fun `a part split across several reads is reassembled`() {
        val buffer = GrowingBuffer()
        buffer.feed("--frameAA")
        assertNull(buffer.takePart(delimiter))
        buffer.feed("BB--frame")

        assertArrayEquals("AABB".toByteArray(), buffer.takePart(delimiter))
    }

    @Test fun `consumed bytes are dropped so a long stream never grows without bound`() {
        val buffer = GrowingBuffer()
        repeat(200) { buffer.feed("--frame" + "x".repeat(1_000)) }
        while (buffer.takePart(delimiter) != null) Unit

        assertTrue("buffer kept ${buffer.size} bytes", buffer.size < 2_000)
    }

    @Test fun `growing past the initial capacity preserves the content`() {
        val buffer = GrowingBuffer(initialCapacity = 8)
        val payload = "y".repeat(5_000)
        buffer.feed("--frame$payload--frame")

        assertArrayEquals(payload.toByteArray(), buffer.takePart(delimiter))
    }
}
