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
package org.mokee.warpshare.nearbysharing

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.microsoft.connecteddevices.AsyncOperationWithProgress
import com.microsoft.connecteddevices.ConnectedDevicesAccessTokenInvalidatedEventArgs
import com.microsoft.connecteddevices.ConnectedDevicesAccessTokenRequestedEventArgs
import com.microsoft.connecteddevices.ConnectedDevicesAccount
import com.microsoft.connecteddevices.ConnectedDevicesAccountManager
import com.microsoft.connecteddevices.ConnectedDevicesAddAccountResult
import com.microsoft.connecteddevices.ConnectedDevicesNotificationRegistrationManager
import com.microsoft.connecteddevices.ConnectedDevicesNotificationRegistrationStateChangedEventArgs
import com.microsoft.connecteddevices.ConnectedDevicesPlatform
import com.microsoft.connecteddevices.remotesystems.RemoteSystemAddedEventArgs
import com.microsoft.connecteddevices.remotesystems.RemoteSystemAuthorizationKind
import com.microsoft.connecteddevices.remotesystems.RemoteSystemAuthorizationKindFilter
import com.microsoft.connecteddevices.remotesystems.RemoteSystemDiscoveryType
import com.microsoft.connecteddevices.remotesystems.RemoteSystemDiscoveryTypeFilter
import com.microsoft.connecteddevices.remotesystems.RemoteSystemFilter
import com.microsoft.connecteddevices.remotesystems.RemoteSystemRemovedEventArgs
import com.microsoft.connecteddevices.remotesystems.RemoteSystemStatusType
import com.microsoft.connecteddevices.remotesystems.RemoteSystemStatusTypeFilter
import com.microsoft.connecteddevices.remotesystems.RemoteSystemWatcher
import com.microsoft.connecteddevices.remotesystems.commanding.RemoteSystemConnectionRequest
import com.microsoft.connecteddevices.remotesystems.commanding.nearshare.NearShareFileProvider
import com.microsoft.connecteddevices.remotesystems.commanding.nearshare.NearShareHelper
import com.microsoft.connecteddevices.remotesystems.commanding.nearshare.NearShareProgress
import com.microsoft.connecteddevices.remotesystems.commanding.nearshare.NearShareSender
import com.microsoft.connecteddevices.remotesystems.commanding.nearshare.NearShareStatus
import org.mokee.warpshare.base.DiscoverListener
import org.mokee.warpshare.base.Discoverer
import org.mokee.warpshare.domain.data.Entity
import org.mokee.warpshare.base.SendListener
import org.mokee.warpshare.base.Sender
import org.mokee.warpshare.base.SendingSession
import java.util.concurrent.atomic.AtomicBoolean

class NearShareManager(val context: Context) : Discoverer, Sender<NearSharePeer> {
    private val mHandler = Handler(Looper.getMainLooper())
    private val mPeers: MutableMap<String, NearSharePeer> = HashMap()
    private var mPlatform: ConnectedDevicesPlatform = setupPlatform(context)

    private var mRemoteSystemWatcher: RemoteSystemWatcher? = setupWatcher()
    private val mNearShareSender = NearShareSender()
    private var mDiscoverListener: DiscoverListener? = null

    fun destroy() {
        mPlatform.shutdownAsync()
        mHandler.removeCallbacksAndMessages(null)
    }

    private fun setupPlatform(context: Context): ConnectedDevicesPlatform {
        return ConnectedDevicesPlatform(context.applicationContext).apply {

            accountManager.accessTokenRequested()
                .subscribe { manager: ConnectedDevicesAccountManager?, args: ConnectedDevicesAccessTokenRequestedEventArgs? ->

                }
            accountManager.accessTokenInvalidated()
                .subscribe { manager: ConnectedDevicesAccountManager?, args: ConnectedDevicesAccessTokenInvalidatedEventArgs? ->

                }
            notificationRegistrationManager.notificationRegistrationStateChanged()
                .subscribe { manager: ConnectedDevicesNotificationRegistrationManager?, args: ConnectedDevicesNotificationRegistrationStateChangedEventArgs? ->

                }
            start()

//            setupAnonymousAccount
            val account = ConnectedDevicesAccount.getAnonymousAccount()
            accountManager.addAccountAsync(account)
                .whenComplete { result: ConnectedDevicesAddAccountResult?, tr: Throwable? ->
                    if (tr != null) {
                        Log.e(TAG, "Failed creating anonymous account", tr)
                    }
                }
        }
    }

