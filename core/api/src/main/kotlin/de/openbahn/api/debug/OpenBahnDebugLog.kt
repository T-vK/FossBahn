package de.openbahn.api.debug

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Debug logging for journey search diagnostics.
 * Always keeps an in-memory ring buffer for the in-app log viewer; logcat output
 * is controlled by [isEnabled].
 */
object OpenBahnDebugLog {
    private const val ROOT = "OpenBahn"
    private const val MAX_ENTRIES = 2_000

    /** Set to true in debug builds to emit verbose search/API logs to logcat. */
    @JvmField
    var isEnabled: Boolean = false

    private val idSeq = AtomicLong(0)
    private val buffer = ArrayDeque<DebugLogEntry>()
    private val _entries = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    val entries: StateFlow<List<DebugLogEntry>> = _entries.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun d(tag: String, message: String) {
        append("D", tag, message)
        if (!isEnabled) return
        Log.d("$ROOT/$tag", message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val full = if (throwable != null) "$message\n${Log.getStackTraceString(throwable)}" else message
        append("W", tag, full)
        if (!isEnabled) return
        if (throwable != null) {
            Log.w("$ROOT/$tag", message, throwable)
        } else {
            Log.w("$ROOT/$tag", message)
        }
    }

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            _entries.value = emptyList()
        }
    }

    fun formattedText(): String = entries.value.joinToString("\n") { it.format(timeFormat) }

    private fun append(level: String, tag: String, message: String) {
        val entry = DebugLogEntry(
            id = idSeq.incrementAndGet(),
            timestampMillis = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
        )
        synchronized(buffer) {
            buffer.addLast(entry)
            while (buffer.size > MAX_ENTRIES) {
                buffer.removeFirst()
            }
            _entries.value = buffer.toList()
        }
    }
}

data class DebugLogEntry(
    val id: Long,
    val timestampMillis: Long,
    val level: String,
    val tag: String,
    val message: String,
) {
    fun format(timeFormat: SimpleDateFormat): String {
        val time = timeFormat.format(Date(timestampMillis))
        return "$time $level/$tag: $message"
    }
}
