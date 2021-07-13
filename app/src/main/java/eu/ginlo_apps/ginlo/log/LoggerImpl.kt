// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.log

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.android.LogcatAppender
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy
import ch.qos.logback.core.util.FileSize
import eu.ginlo_apps.ginlo.BuildConfig
import eu.ginlo_apps.ginlo.context.SimsMeApplication
import org.slf4j.LoggerFactory
import java.io.File
import java.io.PrintWriter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoggerImpl @Inject constructor(application: SimsMeApplication) : Logger {

    companion object {
        private const val DEFAULT_LOGGER_NAME = "ginlo"
        private const val LOG_FILE_PREFIX = "roll_log"
        private const val LOG_FILE_SUFFIX = "txt"
        private const val LOG_ACTIVE_FILE = "$LOG_FILE_PREFIX.$LOG_FILE_SUFFIX"
        private const val LOG_FILE_PATTERN =
            "$LOG_FILE_PREFIX.%i.$LOG_FILE_SUFFIX" // %i denote the index for size based roll over
        private const val LOG_FILE_DIR = "log_files"
        private const val LOG_FILE_CONCATED_PREFIX = "ginlo_log"
        private const val LOG_FILE_MSG_PATTERN =
            "{\"time\":\"%d{dd-MM-yyyy HH:mm:ss.SSS}\",\"level\":\"%-5level\",\"msg\":\"%msg\"}%n"
        private const val LOG_LOGCAT_MSG_PATTERN = "%msg%n"
    }

    private var logDir = File("${application.filesDir.absolutePath}/$LOG_FILE_DIR/")

    init {
        if (!logDir.exists()) {
            logDir.mkdir()
        }
        configure()
    }

    private fun configure() {
        val lc = LoggerFactory.getILoggerFactory() as LoggerContext
        lc.reset()

        val root =
            LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        if (BuildConfig.DEBUG)
            root.level = Level.DEBUG
        else
            root.level = Level.INFO

        setupFileAppender(lc, root)
        setupLogcatAppender(lc, root)
    }

    private fun setupLogcatAppender(lc: LoggerContext, root: ch.qos.logback.classic.Logger) {
        val logCatEncoder = PatternLayoutEncoder().apply {
            context = lc
            pattern = LOG_LOGCAT_MSG_PATTERN
            start()
        }

        val logcatAppender = LogcatAppender().apply {
            context = lc
            encoder = logCatEncoder
            start()
        }

        root.addAppender(logcatAppender)
    }

    private fun setupFileAppender(lc: LoggerContext, root: ch.qos.logback.classic.Logger) {

        val rollingFileAppender = RollingFileAppender<ILoggingEvent>().apply {
            context = lc
            lazy = true
            isAppend = true
            file = "${logDir.absolutePath}/$LOG_ACTIVE_FILE"
        }

        val fixedWindowRollingPolicy = FixedWindowRollingPolicy().apply {
            setParent(rollingFileAppender)
            context = lc
            minIndex = 1
            maxIndex = 4
            fileNamePattern = "${logDir.absolutePath}/$LOG_FILE_PATTERN"
            start()
        }

        val sizeBasedTriggeringPolicy = SizeBasedTriggeringPolicy<ILoggingEvent>().apply {
            maxFileSize = FileSize.valueOf("1MB")
            context = lc
            start()
        }

        val fileEncoder = PatternLayoutEncoder().apply {
            context = lc
            pattern = LOG_FILE_MSG_PATTERN
            start()
        }

        rollingFileAppender.apply {
            encoder = fileEncoder
            rollingPolicy = fixedWindowRollingPolicy
            triggeringPolicy = sizeBasedTriggeringPolicy
            start()
        }

        root.addAppender(rollingFileAppender)
    }

    override fun debug(message: String) {
        LoggerFactory.getLogger(DEFAULT_LOGGER_NAME).debug("${thread()} $message")
    }

    override fun info(message: String) {
        LoggerFactory.getLogger(DEFAULT_LOGGER_NAME).info("${thread()} $message")
    }

    override fun warn(message: String) {
        LoggerFactory.getLogger(DEFAULT_LOGGER_NAME).warn("${version()} ${thread()} $message")
    }

    override fun error(message: String) {
        LoggerFactory.getLogger(DEFAULT_LOGGER_NAME).error("${version()} ${thread()} $message")
    }

    override fun error(e: Throwable) {
        LoggerFactory.getLogger(DEFAULT_LOGGER_NAME).error("${version()} ${thread()} $e")
    }

    override fun getLog(): File? {
        val outputLogFile = createOutputLogFile()
        val files = logDir.listFiles { _: File?, name: String? ->
            name?.startsWith(LOG_FILE_PREFIX) ?: false
        }

        files?.sortBy { it.lastModified() }
        files?.forEach { file ->
            outputLogFile?.appendText(file.readText())
        }

        return outputLogFile
    }

    private fun createOutputLogFile(): File? {
        val outputFile = File(
            logDir,
            "${LOG_FILE_CONCATED_PREFIX}_${System.currentTimeMillis()}.$LOG_FILE_SUFFIX"
        )
        outputFile.createNewFile()
        PrintWriter(outputFile).use { out -> out.println("{\"versionName\":\"${BuildConfig.VERSION_NAME}\"}") }
        return outputFile
    }

    private fun thread() = if (BuildConfig.DEBUG) "[${Thread.currentThread().name}]" else ""
    private fun version(): String = "[${BuildConfig.VERSION_CODE.toString()}]"
}