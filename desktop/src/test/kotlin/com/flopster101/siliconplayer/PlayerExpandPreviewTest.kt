package com.flopster101.siliconplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerExpandPreviewTest {

    @Test
    fun previewVisibleOnlyWhileSurfaceShownUnexpandedAndDragging() {
        assertTrue(playerDragPreviewVisible(true, false, 0.1f))
        assertFalse(playerDragPreviewVisible(false, false, 0.5f))
        assertFalse(playerDragPreviewVisible(true, true, 0.5f))
        assertFalse(playerDragPreviewVisible(true, false, 0f))
    }

    @Test
    fun previewOffsetTravelsFromScreenBottomToZero() {
        assertEquals(1000f, playerPreviewOffsetPx(0f, 1000f), 0.001f)
        assertEquals(500f, playerPreviewOffsetPx(0.5f, 1000f), 0.001f)
        assertEquals(0f, playerPreviewOffsetPx(1f, 1000f), 0.001f)
    }


    @Test
    fun miniHiddenOnlyForExpandPaths() {
        assertTrue(miniPlayerHiddenForExpand(true, false, false))
        assertTrue(miniPlayerHiddenForExpand(false, true, false))
        assertTrue(miniPlayerHiddenForExpand(false, false, true))
        assertFalse(miniPlayerHiddenForExpand(false, false, false))
    }
}
