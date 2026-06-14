package com.seina.chan.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 日志上下文，用于追踪当前会话。
 */
object LogContext {
    private val _sessionId = ThreadLocal<String?>()
    var sessionId: String?
        get() = _sessionId.get()
        set(value) = _sessionId.set(value)
}

object FileLogger {
    private var logsDir: File? = null
    private var currentDate: String = ""
    private var currentWriter: FileWriter? = null
    private var currentFileIndex: Int = 0

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private const val MAX_FILE_SIZE = 5L * 1024 * 1024

    private data class LogEntry(
        val level: String,
        val tag: String,
        val message: String,
        val sessionId: String?,
        val timestamp: Long,
        val throwable: Throwable? = null
    )

    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val writerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        startWriter()
    }

    private fun startWriter() {
        writerScope.launch {
            while (true) {
                val entries = mutableListOf<LogEntry>()
                while (true) {
                    val entry = logQueue.poll() ?: break
                    entries.add(entry)
                    if (entries.size >= 10) break
                }
                if (entries.isNotEmpty()) {
                    writeBatch(entries)
                }
                delay(200)
            }
        }
    }

    fun init(context: Context) {
        val externalDir = context.getExternalFilesDir(null)?.parentFile
        val dir = if (externalDir != null) {
            val logDir = File(externalDir, "logs")
            if (logDir.exists() || logDir.mkdirs()) {
                logDir
            } else {
                File(context.filesDir, "logs").also { it.mkdirs() }
            }
        } else {
            File(context.filesDir, "logs").also { it.mkdirs() }
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        logsDir = dir
        Log.i("FileLogger", "FileLogger initialized, dir=${dir.absolutePath}")
        cleanOldLogs()
    }

    private fun cleanOldLogs() {
        val dir = logsDir ?: return
        val files = dir.listFiles { file -> file.isFile && file.name.startsWith("app_") && file.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
        if (files != null && files.size > 7) {
            files.drop(7).forEach { it.delete() }
        }
        val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < sevenDaysAgo) {
                file.delete()
            }
        }
    }

    private fun getLogFileName(today: String, index: Int): String {
        return if (index == 0) "app_$today.log" else "app_${today}_$index.log"
    }

    private fun getWriter(): FileWriter? {
        val dir = logsDir ?: return null
        val today = dateFormat.format(Date())
        if (today != currentDate) {
            try {
                currentWriter?.close()
            } catch (_: IOException) {
                // ignore
            }
            currentWriter = null
            currentDate = today
            currentFileIndex = 0
        }

        while (true) {
            val fileName = getLogFileName(today, currentFileIndex)
            val logFile = File(dir, fileName)
            if (logFile.exists() && logFile.length() > MAX_FILE_SIZE) {
                try {
                    currentWriter?.close()
                } catch (_: IOException) {
                    // ignore
                }
                currentWriter = null
                currentFileIndex++
            } else {
                if (currentWriter == null) {
                    currentWriter = FileWriter(logFile, true)
                }
                return currentWriter
            }
        }
    }

    private fun writeBatch(entries: List<LogEntry>) {
        val writer = getWriter() ?: return
        try {
            for (entry in entries) {
                val time = timeFormat.format(Date(entry.timestamp))
                val thread = Thread.currentThread().name
                val sid = entry.sessionId
                val line = if (sid != null) {
                    "$time [${entry.level}] [$thread] [$sid] ${entry.tag}: ${entry.message}\n"
                } else {
                    "$time [${entry.level}] [$thread] ${entry.tag}: ${entry.message}\n"
                }
                writer.write(line)
                entry.throwable?.let {
                    writer.write(it.stackTraceToString() + "\n")
                }
            }
            writer.flush()
        } catch (_: IOException) {
            // Ignore write errors to avoid infinite loops
        }
    }

    private fun write(level: String, tag: String, message: String, throwable: Throwable? = null) {
        logQueue.add(
            LogEntry(
                level = level,
                tag = tag,
                message = message,
                sessionId = LogContext.sessionId,
                timestamp = System.currentTimeMillis(),
                throwable = throwable
            )
        )
    }

    fun v(tag: String, message: String) = write("VERBOSE", tag, message)
    fun d(tag: String, message: String) = write("DEBUG", tag, message)
    fun i(tag: String, message: String) = write("INFO", tag, message)
    fun w(tag: String, message: String) = write("WARN", tag, message)
    fun w(tag: String, message: String, throwable: Throwable) = write("WARN", tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = write("ERROR", tag, message, throwable)
}
