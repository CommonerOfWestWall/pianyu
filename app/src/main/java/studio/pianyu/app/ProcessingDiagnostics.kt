package studio.pianyu.app

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process

/** Small local-only breadcrumbs for diagnosing a process exit after a photo operation. */
class ProcessingDiagnostics(context: Context, private val nativeCheckpoint: Boolean = false) {
    private val app = context.applicationContext
    // Decode and the serialized native worker can overlap when a photo is changed.
    // Finishing decode must not erase the native worker's in-flight checkpoint.
    private val prefs = app.getSharedPreferences(
        if (nativeCheckpoint) "processing-native-checkpoint" else "processing-diagnostics", Context.MODE_PRIVATE)
    private val reports = app.getSharedPreferences("processing-diagnostics", Context.MODE_PRIVATE)

    fun checkpoint(stage: ProcessingStage, width: Int? = null, height: Int? = null, look: String? = null) {
        prefs.edit().putLong(KEY_TIME, System.currentTimeMillis())
            .putInt(KEY_PID, Process.myPid())
            .putString(KEY_STAGE, stage.name)
            .putInt(KEY_WIDTH, width ?: 0).putInt(KEY_HEIGHT, height ?: 0)
            .putString(KEY_LOOK, look?.take(MAX_FIELD).orEmpty()).commit()
    }

    fun record(stage: ProcessingStage, error: Throwable): String {
        val raw = errorChain(error).joinToString(" | ") { "${it.first}: ${it.second}" }
        val text = buildString {
            append(header(stage, error))
            appendCheckpoint(this)
            append("\nrawclass=").append(error::class.java.name)
            append("\nmessage=").append(sanitize(raw))
        }.take(MAX_REPORT)
        reports.edit().putString(KEY_REPORT, text).commit()
        return text
    }

    fun finished() {
        prefs.edit().remove(KEY_TIME).remove(KEY_STAGE).remove(KEY_WIDTH)
            .remove(KEY_HEIGHT).remove(KEY_LOOK).remove(KEY_PID).commit()
    }

    /** Returns a report only when a checkpoint proves the process exited afterwards. */
    fun recoverPreviousExit(): String? {
        val imageReport = recoverOwnExit()
        if (nativeCheckpoint) return imageReport
        val native = ProcessingDiagnostics(app, nativeCheckpoint = true)
        val nativeReport = native.recoverOwnExit()
        if (native.prefs.getInt(KEY_PID, -1) != Process.myPid()) native.finished()
        return (nativeReport ?: imageReport)?.also { reports.edit().putString(KEY_REPORT, it).commit() }
    }

    private fun recoverOwnExit(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val checkpoint = prefs.getLong(KEY_TIME, 0L)
        if (checkpoint == 0L) return null
        val manager = app.getSystemService(ActivityManager::class.java) ?: return null
        val latestMain = manager.getHistoricalProcessExitReasons(app.packageName, 0, 20)
            .asSequence()
            .filter { it.processName == app.packageName }
            .maxByOrNull { it.timestamp } ?: return null
        val exit = latestMain.takeIf { it.pid == prefs.getInt(KEY_PID, -1) && it.timestamp >= checkpoint }
            ?.takeIf { it.reason == android.app.ApplicationExitInfo.REASON_LOW_MEMORY ||
                it.reason == android.app.ApplicationExitInfo.REASON_CRASH ||
                it.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE ||
                it.reason == android.app.ApplicationExitInfo.REASON_ANR ||
                it.reason == android.app.ApplicationExitInfo.REASON_SIGNALED ||
                it.reason == android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE ||
                it.reason == android.app.ApplicationExitInfo.REASON_OTHER ||
                it.reason == android.app.ApplicationExitInfo.REASON_UNKNOWN } ?: return null
        val stage = prefs.getString(KEY_STAGE, null)?.let { runCatching { ProcessingStage.valueOf(it) }.getOrNull() }
            ?: return null
        val report = buildString {
            append(header(stage, null)).append("\nprevious_exit=").append(reasonName(exit.reason))
            append("\nstatus=").append(exit.status)
            append("\nexitDescription=").append(sanitize(exit.description.orEmpty()))
            append("\nexitTimestamp=").append(exit.timestamp)
            append("\nexitPssKb=").append(exit.pss)
            append("\nexitRssKb=").append(exit.rss)
            append("\nlowMemoryKillReportSupported=").append(ActivityManager.isLowMemoryKillReportSupported())
            append("\ncheckpoint=").append(checkpoint)
            prefs.getInt(KEY_WIDTH, 0).takeIf { it > 0 }?.let { append("\nwidth=").append(it) }
            prefs.getInt(KEY_HEIGHT, 0).takeIf { it > 0 }?.let { append("\nheight=").append(it) }
            prefs.getString(KEY_LOOK, null)?.takeIf { it.isNotEmpty() }?.let { append("\nlook=").append(sanitize(it)) }
        }.take(MAX_REPORT)
        return report
    }

