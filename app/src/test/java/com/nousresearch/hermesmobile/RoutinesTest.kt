package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.data.CronJob
import com.nousresearch.hermesmobile.ui.screens.cronJobOrder
import com.nousresearch.hermesmobile.ui.screens.cronJobState
import com.nousresearch.hermesmobile.ui.screens.cronJobTitle
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutinesTest {
    private fun job(id: String, name: String = id, next: Long? = null, state: String = "", enabled: Boolean = true, prompt: String = "") =
        CronJob(id, name, prompt, "0 9 * * *", "", "local", enabled, "default", state = state, nextRunAt = next)

    @Test
    fun soonestFirstThenJobsThatWillNotFireThenByName() {
        val order = listOf(
            job("later", next = 2_000_000_000), job("paused", next = 1_000_000_000, state = "paused"),
            job("soon", next = 1_500_000_000), job("b-none"), job("a-none")
        ).sortedWith(cronJobOrder).map { it.id }
        assertEquals(listOf("soon", "later", "a-none", "b-none", "paused"), order)
    }

    @Test
    fun stateAndTitleFollowDesktop() {
        assertEquals("disabled", cronJobState(job("x", enabled = false)))
        assertEquals("error", cronJobState(job("x", state = "Error")))
        assertEquals("Water the plants", cronJobTitle(job("x", name = " ", prompt = "Water the plants")))
        assertEquals("a".repeat(60) + "…", cronJobTitle(job("x", name = "", prompt = "a".repeat(80))))
        assertEquals("x", cronJobTitle(job("x", name = "")))
    }
}
