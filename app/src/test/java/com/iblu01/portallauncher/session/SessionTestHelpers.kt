package com.iblu01.portallauncher.session

class TestTimeSource(private var time: Long = 0L) : SessionTimeSource {
    override fun now(): Long = time
    fun advance(ms: Long) { time += ms }
    fun set(timeMs: Long) { time = timeMs }
}
