package org.mokee.warpshare.di

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.os.PowerManager
import androidx.preference.PreferenceManager
import org.mokee.warpshare.core.certificate.CertificateManager
import org.mokee.warpshare.presentation.WarpShareApplication


object AppModule {

    val certificateManager = CertificateManager()

    val mPref: SharedPreferences by lazy{
        PreferenceManager.getDefaultSharedPreferences(WarpShareApplication.instance)
    }

    val powerManager: PowerManager by lazy{
        WarpShareApplication.instance.applicationContext
            .getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    val wifiManager: WifiManager by lazy{
        WarpShareApplication.instance.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
}