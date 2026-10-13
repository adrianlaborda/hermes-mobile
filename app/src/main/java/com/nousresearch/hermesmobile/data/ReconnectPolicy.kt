package com.nousresearch.hermesmobile.data

/** Equal jitter: never spin, never exceed 30s, no overflow after prolonged outages. */
object ReconnectPolicy {
    fun delayMillis(attempt: Int, random: Double): Long {
        val ceiling = (1_000L shl attempt.coerceIn(0, 5)).coerceAtMost(30_000L)
        return ceiling / 2 + ((ceiling / 2) * random.coerceIn(0.0, 1.0)).toLong()
    }
}
