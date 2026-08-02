package com.iblu01.portallauncher.session

/**
 * Abstraction over wall-clock time so the session manager can be tested deterministically.
 */
interface SessionTimeSource {
    fun now(): Long
}

object RealSessionTimeSource : SessionTimeSource {
    override fun now(): Long = System.currentTimeMillis()
}
