// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.router

import eu.ginlo_apps.ginlo.controller.GinloAppLifecycle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouterImpl @Inject constructor(appLifecycle: GinloAppLifecycle) : RouterBaseImpl(appLifecycle), Router
