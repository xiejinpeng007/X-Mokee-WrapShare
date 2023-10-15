package org.mokee.warpshare.presentation

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources

val Int.vdp
    get() = Resources.getSystem().displayMetrics.density * this + 0.5f

val Context.isNightMode
    get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES