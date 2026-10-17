package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.data.ChatMedia
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMediaTest {
    @Test
    fun imagesBecomeImagesAndFilesBecomeLinks() {
        assertEquals("Here:\n![chart.png](</tmp/chart.png>)\n", ChatMedia.render("Here:\nMEDIA:/tmp/chart.png\n"))
        assertEquals("open [report.pdf](</tmp/report.pdf>).", ChatMedia.render("open MEDIA:/tmp/report.pdf."))
    }

    @Test
    fun spacesAndQuotes() {
        assertEquals("[report.pdf](</tmp/AI Brain/report.pdf>)", ChatMedia.render("MEDIA:/tmp/AI Brain/report.pdf"))
        assertEquals("[stop!.md](</tmp/stop!.md>)", ChatMedia.render("MEDIA:'/tmp/stop!.md'"))
    }

    @Test
    fun proseStaysProse() {
        assertEquals("MEDIA:... later", ChatMedia.render("MEDIA:... later"))
        assertEquals("no tags here", ChatMedia.render("no tags here"))
    }
}
