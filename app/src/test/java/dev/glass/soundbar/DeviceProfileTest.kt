package dev.glass.soundbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceProfileTest {
    @Test fun unknownDevicesTryBothContracts() {
        val attempts = mutableListOf<DeviceProfile>()
        val selected = DeviceProfile.select("OTHER110") {
            attempts += it
            it == DeviceProfile.ACE_6
        }
        assertEquals(listOf(DeviceProfile.ONEPLUS_15, DeviceProfile.ACE_6), attempts)
        assertEquals(DeviceProfile.ACE_6, selected)
        assertEquals(DeviceProfile.ONEPLUS_15, DeviceProfile.select("OTHER110") { true })
        assertNull(DeviceProfile.select("OTHER110") { false })
    }

    @Test fun knownDevicesPreferTheirContractButAllowRomChanges() {
        assertEquals(DeviceProfile.ACE_6, DeviceProfile.select("PLQ110") { true })
        assertEquals(DeviceProfile.ONEPLUS_15, DeviceProfile.select("PLQ110") { it == DeviceProfile.ONEPLUS_15 })
    }

    class LegacyView { @JvmField var mVolumeBackgroundBlurDrawable: Any? = null }
    class PlatformOwner { @JvmField var panelBackground: Any? = null }
    class PlatformView { @JvmField val blurHostHelper = PlatformOwner() }
    class LegacyHost { fun resolveVisualCapsuleInto(rect: android.graphics.Rect) = Unit }
    class PlatformHost { fun resolveVisualShapeInto(rect: android.graphics.Rect) = Unit }

    @Test fun reflectiveContractsMatchActualFieldChains() {
        DeviceProfile.verifyContract(DeviceProfile.ACE_6, LegacyView::class.java, LegacyHost::class.java)
        DeviceProfile.verifyContract(DeviceProfile.ONEPLUS_15, PlatformView::class.java, PlatformHost::class.java)
    }

    @Test(expected = NoSuchMethodException::class)
    fun mismatchedSliderInterfaceIsRejected() {
        DeviceProfile.verifyContract(DeviceProfile.ONEPLUS_15, PlatformView::class.java, LegacyHost::class.java)
    }

    @Test(expected = NoSuchFieldException::class)
    fun missingNestedBackgroundIsRejected() {
        DeviceProfile.verifyContract(DeviceProfile.ONEPLUS_15, LegacyView::class.java, PlatformHost::class.java)
    }

    @Test fun exactModelsAndProductNamesSelectTheirOwnContract() {
        listOf("PLK110", " plk110 ", "OnePlus 15", "一加15").forEach {
            assertEquals(DeviceProfile.ONEPLUS_15, DeviceProfile.forModel(it))
        }
        listOf("PLQ110", "OnePlus Ace 6", "一加 Ace 6").forEach {
            assertEquals(DeviceProfile.ACE_6, DeviceProfile.forModel(it))
        }
    }

    @Test fun relatedNamesDoNotAccidentallyEnableHooks() {
        listOf("", "PLK110-test", "OnePlus 15R", "OnePlus Ace 6T", "Pixel 9").forEach {
            assertNull(DeviceProfile.forModel(it))
        }
    }
}
