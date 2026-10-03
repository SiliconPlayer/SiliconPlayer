package com.flopster101.siliconplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeColumnSplitTest {
    @Test
    fun fillsTopToBottomBeforeNextColumn() {
        val items = (0 until 10).toList()
        val columns = splitIntoColumns(items, 3)
        assertEquals(3, columns.size)
        assertEquals(listOf(0, 1, 2, 3), columns[0].map { it.first })
        assertEquals(listOf(4, 5, 6), columns[1].map { it.first })
        assertEquals(listOf(7, 8, 9), columns[2].map { it.first })
    }

    @Test
    fun singleColumnKeepsOrder() {
        val items = listOf("a", "b", "c")
        val columns = splitIntoColumns(items, 1)
        assertEquals(1, columns.size)
        assertEquals(listOf(0, 1, 2), columns[0].map { it.first })
    }

    @Test
    fun clampsColumnCountToItemCount() {
        val columns = splitIntoColumns(listOf("a", "b"), 5)
        assertEquals(2, columns.size)
        assertEquals("a", columns[0].single().second)
        assertEquals("b", columns[1].single().second)
    }
}
