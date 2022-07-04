// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.log;

import org.jetbrains.annotations.NotNull;

import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.log.Logger;

public class LogUtil {
    private static Logger logger;

    public static void init(Logger loggerInstance) {
        logger = loggerInstance;
    }

    public static void d(final String source, final String msg) {
        if (BuildConfig.DEBUG && msg != null) {
            logger.debug(source + ": " + msg);
        }
    }

    public static void d(final String source, final String msg, final Throwable tr) {
        if (BuildConfig.DEBUG) {
            if (msg != null) {
                logger.debug(source + ": " + msg);

                if (tr != null) {
                    for (StackTraceElement traceElement : tr.getStackTrace()) {
                        logger.debug(traceElement.toString());
                    }
                }
            }
        }
    }

    public static void i(final String source, final String msg) {
        if (msg != null) {
            logger.info(source + ": " + msg);
        }
    }

    public static void w(final String source, final String msg) {
        if (msg != null) {
            logger.warn(source + ": " + msg);
        }
    }

    public static void w(final String source, final String msg, final Throwable tr) {
        if (msg != null) {
            logger.warn(source + ": " + msg);
        }

        if (tr != null) {
            for (StackTraceElement traceElement : tr.getStackTrace()) {
                logger.warn(traceElement.toString());
            }
        }
    }

    public static void e(final String source, final String msg) {
        if (msg != null) {
            logger.error(source + ": " + msg);
        }
    }

    public static void e(final Throwable e) {
        if (e != null) {
            logger.error(e);
        }
    }

    public static void e(@NotNull final String source, final String msg, final Throwable tr) {
        if (msg != null) {
            logger.error(source + ": " + msg);
        }

        if (tr != null) {
            for (StackTraceElement traceElement : tr.getStackTrace()) {
                logger.error(traceElement.toString());
            }
        }
    }
}
