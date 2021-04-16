// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.dagger.modules

import dagger.Binds
import dagger.Module
import eu.ginlo_apps.ginlo.context.GinloLifecycleObserver
import eu.ginlo_apps.ginlo.context.GinloLifecycleObserverImpl
import eu.ginlo_apps.ginlo.controller.GinloAppLifecycle
import eu.ginlo_apps.ginlo.controller.GinloAppLifecycleImpl
import eu.ginlo_apps.ginlo.data.network.AppConnectivity
import eu.ginlo_apps.ginlo.data.network.AppConnectivityImpl
import eu.ginlo_apps.ginlo.data.preferences.SecurePreferences
import eu.ginlo_apps.ginlo.data.preferences.SecurePreferencesImpl
import eu.ginlo_apps.ginlo.log.Logger
import eu.ginlo_apps.ginlo.log.LoggerImpl
import eu.ginlo_apps.ginlo.router.Router
import eu.ginlo_apps.ginlo.router.RouterImpl

@Suppress("unused")
@Module
internal abstract class ApplicationModuleBase {
    @Binds
    internal abstract fun bindRouter(router: RouterImpl): Router

    @Binds
    internal abstract fun bindLogger(logger: LoggerImpl): Logger

    @Binds
    internal abstract fun bindGinloAppLifecycle(appLifecycle: GinloAppLifecycleImpl): GinloAppLifecycle

    @Binds
    internal abstract fun bindLifecycleObserver(ginloLifecycleObserver: GinloLifecycleObserverImpl): GinloLifecycleObserver

    @Binds
    internal abstract fun bindAppConnectivity(appConnectivityImpl: AppConnectivityImpl): AppConnectivity

    @Binds
    internal abstract fun bindSecurePreferences(securePreferencesImpl: SecurePreferencesImpl): SecurePreferences
}