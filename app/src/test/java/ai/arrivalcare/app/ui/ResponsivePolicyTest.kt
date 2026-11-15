package ai.arrivalcare.app.ui
import org.junit.Assert.*
import org.junit.Test

class ResponsivePolicyTest {
    @Test fun phonePortraitUsesStack(){assertFalse(ResponsivePolicy.sideBySide(393,700))}
    @Test fun vehicleLandscapeUsesColumns(){assertTrue(ResponsivePolicy.sideBySide(1280,600))}
    @Test fun narrowMultiWindowUsesStack(){assertFalse(ResponsivePolicy.sideBySide(480,500))}
    @Test fun phoneLandscapeUsesColumns(){assertTrue(ResponsivePolicy.sideBySide(780,280))}
    @Test fun shortWindowLeavesGuidanceSpace(){assertTrue(ResponsivePolicy.mapHeight(250)<=112)}
    @Test fun x86CannotLoadArmEngine(){assertNotNull(MapAvailability.reason(true,"x86_64","key",0x30000))}
    @Test fun missingKeyFallsBack(){assertNotNull(MapAvailability.reason(true,"arm64-v8a","",0x30000))}
    @Test fun supportedConfiguredArmCanInitialize(){assertNull(MapAvailability.reason(true,"arm64-v8a","key",0x20000))}
    @Test fun unsupportedGraphicsFallsBack(){assertNotNull(MapAvailability.reason(true,"arm64-v8a","key",0x10000))}
    @Test fun compatibilityBuildDoesNotLoadNativeEngine(){assertNotNull(MapAvailability.reason(false,"arm64-v8a","key",0x30000))}
}