    private fun setupWatcher(): RemoteSystemWatcher {
        val filters: MutableList<RemoteSystemFilter> = ArrayList()
        filters.add(RemoteSystemDiscoveryTypeFilter(RemoteSystemDiscoveryType.PROXIMAL))
        filters.add(RemoteSystemStatusTypeFilter(RemoteSystemStatusType.ANY))
        filters.add(RemoteSystemAuthorizationKindFilter(RemoteSystemAuthorizationKind.ANONYMOUS))
        return RemoteSystemWatcher(filters).apply {
            remoteSystemAdded()
                .subscribe { _: RemoteSystemWatcher?, args: RemoteSystemAddedEventArgs ->
                    val peer = NearSharePeer.from(args.remoteSystem)
                    val connectionRequest = RemoteSystemConnectionRequest(peer.remoteSystem)
                    if (mNearShareSender.isNearShareSupported(connectionRequest)) {
                        Log.d(TAG, "Found: " + peer.id + " (" + peer.name + ")")
                        mPeers[peer.id] = peer
                        mHandler.post { mDiscoverListener?.onPeerFound(peer) }
                    }
                }
            remoteSystemRemoved()
                .subscribe { _: RemoteSystemWatcher?, args: RemoteSystemRemovedEventArgs ->
                    val peer = mPeers.remove(args.remoteSystem.id)
                    if (peer != null) {
                        mHandler.post { mDiscoverListener?.onPeerDisappeared(peer) }
                    }
                }
        }
    }

    override fun startDiscover(discoverListener: DiscoverListener) {
        mDiscoverListener = discoverListener
        mRemoteSystemWatcher?.start()
    }

    override fun stopDiscover() {
        mRemoteSystemWatcher?.stop()
    }

    override fun send(
        peer: NearSharePeer,
        entities: List<Entity>,
        listener: SendListener
    ): SendingSession {
        val connectionRequest = RemoteSystemConnectionRequest(peer.remoteSystem)
        val operation: AsyncOperationWithProgress<NearShareStatus, NearShareProgress>
        if (entities.size == 1) {
            val fileProvider = NearShareHelper.createNearShareFileFromContentUri(
                entities[0].uri, context
            )
            operation = mNearShareSender.sendFileAsync(connectionRequest, fileProvider)
        } else {
            val fileProviders = arrayOfNulls<NearShareFileProvider>(entities.size)
            for (i in entities.indices) {
                fileProviders[i] = NearShareHelper.createNearShareFileFromContentUri(
                    entities[i].uri, context
                )
            }
            operation = mNearShareSender.sendFilesAsync(connectionRequest, fileProviders)
        }
        val accepted = AtomicBoolean(false)
        operation.progress()
            .subscribe { op: AsyncOperationWithProgress<NearShareStatus, NearShareProgress>?, progress: NearShareProgress ->
                if (progress.filesSent != 0 || progress.totalFilesToSend != 0) {
                    if (accepted.compareAndSet(false, true)) {
                        mHandler.post { listener.onAccepted() }
                    }
                    mHandler.post {
                        listener.onProgress(
                            progress.bytesSent,
                            progress.totalBytesToSend
                        )
                    }
                }
            }
        operation.whenComplete { status: NearShareStatus, tr: Throwable? ->
            if (tr != null) {
                Log.e(TAG, "Failed sending files to " + peer.name, tr)
                mHandler.post { listener.onSendFailed() }
            } else if (status != NearShareStatus.COMPLETED) {
                Log.e(TAG, "Failed sending files to " + peer.name + ": " + status)
                mHandler.post { listener.onSendFailed() }
            } else {
                mHandler.post { listener.onSent() }
            }
        }
        return object : SendingSession() {
            override fun cancel() {
                operation.cancel(true)
            }
        }
    }

    companion object {
        private const val TAG = "NearShareManager"
    }
}
