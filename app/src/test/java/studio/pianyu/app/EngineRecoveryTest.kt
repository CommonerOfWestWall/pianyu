package studio.pianyu.app

import org.junit.Assert.*
import org.junit.Test

class EngineRecoveryTest {
    @Test fun deviceLossInsideCauseChainIsDistinctFromAllocation() {
        assertEquals(ProcessingFailureKind.DEVICE_LOST, ProcessingFailure.classify(
            RuntimeException("render failed", IllegalStateException("Vulkan upload failed (VkResult -4)"))))
        assertEquals(ProcessingFailureKind.DEVICE_LOST,
            ProcessingFailure.classify(IllegalStateException("VK_ERROR_DEVICE_LOST")))
        assertEquals(ProcessingFailureKind.OTHER,
            ProcessingFailure.classify(IllegalStateException("VkResult -40")))
        assertEquals(ProcessingFailureKind.ALLOCATION,
            ProcessingFailure.classify(IllegalStateException("VkResult -2")))
    }

    @Test fun lostDeviceIsInvalidatedAndSameOperationRetriedOnce() {
        var attempts = 0; var invalidations = 0
        val result = retryLostDevice(invalidate = { invalidations++ }) {
            if (++attempts == 1) throw IllegalStateException("VkResult -4")
            42
        }
        assertEquals(42, result)
        assertEquals(2, attempts)
        assertEquals(1, invalidations)
    }

    @Test fun repeatedLossLeavesNoBrokenEngineForNextPhoto() {
        var attempts = 0; var invalidations = 0
        val error = IllegalStateException("VkResult -4")
        assertSame(error, assertThrows(IllegalStateException::class.java) {
            retryLostDevice(invalidate = { invalidations++ }) { attempts++; throw error }
        })
        assertEquals(2, attempts)
        assertEquals(2, invalidations)
    }

    @Test fun otherFailuresAreNotSilentlyRetried() {
        var invalidations = 0; var attempts = 0
        assertThrows(SecurityException::class.java) {
            retryLostDevice(invalidate = { invalidations++ }) { attempts++; throw SecurityException("permission") }
        }
        assertEquals(1, attempts)
        assertEquals(0, invalidations)
    }
}