    fun lastReport(): String? = reports.getString(KEY_REPORT, null)

    private fun appendCheckpoint(out: StringBuilder) {
        prefs.getInt(KEY_WIDTH, 0).takeIf { it > 0 }?.let { out.append("\ncheckpointWidth=").append(it) }
        prefs.getInt(KEY_HEIGHT, 0).takeIf { it > 0 }?.let { out.append("\ncheckpointHeight=").append(it) }
        prefs.getString(KEY_LOOK, null)?.takeIf { it.isNotEmpty() }?.let { out.append("\ncheckpointLook=").append(sanitize(it)) }
    }

    private fun header(stage: ProcessingStage, error: Throwable?): String = buildString {
        append("Pianyu processing diagnostic")
        append("\nmanufacturer=").append(Build.MANUFACTURER)
        append("\nmodel=").append(Build.MODEL)
        append("\nhardware=").append(Build.HARDWARE)
        append("\nsdk=").append(Build.VERSION.SDK_INT)
        val version = packageVersion()
        append("\nversionName=").append(version.first)
        append("\nversionCode=").append(version.second)
        append("\nstage=").append(stage.name)
        append("\nheapSample=report_created_not_previous_exit")
        val runtime = Runtime.getRuntime()
        append("\nheapBytes=").append(runtime.totalMemory()).append('/').append(runtime.maxMemory())
        append("\nheapFreeBytes=").append(runtime.freeMemory())
        if (error != null) append("\nkind=").append(ProcessingFailure.classify(error).name)
    }

    private fun packageVersion(): Pair<String, Long> = runCatching {
        @Suppress("DEPRECATION")
        app.packageManager.getPackageInfo(app.packageName, 0).let { it.versionName.orEmpty() to it.longVersionCode }
    }.getOrDefault("unknown" to -1L)

    private fun reasonName(reason: Int): String = when (reason) {
        android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        android.app.ApplicationExitInfo.REASON_CRASH -> "CRASH"
        android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        android.app.ApplicationExitInfo.REASON_ANR -> "ANR"
        android.app.ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        android.app.ApplicationExitInfo.REASON_OTHER -> "OTHER"
        android.app.ApplicationExitInfo.REASON_UNKNOWN -> "UNKNOWN"
        else -> reason.toString()
    }

    private fun errorChain(error: Throwable): List<Pair<String, String>> = buildList {
        var current: Throwable? = error
        repeat(4) {
            current ?: return@repeat
            add(current!!::class.java.name to current!!.message.orEmpty())
            current = current!!.cause
        }
    }

    private fun sanitize(value: String): String = value.replace(Regex("(?i)\\b(?:content|file|https?)://[^\\s]+"), "[uri]")
        .replace(Regex("(?:[A-Za-z]:\\\\|/)[^\\s]+"), "[path]")
        .replace(Regex("\\s+"), " ").trim().take(MAX_FIELD)

    private companion object {
        const val KEY_TIME = "checkpoint_time"
        const val KEY_PID = "checkpoint_pid"
        const val KEY_STAGE = "checkpoint_stage"
        const val KEY_WIDTH = "checkpoint_width"
        const val KEY_HEIGHT = "checkpoint_height"
        const val KEY_LOOK = "checkpoint_look"
        const val KEY_REPORT = "last_report"
        const val MAX_FIELD = 240
        const val MAX_REPORT = 1800
    }
}
