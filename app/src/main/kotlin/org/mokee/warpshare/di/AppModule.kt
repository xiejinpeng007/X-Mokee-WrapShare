package org.mokee.warpshare.di

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.os.PowerManager
import androidx.preference.PreferenceManager
import org.mokee.warpshare.presentation.WarpShareApplication
import org.mokee.warpshare.core.certificate.CertificateManager


object AppModule {

    val certificateManager = CertificateManager()

    val mPref: SharedPreferences by lazy{
        PreferenceManager.getDefaultSharedPreferences(WarpShareApplication.instance)
    }

    val bleManager: BluetoothManager by lazy{
        WarpShareApplication.instance.applicationContext
            .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
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