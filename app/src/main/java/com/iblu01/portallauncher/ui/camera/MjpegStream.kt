package com.iblu01.portallauncher.ui.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream

/**
 * Reads Home Assistant's `multipart/x-mixed-replace` camera proxy stream.
 *
 * ExoPlayer cannot read multipart MJPEG — it is not a container it knows — so this is a small
 * dedicated reader rather than a media source. It is also the reason MJPEG never carries audio:
 * the format is a sequence of JPEG images and nothing else.
 *
 * The reader is deliberately blocking and single-use: [read] runs on a background thread and
 * returns as soon as the thread is interrupted or the caller's [onFrame] reports it no longer
 * wants frames. Closing the response there and then is what makes stopping deterministic.
 */
internal class MjpegReader(
    private val client: OkHttpClient,
    private val url: String,
    private val token: String,
) {
    /**
     * Streams frames into [onFrame] until it returns false, the thread is interrupted, or the
     * stream ends. Throws [IOException] on a transport or protocol failure so the caller can show
     * its error state; it never throws for an ordinary, requested stop.
     */
    @Throws(IOException::class)
    fun read(onFrame: (Bitmap) -> Boolean) {
        // The credential travels in the header, never in the url: nothing here can end up in a log
        // line, a crash report, or an intent handed to another application.
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // The status alone: an error body from Home Assistant may echo request details.
                throw IOException("camera stream refused with HTTP ${response.code}")
            }
            val boundary = boundaryOf(response.header("Content-Type"))
                ?: throw IOException("camera stream is not a multipart response")
            val body = response.body ?: throw IOException("camera stream has no body")
            body.byteStream().use { stream -> readParts(stream, boundary, onFrame) }
        }
    }

    private fun readParts(stream: InputStream, boundary: String, onFrame: (Bitmap) -> Boolean) {
        val delimiter = "--$boundary".toByteArray()
        val buffer = GrowingBuffer()
        val chunk = ByteArray(READ_CHUNK)
        var wants = true
        while (wants && !Thread.currentThread().isInterrupted) {
            val read = stream.read(chunk)
            if (read < 0) return
            buffer.append(chunk, read)
            while (true) {
                val part = buffer.takePart(delimiter) ?: break
                val jpeg = jpegOf(part) ?: continue
                val bitmap = BitmapFactory.decodeByteArray(jpeg.first, jpeg.second, jpeg.third)
                    ?: continue
                wants = onFrame(bitmap)
                if (!wants) return
            }
            if (buffer.size > MAX_BUFFER) {
                throw IOException("camera stream part exceeded the frame budget")
            }
        }
    }

    /** `multipart/x-mixed-replace; boundary=--frameboundary` -> `--frameboundary`. */
    private fun boundaryOf(contentType: String?): String? {
        val value = contentType ?: return null
        val marker = value.indexOf("boundary=", ignoreCase = true, startIndex = 0)
        if (marker < 0) return null
        return value.substring(marker + "boundary=".length)
            .substringBefore(';')
            .trim()
            .trim('"')
            .takeIf(String::isNotEmpty)
    }

    /**
     * Locates the JPEG payload inside one multipart part: its headers end at the first blank line,
     * and the image itself starts at the SOI marker. Returns (array, offset, length).
     */
    private fun jpegOf(part: ByteArray): Triple<ByteArray, Int, Int>? {
        var start = -1
        for (i in 0 until part.size - 1) {
            if (part[i] == SOI_FIRST && part[i + 1] == SOI_SECOND) {
                start = i
                break
            }
        }
        if (start < 0) return null
        return Triple(part, start, part.size - start)
    }

    private companion object {
        const val READ_CHUNK = 16 * 1024

        /** A single frame above this is treated as a protocol failure, not as a slow camera. */
        const val MAX_BUFFER = 12 * 1024 * 1024
        const val SOI_FIRST = 0xFF.toByte()
        const val SOI_SECOND = 0xD8.toByte()
    }
}

/**
 * Accumulates the multipart stream and yields complete parts. Kept separate from the transport so
 * the framing rules can be exercised without a socket.
 */
internal class GrowingBuffer(initialCapacity: Int = 64 * 1024) {
    private var data = ByteArray(initialCapacity)
    var size: Int = 0
        private set

    /** True once a first delimiter has been seen; everything before it is preamble, not a frame. */
    private var started = false

    fun append(source: ByteArray, length: Int) {
        ensure(size + length)
        System.arraycopy(source, 0, data, size, length)
        size += length
    }

    /**
     * Returns the bytes of the next complete part, or null when the buffer does not hold one yet.
     * Consumed bytes are dropped, so a long-running stream never grows without bound.
     */
    fun takePart(delimiter: ByteArray): ByteArray? {
        if (!started) {
            val first = indexOf(delimiter, 0) ?: return null
            drop(first + delimiter.size)
            started = true
        }
        val next = indexOf(delimiter, 0) ?: return null
        val part = data.copyOfRange(0, next)
        drop(next + delimiter.size)
        return part
    }

    private fun drop(count: Int) {
        val remaining = size - count
        if (remaining > 0) System.arraycopy(data, count, data, 0, remaining)
        size = remaining.coerceAtLeast(0)
    }

    private fun indexOf(needle: ByteArray, from: Int): Int? {
        if (needle.isEmpty()) return null
        outer@ for (i in from..size - needle.size) {
            for (j in needle.indices) {
                if (data[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return null
    }

    private fun ensure(capacity: Int) {
        if (capacity <= data.size) return
        var next = data.size
        while (next < capacity) next *= 2
        data = data.copyOf(next)
    }
}
