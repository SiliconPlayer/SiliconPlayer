package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.desktop.DesktopBackCallback
import com.flopster101.siliconplayer.desktop.DesktopBackDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopBackDispatcherTest {

    @Test
    fun testEmptyDispatcherReturnsFalse() {
        val dispatcher = DesktopBackDispatcher()
        assertFalse(dispatcher.hasActiveHandlers)
        assertFalse(dispatcher.onBackPressed())
    }

    @Test
    fun testDisabledCallbackIsSkipped() {
        val dispatcher = DesktopBackDispatcher()
        var called = false
        val cb = DesktopBackCallback(isEnabled = false, onBack = { called = true })
        dispatcher.register(cb)

        assertFalse(dispatcher.hasActiveHandlers)
        assertFalse(dispatcher.onBackPressed())
        assertFalse(called)
    }

    @Test
    fun testSingleEnabledCallbackIsInvoked() {
        val dispatcher = DesktopBackDispatcher()
        var called = false
        val cb = DesktopBackCallback(isEnabled = true, onBack = { called = true })
        dispatcher.register(cb)

        assertTrue(dispatcher.hasActiveHandlers)
        assertTrue(dispatcher.onBackPressed())
        assertTrue(called)
    }

    @Test
    fun testLifoExecutionOrder() {
        val dispatcher = DesktopBackDispatcher()
        val callOrder = mutableListOf<String>()

        val cb1 = DesktopBackCallback(isEnabled = true, onBack = { callOrder.add("first") })
        val cb2 = DesktopBackCallback(isEnabled = true, onBack = { callOrder.add("second") })
        val cb3 = DesktopBackCallback(isEnabled = true, onBack = { callOrder.add("third") })

        dispatcher.register(cb1)
        dispatcher.register(cb2)
        dispatcher.register(cb3)

        // First back press -> invokes newest ("third")
        assertTrue(dispatcher.onBackPressed())
        assertEquals(listOf("third"), callOrder)

        // Disable "third", next press -> invokes "second"
        cb3.isEnabled = false
        assertTrue(dispatcher.onBackPressed())
        assertEquals(listOf("third", "second"), callOrder)

        // Disable "second", next press -> invokes "first"
        cb2.isEnabled = false
        assertTrue(dispatcher.onBackPressed())
        assertEquals(listOf("third", "second", "first"), callOrder)

        // Disable "first", next press -> none left
        cb1.isEnabled = false
        assertFalse(dispatcher.onBackPressed())
        assertEquals(listOf("third", "second", "first"), callOrder)
    }

    @Test
    fun testUnregisterRemovesCallback() {
        val dispatcher = DesktopBackDispatcher()
        var called = false
        val cb = DesktopBackCallback(isEnabled = true, onBack = { called = true })
        val reg = dispatcher.register(cb)

        assertTrue(dispatcher.hasActiveHandlers)
        reg.unregister()
        assertFalse(dispatcher.hasActiveHandlers)
        assertFalse(dispatcher.onBackPressed())
        assertFalse(called)
    }

    @Test
    fun testSafeMutationDuringCallback() {
        val dispatcher = DesktopBackDispatcher()
        var step = 0
        lateinit var cb2: DesktopBackCallback
        val cb1 = DesktopBackCallback(isEnabled = true, onBack = { step = 1 })
        cb2 = DesktopBackCallback(isEnabled = true, onBack = {
            step = 2
            cb2.isEnabled = false
            dispatcher.register(DesktopBackCallback(isEnabled = true, onBack = { step = 3 }))
        })

        dispatcher.register(cb1)
        dispatcher.register(cb2)

        assertTrue(dispatcher.onBackPressed())
        assertEquals(2, step)

        // Next press should hit the newly registered callback
        assertTrue(dispatcher.onBackPressed())
        assertEquals(3, step)
    }
}
