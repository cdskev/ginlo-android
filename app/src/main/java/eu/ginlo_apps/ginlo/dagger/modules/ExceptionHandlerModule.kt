// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.dagger.modules

import dagger.Module
import dagger.Provides
import eu.ginlo_apps.ginlo.context.GinloUncaughtExceptionHandler
import eu.ginlo_apps.ginlo.log.Logger
import javax.inject.Singleton

@Module
class ExceptionHandlerModule {
    @Provides
    @Singleton
    fun provideUncaughtExceptionHandler(logger: Logger) =
        GinloUncaughtExceptionHandler(Thread.getDefaultUncaughtExceptionHandler() as Thread.UncaughtExceptionHandler, logger)
}