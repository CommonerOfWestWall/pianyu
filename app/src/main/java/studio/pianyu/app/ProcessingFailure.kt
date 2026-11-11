package studio.pianyu.app

/** The user-visible phase in which a photo operation is running. */
enum class ProcessingStage(val label: String) {
    DECODE("解码"), ENGINE("胶片引擎"), PREVIEW("预览"), EXPORT("导出")
}

enum class ProcessingFailureKind { ALLOCATION, OTHER }

/** Carries the report captured before the engine checkpoint was cleared. */
class ProcessingException(val report: String, cause: Throwable) : RuntimeException(cause.message, cause)

object ProcessingFailure {
    private val vkResult = Regex("(?i)\\bVkResult\\s*[:=]?\\s*\\(?-(?:1|2)\\b")
    private val vkMemory = Regex("(?i)\\b(?:VK_ERROR_)?OUT_OF_(?:HOST|DEVICE)_MEMORY\\b")

    fun classify(error: Throwable): ProcessingFailureKind {
        if (error is OutOfMemoryError) return ProcessingFailureKind.ALLOCATION
        val text = buildString {
            val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
            var current: Throwable? = error
            repeat(8) {
                val next = current ?: return@repeat
                if (!seen.add(next)) return@repeat
                append(next::class.java.name).append(' ').append(next.message.orEmpty()).append(' ')
                current = next.cause
            }
        }
        if (text.contains("OutOfMemoryError", ignoreCase = true) ||
            text.contains("bad_alloc", ignoreCase = true) || vkResult.containsMatchIn(text) || vkMemory.containsMatchIn(text)) {
            return ProcessingFailureKind.ALLOCATION
        }
        return ProcessingFailureKind.OTHER
    }

    fun message(stage: ProcessingStage, error: Throwable): String {
        val kind = classify(error)
        return when (kind) {
            ProcessingFailureKind.ALLOCATION -> when (stage) {
                ProcessingStage.EXPORT -> "当前尺寸导出时内存分配失败；可重试或选择较小尺寸，原片未改动。"
                else -> "${stage.label}失败（内存分配）：设备可用内存不足，请关闭其他应用后重试。"
            }
            ProcessingFailureKind.OTHER -> "${stage.label}失败：请重试；如果问题持续，请复制诊断信息。"
        }
    }
}
