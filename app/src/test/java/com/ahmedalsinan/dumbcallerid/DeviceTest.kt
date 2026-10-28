package com.ahmedalsinan.dumbcallerid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class DeviceTest {
    @Test
    fun recognisesMakersAndSubBrands() {
        assertEquals(Oem.SAMSUNG, Oem.from("samsung", "samsung"))
        assertEquals(Oem.XIAOMI, Oem.from("Xiaomi", "Redmi"))
        assertEquals(Oem.XIAOMI, Oem.from("Xiaomi", "POCO"))
        assertEquals(Oem.OPPO, Oem.from("realme", "realme"))
        assertEquals(Oem.OPPO, Oem.from("OnePlus", "OnePlus"))
        assertEquals(Oem.VIVO, Oem.from("vivo", "iQOO"))
        assertEquals(Oem.HUAWEI, Oem.from("HONOR", "HONOR"))
        assertEquals(Oem.HUAWEI, Oem.from("HUAWEI", "HUAWEI"))
    }

    @Test
    fun otherMakersGetNoExtraSteps() {
        assertEquals(Oem.OTHER, Oem.from("motorola", "motorola"))
        assertEquals(Oem.OTHER, Oem.from("Google", "google"))
        assertEquals(emptyList<ManualStep>(), ManualSteps.forOem(Oem.OTHER))
    }

    @Test
    fun batteryLayoutFollowsAndroidVersion() {
        assertEquals(BatteryUi.OPTIMIZATION_LIST, BatteryUi.forSdk(29)) // Android 10
        assertEquals(BatteryUi.OPTIMIZATION_LIST, BatteryUi.forSdk(30)) // 11
        assertEquals(BatteryUi.UNRESTRICTED, BatteryUi.forSdk(31)) // 12
        assertEquals(BatteryUi.UNRESTRICTED, BatteryUi.forSdk(33)) // 13
        assertEquals(BatteryUi.BACKGROUND_USAGE, BatteryUi.forSdk(34)) // 14
        assertEquals(BatteryUi.BACKGROUND_USAGE, BatteryUi.forSdk(36)) // 16
    }

    // The one-tap dialog first everywhere, then the settings page for that Android version.
    @Test
    fun batteryTriesTheDialogFirst() {
        for (ui in BatteryUi.entries) {
            assertEquals("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", ui.screens.first().action)
            assertTrue(ui.screens.size > 1)
        }
    }

    @Test
    fun overlayTextFollowsAndroidVersion() {
        assertEquals(R.string.next_overlay_direct, overlayNext(29))
        assertEquals(R.string.next_overlay, overlayNext(30))
    }

    @Test
    fun manualStepsHaveScreensAndUniqueKeys() {
        val steps = Oem.entries.flatMap(ManualSteps::forOem)
        for (step in steps) assertTrue("${step.key} has no screens", step.screens.isNotEmpty())
        assertEquals(steps.size, steps.map { it.key }.toSet().size)
    }

    // The battery step's Unrestricted covers Samsung; its "Never auto sleeping apps" list refuses such apps.
    @Test
    fun samsungNeedsNoExtraSteps() = assertEquals(emptyList<ManualStep>(), ManualSteps.forOem(Oem.SAMSUNG))

    @Test
    fun everyScreenIsDeclaredInManifestQueries() {
        val queries = Queries.read(File("src/main/AndroidManifest.xml"))
        val screens = Oem.entries.flatMap(ManualSteps::forOem).flatMap { it.screens } +
            BatteryUi.entries.flatMap { it.screens }
        for (screen in screens) {
            when {
                // An explicit component is visible through its package.
                screen.pkg != null -> assertTrue("missing <package ${screen.pkg}>", screen.pkg in queries.packages)
                else -> {
                    val schemes = queries.actions[screen.action]
                    assertTrue("missing <intent> for ${screen.action}", schemes != null)
                    if (screen.withPackageUri) {
                        assertTrue("${screen.action} needs <data scheme=\"package\">", "package" in schemes!!)
                    }
                }
            }
        }
    }

    // The manifest's <queries>: declared packages, and each declared intent action with its data schemes.
    private class Queries(val packages: Set<String>, val actions: Map<String, Set<String>>) {
        companion object {
            fun read(manifest: File): Queries {
                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)
                val queries = doc.getElementsByTagName("queries").item(0) as Element
                fun Element.children(tag: String) =
                    (0 until getElementsByTagName(tag).length).map { getElementsByTagName(tag).item(it) as Element }
                val packages = queries.children("package").map { it.getAttribute("android:name") }.toSet()
                val actions = queries.children("intent").associate { intent ->
                    intent.children("action").single().getAttribute("android:name") to
                        intent.children("data").map { it.getAttribute("android:scheme") }.toSet()
                }
                return Queries(packages, actions)
            }
        }
    }
}
