package com.ahmedalsinan.dumbcallerid

import org.junit.Assert.assertEquals
import org.junit.Test

class PopupPositionTest {
    @Test
    fun startsAQuarterDownUntilDragged() = assertEquals(600, popupTop(null, 2400, 200))

    // Dragged near the bottom while upright, the next call comes in landscape: it must stay fully visible.
    @Test
    fun staysOnScreenAfterRotating() {
        val fraction = topFraction(2200, 2400)
        assertEquals(1080 - 200, popupTop(fraction, 1080, 200))
    }

    @Test
    fun keepsItsRelativePlaceOnAnotherScreenSize() = assertEquals(540, popupTop(topFraction(1200, 2400), 1080, 200))

    @Test
    fun neverGoesAboveTheTop() = assertEquals(0, clampTop(-50, 2400, 200))

    @Test
    fun cardTallerThanTheScreenSticksToTheTop() = assertEquals(0, popupTop(0.9f, 300, 500))

    @Test
    fun unknownScreenHeightFallsBackToTheDefault() = assertEquals(DEFAULT_TOP_FRACTION, topFraction(100, 0))
}
