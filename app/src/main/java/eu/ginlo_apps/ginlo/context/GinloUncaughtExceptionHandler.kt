// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.context

import eu.ginlo_apps.ginlo.log.Logger

class GinloUncaughtExceptionHandler(
    private val defaultExceptionHandler: Thread.UncaughtExceptionHandler,
    private val logger: Logger
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread, e: Throwable) {
        logger.error(composeMessage(e))
        if ((e as? SecurityException)?.message?.contains("Caller no longer running") == true) {
            // this is related to JobIntentService started from Async Task
            // https://issuetracker.google.com/issues/63622293
            // Do not crash in this case, just "Let One Go" (https://www.youtube.com/watch?v=ZpuN-Cc-cWU)
            return
        }
        defaultExceptionHandler.uncaughtException(t, e)
    }

    private fun composeMessage(e: Throwable) =
        with(StringBuilder("!! FATAL ERROR !! ")) {
            append(e.javaClass.name)
            append(", cause = ")
            append(e.message)
            append("\n")
            append(extractTrace(e))
            toString()
        }

    private fun extractTrace(e: Throwable) =
        e.stackTrace.map { it.toString() }
            .filter { it.startsWith(this.javaClass.getPackage()?.name.orEmpty()) }
            .joinToString("\n") { it }
}