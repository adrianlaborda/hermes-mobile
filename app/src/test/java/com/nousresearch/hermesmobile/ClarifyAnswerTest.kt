package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.data.ClarifyAnswer
import com.nousresearch.hermesmobile.data.ClarifyOption
import org.junit.Assert.assertEquals
import org.junit.Test

class ClarifyAnswerTest {
    @Test fun parsesEnglishRecommendedMarker() {
        assertEquals(ClarifyOption("X", true), ClarifyAnswer.parseOption("X (Recommended)"))
    }

    @Test fun parsesSpanishRecommendedMarker() {
        assertEquals(ClarifyOption("X", true), ClarifyAnswer.parseOption("X (Recomendado)"))
    }

    @Test fun stripsStackedMarkersRepeatedly() {
        assertEquals(ClarifyOption("X", true), ClarifyAnswer.parseOption("X (Recomendado) (Recommended)"))
    }

    @Test fun leavesPlainChoicesUntouched() {
        assertEquals(ClarifyOption("X", false), ClarifyAnswer.parseOption("X"))
    }

    @Test fun keepsMarkerWordsInsideTheLabel() {
        assertEquals(ClarifyOption("Use (Recommended) settings", false), ClarifyAnswer.parseOption("Use (Recommended) settings"))
    }

    @Test fun matchesMarkersCaseInsensitively() {
        assertEquals(ClarifyOption("X", true), ClarifyAnswer.parseOption("X (recommended)"))
    }
}
