package studio.pianyu.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessingFailureTest {
    @Test fun onlyAllocationSignalsAreClassifiedAsAllocation() {
        assertEquals(ProcessingFailureKind.ALLOCATION, ProcessingFailure.classify(OutOfMemoryError("heap")))
        assertEquals(ProcessingFailureKind.ALLOCATION, ProcessingFailure.classify(RuntimeException("std::bad_alloc")))
        assertEquals(ProcessingFailureKind.ALLOCATION, ProcessingFailure.classify(RuntimeException("VkResult=-1")))
        assertEquals(ProcessingFailureKind.ALLOCATION, ProcessingFailure.classify(RuntimeException("VK_ERROR_OUT_OF_DEVICE_MEMORY (-2)")))
        assertEquals(ProcessingFailureKind.ALLOCATION, ProcessingFailure.classify(RuntimeException("wrapper", OutOfMemoryError("heap"))))
        assertEquals(ProcessingFailureKind.ALLOCATION, ProcessingFailure.classify(RuntimeException("Vulkan buffer memory failed (VkResult -2)")))
        assertEquals(ProcessingFailureKind.OTHER, ProcessingFailure.classify(RuntimeException("VK_ERROR_DEVICE_LOST (-4)")))
        assertEquals(ProcessingFailureKind.OTHER, ProcessingFailure.classify(RuntimeException("GPU capability unsupported")))
        assertEquals(ProcessingFailureKind.OTHER, ProcessingFailure.classify(RuntimeException("unsupported memorytype")))
        assertEquals(ProcessingFailureKind.OTHER, ProcessingFailure.classify(RuntimeException("result=-1")))
    }

    @Test fun exportMessageNeverPromisesSilentDownscale() {
        val message = ProcessingFailure.message(ProcessingStage.EXPORT, OutOfMemoryError())
        assertTrue(message.contains("重试"))
        assertTrue(message.contains("较小尺寸"))
        assertTrue(!message.contains("自动"))
    }
}
