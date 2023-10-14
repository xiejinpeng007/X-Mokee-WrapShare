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
package org.mokee.warpshare.ui.service

import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.content.FileProvider
import okio.buffer
import okio.sink
import okio.source
import org.mokee.warpshare.ConfigManager
import org.mokee.warpshare.PartialWakeLock
import org.mokee.warpshare.R
import org.mokee.warpshare.airdrop.AirDropManager
import org.mokee.warpshare.airdrop.ReceiverListener
import org.mokee.warpshare.airdrop.ReceivingSession
import org.mokee.warpshare.di.AppModule
import org.mokee.warpshare.di.AppModule2
import org.mokee.warpshare.ui.receiver.BluetoothStateMonitor
import org.mokee.warpshare.ui.receiver.WifiStateMonitor
import org.mokee.warpshare.ui.settings.SettingsActivity
import java.io.File
import java.io.IOException
import java.io.InputStream

class ReceiverService : Service(), ReceiverListener {
    private val mAirDropManager = AppModule.mAirDropManager

    private val mDevices: MutableSet<String> = HashSet()
    private val mSessions: MutableMap<String?, ReceivingSession> = HashMap()
    private var mRunning = false
    private var mWakeLock = PartialWakeLock(AppModule2.powerManager, TAG)
    private var mNotificationManager: NotificationManager? = null
    private val mWifiStateMonitor: WifiStateMonitor = object : WifiStateMonitor() {
        override fun onReceive(context: Context, intent: Intent) {
            stopIfNotReady()
        }
    }
    private val mBluetoothStateMonitor: BluetoothStateMonitor = object : BluetoothStateMonitor() {
        override fun onReceive(context: Context, intent: Intent) {
            stopIfNotReady()
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        mWifiStateMonitor.register(this)
        mBluetoothStateMonitor.register(this)
        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val serviceChannel: NotificationChannel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_SERVICE,
                getString(R.string.notif_recv_service_channel), NotificationManager.IMPORTANCE_MIN
            )
            serviceChannel.enableLights(false)
            serviceChannel.enableVibration(false)
            serviceChannel.setShowBadge(false)
            mNotificationManager?.createNotificationChannel(serviceChannel)
            val transferChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_TRANSFER,
                getString(R.string.notif_recv_transfer_channel), NotificationManager.IMPORTANCE_HIGH
            )
            mNotificationManager?.createNotificationChannel(transferChannel)
        }
        startForeground(
            NOTIFICATION_ACTIVE,
            getNotificationBuilder(NOTIFICATION_CHANNEL_SERVICE, Notification.CATEGORY_SERVICE)
                .setContentTitle(getString(R.string.notif_recv_active_title))
                .setContentText(getString(R.string.notif_recv_active_desc))
                .addAction(
                    Notification.Action.Builder(
                        null,
                        getString(R.string.settings),
                        PendingIntent.getActivity(
                            this, 0,
                            Intent(this, SettingsActivity::class.java),
                            PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    )
                        .build()
                )
                .setOngoing(true)
                .build()
        )
        stopIfNotReady()
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        mWifiStateMonitor.unregister(this)
        mBluetoothStateMonitor.unregister(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }else{
            stopForeground(true)
        }
    }

    private fun stopIfNotReady() {
        if (mAirDropManager.ready() != AirDropManager.STATUS_OK) {
            Log.w(TAG, "Hardware not ready, quit")
            stopSelf()
        }
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: $intent")
        val action = intent.action
        if (ACTION_SCAN_RESULT == action) {
            val callbackType = intent.getIntExtra(
                BluetoothLeScanner.EXTRA_CALLBACK_TYPE,
                ScanSettings.CALLBACK_TYPE_ALL_MATCHES
            )
            val results: List<ScanResult>? =
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
                    intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java)
                }else{
                    intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)
                }
            handleScanResult(callbackType, results)
        } else if (ACTION_TRANSFER_ACCEPT == action) {
            val data = intent.data
            if (data != null) {
                handleTransferAccept(data.path)
            }
        } else if (ACTION_TRANSFER_REJECT == action) {
            val data = intent.data
            if (data != null) {
                handleTransferReject(data.path)
            }
        } else if (ACTION_TRANSFER_CANCEL == action) {
            val data = intent.data
            if (data != null) {
                handleTransferCancel(data.path)
            }
        }
        return START_STICKY
    }

    private fun handleScanResult(callbackType: Int, results: List<ScanResult>?) {
        if (results != null) {
            if (callbackType == ScanSettings.CALLBACK_TYPE_FIRST_MATCH) {
                for (result in results) {
                    mDevices.add(result.device.address)
                }
            } else if (callbackType == ScanSettings.CALLBACK_TYPE_MATCH_LOST) {
                for (result in results) {
                    mDevices.remove(result.device.address)
                }
            }
        }
        if (mRunning && mDevices.isEmpty()) {
            Log.d(TAG, "Peers lost, sleep")
            mAirDropManager.stopDiscoverable()
            stopSelf()
            mRunning = false
        } else if (!mRunning && mDevices.isNotEmpty()) {
            Log.d(TAG, "Peers discovering")
            if (mAirDropManager.ready() != AirDropManager.STATUS_OK) {
                Log.w(TAG, "Hardware not ready, quit")
                stopSelf()
                return
            }
            mAirDropManager.startDiscoverable(this)
            mRunning = true
        }
    }

    override fun onAirDropRequest(session: ReceivingSession) {
        Log.d(TAG, "Asking from " + session.name + " (" + session.ip + ")")
        mSessions[session.ip] = session
        val builder =
            getNotificationBuilder(NOTIFICATION_CHANNEL_TRANSFER, Notification.CATEGORY_STATUS)
                .setContentTitle(getString(R.string.notif_recv_transfer_request_title))
                .setContentText(
                    resources.getQuantityString(
                        R.plurals.notif_recv_transfer_request_desc, session.paths.size,
                        session.name, session.paths.size
                    )
                )
                .addAction(
                    Notification.Action.Builder(
                        null,
                        getString(R.string.notif_recv_transfer_request_accept),
                        getTransferIntent(ACTION_TRANSFER_ACCEPT, session.ip)
                    )
                        .build()
                )
                .addAction(
                    Notification.Action.Builder(
                        null,
                        getString(R.string.notif_recv_transfer_request_reject),
                        getTransferIntent(ACTION_TRANSFER_REJECT, session.ip)
                    )
                        .build()
                )
                .setDeleteIntent(getTransferIntent(ACTION_TRANSFER_REJECT, session.ip))
        if (session.preview != null) {
            builder.setLargeIcon(session.preview)
            builder.setStyle(
                Notification.BigPictureStyle()
                    .bigLargeIcon(null as Icon?)
                    .bigPicture(session.preview)
            )
        }
        mNotificationManager?.notify(session.ip, NOTIFICATION_TRANSFER, builder.build())
        mWakeLock.acquire()
    }

    override fun onAirDropRequestCanceled(session: ReceivingSession) {
        Log.d(TAG, "Transfer ask canceled")
        mNotificationManager?.cancel(session.ip, NOTIFICATION_TRANSFER)
        mWakeLock.release()
    }

    private fun handleTransferAccept(ip: String?) {
        val session = mSessions[ip]
        if (session != null) {
            Log.d(TAG, "Transfer accepted")
            session.accept()
            mNotificationManager?.notify(
                ip, NOTIFICATION_TRANSFER,
                getNotificationBuilder(NOTIFICATION_CHANNEL_TRANSFER, Notification.CATEGORY_STATUS)
                    .setContentTitle(
                        resources.getQuantityString(
                            R.plurals.notif_recv_transfer_progress_title, session.paths.size,
                            session.paths.size, session.name
                        )
                    )
                    .setContentText(
                        getString(
                            R.string.notif_recv_transfer_progress_desc,
                            0, session.paths.size
                        )
                    )
                    .setProgress(0, 0, true)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build()
            )
        }
    }

    private fun handleTransferReject(ip: String?) {
        val session = mSessions.remove(ip)
        if (session != null) {
            Log.d(TAG, "Transfer rejected")
            session.reject()
        }
        mNotificationManager?.cancel(ip, NOTIFICATION_TRANSFER)
        mWakeLock.release()
    }

    override fun onAirDropTransfer(
        session: ReceivingSession,
        fileName: String,
        input: InputStream
    ) {
        Log.d(TAG, "Transferring " + fileName + " from " + session.name)

        val targetFileName = session.getFileName(fileName)
        val downloadDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadDir, targetFileName)
        try {
            val source = input.source()
            val sink = file.sink().buffer()
            sink.writeAll(source)
            sink.flush()
            sink.close()
            Log.d(TAG, "Received $fileName as $targetFileName")
        } catch (e: IOException) {
            Log.e(TAG, "Failed writing file to " + file.absolutePath, e)
        }
    }

    private fun handleTransferCancel(ip: String?) {
        val session = mSessions.remove(ip)
        session?.cancel()
        mNotificationManager?.cancel(ip, NOTIFICATION_TRANSFER)
        mWakeLock.release()
    }

    override fun onAirDropTransferProgress(
        session: ReceivingSession,
        fileName: String,
        bytesReceived: Long,
        bytesTotal: Long,
        index: Int, count: Int
    ) {
        mNotificationManager?.notify(
            session.ip, NOTIFICATION_TRANSFER,
            getNotificationBuilder(NOTIFICATION_CHANNEL_TRANSFER, Notification.CATEGORY_STATUS)
                .setContentTitle(
                    resources.getQuantityString(
                        R.plurals.notif_recv_transfer_progress_title, session.paths.size,
                        session.paths.size, session.name
                    )
                )
                .setContentText(
                    getString(
                        R.string.notif_recv_transfer_progress_desc,
                        index + 1, count
                    )
                )
                .setProgress(bytesTotal.toInt(), bytesReceived.toInt(), false)
                .addAction(
                    Notification.Action.Builder(
                        null,
                        getString(R.string.notif_recv_transfer_progress_cancel),
                        getTransferIntent(ACTION_TRANSFER_CANCEL, session.ip)
                    )
                        .build()
                )
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        )
    }

    override fun onAirDropTransferDone(session: ReceivingSession) {
        Log.d(TAG, "All files received")
        mNotificationManager?.cancel(session.ip, NOTIFICATION_TRANSFER)
        val shareIntent: Intent
        if (session.paths.size > 1) {
            val uris = ArrayList<Uri>()
            for (path in session.paths) {
                uris.add(getUriForReceivedFile(session.getFileName(path)))
            }
            shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE)
            shareIntent.putExtra(Intent.EXTRA_STREAM, uris)
        } else {
            val uri = getUriForReceivedFile(session.getFileName(session.paths[0]))
            shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        }
        shareIntent.setType(getGeneralMimeType(session.types))
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val builder =
            getNotificationBuilder(NOTIFICATION_CHANNEL_TRANSFER, Notification.CATEGORY_STATUS)
                .setContentTitle(
                    resources.getQuantityString(
                        R.plurals.notif_recv_transfer_done_title, session.paths.size,
                        session.paths.size, session.name
                    )
                )
                .setContentText(getString(R.string.notif_recv_transfer_done_desc))
                .setContentIntent(
                    PendingIntent.getActivity(
                        this, 0,
                        Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                .addAction(
                    Notification.Action.Builder(
                        null,
                        getString(R.string.notif_recv_transfer_done_share),
                        PendingIntent.getActivity(
                            this, 0,
                            Intent.createChooser(shareIntent, null)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    )
                        .build()
                )
        if (session.preview != null) {
            builder.setLargeIcon(session.preview)
            builder.setStyle(
                Notification.BigPictureStyle()
                    .bigLargeIcon(null as Icon?)
                    .bigPicture(session.preview)
            )
        }
        mNotificationManager?.notify(session.ip, NOTIFICATION_TRANSFER, builder.build())
        mSessions.remove(session.ip)
        mWakeLock.release()
    }

    override fun onAirDropTransferFailed(session: ReceivingSession) {
        Log.d(TAG, "Receiving aborted")
        mNotificationManager?.cancel(session.ip, NOTIFICATION_TRANSFER)
        mNotificationManager?.notify(
            session.ip, NOTIFICATION_TRANSFER,
            getNotificationBuilder(NOTIFICATION_CHANNEL_TRANSFER, Notification.CATEGORY_STATUS)
                .setContentTitle(getString(R.string.notif_recv_transfer_failed_title))
                .setContentText(
                    getString(
                        R.string.notif_recv_transfer_failed_desc,
                        session.name
                    )
                )
                .build()
        )
        mSessions.remove(session.ip)
        mWakeLock.release()
    }

    private fun getTransferIntent(action: String, ip: String): PendingIntent {
        val intent = Intent(action, null, this, javaClass)
            .setData(Uri.Builder().path(ip).build())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    @Suppress("DEPRECATION")
    private fun getNotificationBuilder(channelId: String, category: String): Notification.Builder {
        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }
        return builder.setCategory(category)
            .setSmallIcon(R.drawable.ic_notification_white_24dp)
            .setColor(getColor(R.color.primary))
    }

    private fun getUriForReceivedFile(fileName: String): Uri {
        val downloadDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadDir, fileName)
        return FileProvider.getUriForFile(this, "org.mokee.warpshare.files", file)
    }

    private fun getGeneralMimeType(mimeTypes: List<String>): String {
        var generalType: String? = null
        var generalSubtype: String? = null
        for (mimeType in mimeTypes) {
            val segments =
                mimeType.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (segments.size != 2) continue
            val type = segments[0]
            val subtype = segments[1]
            if (type == "*" && subtype != "*") continue
            if (generalType == null) {
                generalType = type
                generalSubtype = subtype
                continue
            }
            if (generalType != type) {
                generalType = "*"
                generalSubtype = "*"
                break
            }
            if (generalSubtype != subtype) {
                generalSubtype = "*"
            }
        }
        if (generalType == null) {
            generalType = "*"
        }
        if (generalSubtype == null) {
            generalSubtype = "*"
        }
        return "$generalType/$generalSubtype"
    }

    companion object {
        private const val TAG = "ReceiverService"
        private const val ACTION_SCAN_RESULT = "org.mokee.warpshare.SCAN_RESULT"
        private const val ACTION_TRANSFER_ACCEPT = "org.mokee.warpshare.TRANSFER_ACCEPT"
        private const val ACTION_TRANSFER_REJECT = "org.mokee.warpshare.TRANSFER_REJECT"
        private const val ACTION_TRANSFER_CANCEL = "org.mokee.warpshare.TRANSFER_CANCEL"
        private const val NOTIFICATION_CHANNEL_SERVICE = "receiver"
        private const val NOTIFICATION_CHANNEL_TRANSFER = "transfer"
        private const val NOTIFICATION_ACTIVE = 1
        private const val NOTIFICATION_TRANSFER = 2
        fun getTriggerIntent(context: Context?): PendingIntent {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context, 0,
                    Intent(
                        ACTION_SCAN_RESULT,
                        null,
                        context,
                        ReceiverService::class.java
                    ),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getService(
                    context, 0,
                    Intent(
                        ACTION_SCAN_RESULT,
                        null,
                        context,
                        ReceiverService::class.java
                    ),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
        }

        fun updateDiscoverability(context: Context) {
            val packageManager = context.packageManager
            
            val isDiscoverable = ConfigManager.isDiscoverable
            val state = if (isDiscoverable) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            packageManager.setComponentEnabledSetting(
                ComponentName(context, ReceiverService::class.java),
                state, PackageManager.DONT_KILL_APP
            )
            if (!isDiscoverable) {
                context.stopService(Intent(context, ReceiverService::class.java))
            }
        }
    }
}
