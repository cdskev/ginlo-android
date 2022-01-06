// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo

import android.app.Application
import android.content.Context
import android.graphics.PorterDuff
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import eu.ginlo_apps.ginlo.context.SimsMeApplication
import eu.ginlo_apps.ginlo.theme.ThemedLayoutInflater
import eu.ginlo_apps.ginlo.util.ColorUtil

internal inline fun View.setThrottledClick(
    throttleTimeMs: Long = 1000L,
    crossinline block: () -> Unit
) {
    var lastClickMillis: Long = 0
    this.setOnClickListener {
        synchronized(this) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastClickMillis > throttleTimeMs) {
                lastClickMillis = now
                block()
            }
        }
    }
}

internal fun LayoutInflater.themedInflate(context: Context?, layoutId: Int, rootView: ViewGroup?): View =
    getInflatedView(this, context, layoutId, rootView, rootView != null)

internal fun LayoutInflater.themedInflate(
    context: Context?,
    layoutId: Int,
    rootView: ViewGroup?,
    attachToParent: Boolean
): View =
    getInflatedView(this, context, layoutId, rootView, attachToParent)

internal fun LayoutInflater.themedInflater(context: Context?): LayoutInflater =
    this.cloneInContext(context).also { inflater ->
        inflater.factory2 = ThemedLayoutInflater(context, inflater.factory, inflater.factory2)
    }

private fun getInflatedView(
    baseInflater: LayoutInflater,
    context: Context?,
    layoutId: Int,
    rootView: ViewGroup?,
    attachToParent: Boolean
): View =
    baseInflater.themedInflater(context).inflate(layoutId, rootView, attachToParent)

internal fun MenuItem.applyColorFilter(application: Application) {
    if(this.icon != null) {
        ColorUtil.setColorFilter(this.icon, ColorUtil.getInstance().getMainContrast80Color(application))
    }
}