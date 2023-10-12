/*
 * Copyright (C) 2019 The MoKee Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mokee.warpshare;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.mokee.warpshare.airdrop.AirDropManager;
import org.mokee.warpshare.databinding.ActivitySetupBinding;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS;
import static android.provider.Settings.ACTION_WIFI_SETTINGS;
import static org.mokee.warpshare.airdrop.AirDropManager.STATUS_NO_BLUETOOTH;
import static org.mokee.warpshare.airdrop.AirDropManager.STATUS_NO_WIFI;

public class SetupActivity extends AppCompatActivity {

    private static final String TAG = "SetupActivity";

    private ActivitySetupBinding mBinding;

    private AirDropManager mAirDropManager;
    private final WifiStateMonitor mWifiStateMonitor = new WifiStateMonitor() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateState();
        }
    };

    private final BluetoothStateMonitor mBluetoothStateMonitor = new BluetoothStateMonitor() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateState();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivitySetupBinding.inflate(LayoutInflater.from(this));
        setContentView(mBinding.getRoot());

//        mBinding.perm.setOnClickListener(v -> requestPermission());
        mBinding.wifi.setOnClickListener(v -> setupWifi());
        mBinding.bt.setOnClickListener(v -> turnOnBluetooth());

        mAirDropManager = new AirDropManager(this,
                WarpShareApplication.from(this).getCertificateManager());

        mWifiStateMonitor.register(this);
        mBluetoothStateMonitor.register(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mWifiStateMonitor.unregister(this);
        mBluetoothStateMonitor.unregister(this);

        mAirDropManager.destroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateState();
    }

    private void updateState() {
        final int ready = mAirDropManager.ready();
        if (ready == STATUS_NO_WIFI) {
            mBinding.groupPerm.setVisibility(View.GONE);
            mBinding.groupWifi.setVisibility(View.VISIBLE);
            mBinding.groupBt.setVisibility(View.GONE);
        } else if (ready == STATUS_NO_BLUETOOTH) {
            mBinding.groupPerm.setVisibility(View.GONE);
            mBinding.groupWifi.setVisibility(View.GONE);
            mBinding.groupBt.setVisibility(View.VISIBLE);
        } else {
            setResult(RESULT_OK);
            finish();
        }
    }

    private void setupWifi() {
        startActivity(new Intent(ACTION_WIFI_SETTINGS));
    }

    private void turnOnBluetooth() {
        startActivity(new Intent(ACTION_REQUEST_ENABLE));
    }

}
