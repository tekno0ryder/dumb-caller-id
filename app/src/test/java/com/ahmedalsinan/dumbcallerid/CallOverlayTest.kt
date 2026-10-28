package com.ahmedalsinan.dumbcallerid

import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import android.widget.TextView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.lang.reflect.Proxy
import android.os.Looper
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CallOverlayTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val windowManager get() = context.getSystemService(WindowManager::class.java)

    @After
    fun closePopup() = CallOverlay.dismiss()

    private fun windows(): List<android.view.View> = Shadow.extract<ShadowWindowManagerImpl>(windowManager).views

    // Android attaches a new window on the next frame, like a real phone does before the user can see it.
    private fun show(number: String): Boolean = CallOverlay.show(context, number).also { shadowOf(Looper.getMainLooper()).idle() }

    private fun shownDigits() = windows().single().findViewById<TextView>(R.id.overlayNumber).text.filter { it.isDigit() }.toString()

    @Test
    fun showsOneWindowWithTheNumber() {
        assertTrue(show("+966500000000"))
        assertEquals("966500000000", shownDigits())
    }

    // Call waiting: a second call updates the open popup rather than stacking another.
    @Test
    fun secondCallUpdatesTheSamePopup() {
        show("+966500000000")
        show("+966511111111")
        assertEquals("966511111111", shownDigits())
    }

    @Test
    fun dismissRemovesTheWindow() {
        show("+966500000000")
        CallOverlay.dismiss()
        assertEquals(0, windows().size)
        CallOverlay.dismiss() // the call-ended signal can come twice
    }

    @Test
    fun closeButtonRemovesTheWindow() {
        show("+966500000000")
        windows().single().findViewById<android.view.View>(R.id.overlayClose).performClick()
        assertEquals(0, windows().size)
    }

    // Android refusing the window (e.g. the popup permission revoked just before the call) must not crash the app.
    @Test
    fun refusedWindowReportsFalse() {
        assertFalse(CallOverlay.show(RefusingWindows(context), "+966500000000"))
        assertEquals(0, windows().size)
    }

    // A context whose window manager refuses every new window, as Android does without the popup permission.
    private class RefusingWindows(base: Context) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getSystemService(name: String): Any? {
            val real = super.getSystemService(name)
            if (name != WINDOW_SERVICE) return real
            return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(WindowManager::class.java)) { _, method, args ->
                if (method.name == "addView") throw WindowManager.BadTokenException("permission denied")
                method.invoke(real, *(args ?: emptyArray()))
            }
        }
    }
}
