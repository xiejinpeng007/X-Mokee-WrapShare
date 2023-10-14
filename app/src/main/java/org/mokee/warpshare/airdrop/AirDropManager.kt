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
package org.mokee.warpshare.airdrop

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import com.dd.plist.NSArray
import com.dd.plist.NSDictionary
import com.google.gson.Gson
import com.google.gson.JsonObject
import okio.Pipe
import okio.buffer
import org.mokee.warpshare.GossipyInputStream
import org.mokee.warpshare.airdrop.AirDropArchiveUtil.FileFactory
import org.mokee.warpshare.airdrop.AirDropPeer.Companion.from
import org.mokee.warpshare.airdrop.client.AirDropClient
import org.mokee.warpshare.airdrop.client.AirDropClientCallback
import org.mokee.warpshare.airdrop.server.AirDropServer
import org.mokee.warpshare.airdrop.server.ResultCallback
import org.mokee.warpshare.base.DiscoverListener
import org.mokee.warpshare.base.Discoverer
import org.mokee.warpshare.base.Entity
import org.mokee.warpshare.base.SendListener
import org.mokee.warpshare.base.Sender
import org.mokee.warpshare.base.SendingSession
import org.mokee.warpshare.certificate.CertificateManager
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class AirDropManager(context: Context, certificateManager: CertificateManager) : Discoverer,
    Sender<AirDropPeer> {
    private val mConfigManager: AirDropConfigManager
    private val mBleController: AirDropBleController
    private val mNsdController: AirDropNsdController
    private val mWlanController: AirDropWlanController
    private val mClient: AirDropClient
    private val mServer: AirDropServer
    private val mPeers: MutableMap<String, AirDropPeer> = HashMap()
    private val mReceivingSessions: MutableMap<String, ReceivingSession> = HashMap()
    private var mDiscoverListener: DiscoverListener? = null
    private var mReceiverListener: ReceiverListener? = null
    private val mArchiveExecutor: ExecutorService
    private val mMainThreadHandler = Handler(Looper.getMainLooper())

    init {
        mConfigManager = AirDropConfigManager(context)
        mBleController = AirDropBleController(context)
        mNsdController = AirDropNsdController(context, mConfigManager, this)
        mWlanController = AirDropWlanController()
        mClient = AirDropClient(certificateManager)
        mServer = AirDropServer(certificateManager, this)
        mArchiveExecutor = Executors.newFixedThreadPool(10)
    }

    private fun totalLength(entities: List<Entity>): Long {
        var total: Long = -1
        for (entity in entities) {
            val size = entity.size()
            if (size >= 0) {
                if (total == -1L) {
                    total = size
                } else {
                    total += size
                }
            }
        }
        return total
    }

    fun ready(): Int {
        if (!mBleController.ready()) {
            return STATUS_NO_BLUETOOTH
        }
        if (!mWlanController.ready()) {
            return STATUS_NO_WIFI
        }
        mClient.setNetworkInterface(mWlanController.networkInterface)
        return STATUS_OK
    }

    override fun startDiscover(discoverListener: DiscoverListener) {
        if (ready() != STATUS_OK) {
            return
        }
        mDiscoverListener = discoverListener
        mBleController.triggerDiscoverable()
        mWlanController.localAddress?.also {
            mNsdController.startDiscover(it)
        }
    }

    override fun stopDiscover() {
        mBleController.stop()
        mNsdController.stopDiscover()
    }

    fun startDiscoverable(receiverListener: ReceiverListener?) {
        if (ready() != STATUS_OK) {
            return
        }
        mReceiverListener = receiverListener
        mWlanController.localAddress?.also{
            it.hostAddress?.also { address ->
                val port = mServer.start(address)
                mNsdController.publish(it, port)
            }
        }
    }

    fun stopDiscoverable() {
        mNsdController.unpublish()
        mServer.stop()
    }

    fun destroy() {
        mNsdController.destroy()
        mArchiveExecutor.shutdownNow()
    }

    fun registerTrigger(pendingIntent: PendingIntent) {
        mBleController.registerTrigger(pendingIntent)
    }

    fun onServiceResolved(id: String, url: String) {
        val req = NSDictionary()
        mClient.post("$url/Discover", req, object : AirDropClientCallback {
            override fun onFailure(e: IOException) {
                Log.w(TAG, "Failed to discover: $id", e)
            }

            override fun onResponse(response: NSDictionary) {
                val peer = from(response, id, url) ?: return
                mPeers[id] = peer
                mDiscoverListener!!.onPeerFound(peer)
            }
        })
    }

    fun onServiceLost(id: String) {
        val peer = mPeers.remove(id)
        if (peer != null) {
            mDiscoverListener!!.onPeerDisappeared(peer)
        }
    }

    override fun send(
        peer: AirDropPeer,
        entities: List<Entity>,
        listener: SendListener
    ): SendingSession {
        Log.d(TAG, "Asking " + peer.id + " to receive " + entities.size + " files")
        val ref = AtomicReference<Cancelable>()
        val thumbnailCanceled = AtomicBoolean(false)
        val firstType = entities[0].type()
        if (!TextUtils.isEmpty(firstType) && firstType.startsWith("image/")) {
            ref.set(object:Cancelable {
                override fun cancel() {
                    thumbnailCanceled.set(true)
                }
            })
            mArchiveExecutor.execute {
                val thumbnail = AirDropThumbnailUtil.generate(entities[0])
                mMainThreadHandler.post {
                    if (!thumbnailCanceled.get()) {
                        ask(ref, peer, thumbnail, entities, listener)
                    }
                }
            }
        } else {
            ask(ref, peer, null, entities, listener)
        }
        return object : SendingSession() {
            override fun cancel() {
                val cancelable = ref.getAndSet(null)
                if (cancelable != null) {
                    cancelable.cancel()
                    Log.d(TAG, "Canceled")
                }
            }
        }
    }

    private fun ask(
        ref: AtomicReference<Cancelable>, peer: AirDropPeer, icon: ByteArray?,
        entities: List<Entity>, listener: SendListener
    ) {
        val req = NSDictionary()
        req.put("SenderID", mConfigManager.id)
        req.put("SenderComputerName", mConfigManager.name)
        req.put("BundleID", "com.apple.finder")
        req.put("ConvertMediaFormats", false)
        val files: MutableList<NSDictionary> = ArrayList()
        for (entity in entities) {
            val file = NSDictionary()
            file.put("FileName", entity.name())
            file.put("FileType", AirDropTypes.getEntryType(entity))
            file.put("FileBomPath", entity.path())
            file.put("FileIsDirectory", false)
            file.put("ConvertMediaFormats", 0)
            files.add(file)
        }
        req.put("Files", files)
        if (icon != null) {
            req.put("FileIcon", icon)
        }
        val call = mClient.post(peer.url + "/Ask", req,
            object : AirDropClientCallback {
                override fun onFailure(e: IOException) {
                    Log.w(TAG, "Failed to ask: " + peer.id, e)
                    ref.set(null)
                    listener.onRejected()
                }

                override fun onResponse(response: NSDictionary) {
                    Log.d(TAG, "Accepted")
                    listener.onAccepted()
                    upload(ref, peer, entities, listener)
                }
            })
        ref.set(object:Cancelable{
            override fun cancel() {
                call?.cancel()
            }
        })
    }

    private fun upload(
        ref: AtomicReference<Cancelable>, peer: AirDropPeer,
        entities: List<Entity>, listener: SendListener
    ) {
        val archive = Pipe(1024)
        val bytesTotal = totalLength(entities)
        val streamReadListener: GossipyInputStream.Listener = object : GossipyInputStream.Listener {
            private var bytesSent: Long = 0
            override fun onRead(length: Int) {
                if (bytesTotal == -1L) {
                    return
                }
                bytesSent += length.toLong()
                mMainThreadHandler.post { listener.onProgress(bytesSent, bytesTotal) }
            }
        }
        val call = mClient.post(peer.url + "/Upload",
            archive.source.buffer().inputStream(),
            object : AirDropClientCallback {
                override fun onFailure(e: IOException) {
                    Log.e(TAG, "Failed to upload: " + peer.id, e)
                    ref.set(null)
                    listener.onSendFailed()
                }

                override fun onResponse(response: NSDictionary) {
                    Log.d(TAG, "Uploaded")
                    ref.set(null)
                    listener.onSent()
                }
            })
        ref.set(object:Cancelable {
            override fun cancel() {
                call.cancel()
            }
        })
        mArchiveExecutor.execute {
            try {
                archive.sink.buffer().use { sink ->
                    AirDropArchiveUtil.pack(
                        entities,
                        sink.outputStream(),
                        streamReadListener
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to pack upload payload: " + peer.id, e)
                mMainThreadHandler.post { listener.onSendFailed() }
            }
        }
    }

    fun handleDiscover(
        ip: String,
        request: NSDictionary?,
        callback: ResultCallback
    ) {
        val mokee = JsonObject()
        mokee.addProperty("APIVersion", 1)
        val vendor = JsonObject()
        vendor.add("org.mokee", mokee)
        val capabilities = JsonObject()
        capabilities.addProperty("Version", 1)
        capabilities.add("Vendor", vendor)
        val response = NSDictionary()
        response.put("ReceiverComputerName", mConfigManager.name)
        response.put("ReceiverMediaCapabilities", Gson().toJson(capabilities).toByteArray())
        callback.call(response)
    }

    fun handleAsk(ip: String, request: NSDictionary, callback: ResultCallback) {
        val idNode = request["SenderID"]
        if (idNode == null) {
            Log.w(TAG, "Invalid ask from $ip: Missing SenderID")
            callback.call(null)
            return
        }
        val nameNode = request["SenderComputerName"]
        if (nameNode == null) {
            Log.w(TAG, "Invalid ask from $ip: Missing SenderComputerName")
            callback.call(null)
            return
        }
        val filesNode = request["Files"]
        if (filesNode == null) {
            Log.w(TAG, "Invalid ask from $ip: Missing Files")
            callback.call(null)
            return
        } else if (filesNode !is NSArray) {
            Log.w(TAG, "Invalid ask from $ip: Files is not a array")
            callback.call(null)
            return
        }
        val files = filesNode.array
        val fileTypes: MutableList<String> = ArrayList()
        val filePaths: MutableList<String> = ArrayList()
        for (file in files) {
            val fileNode = file as NSDictionary
            val fileTypeNode = fileNode["FileType"]
            val filePathNode = fileNode["FileBomPath"]
            if (fileTypeNode != null && filePathNode != null) {
                fileTypes.add(
                    AirDropTypes.getMimeType(
                        fileTypeNode.toJavaObject(
                            String::class.java
                        )
                    )
                )
                filePaths.add(filePathNode.toJavaObject(String::class.java))
            }
        }
        if (filePaths.isEmpty()) {
            Log.w(TAG, "Invalid ask from $ip: No file asked")
            callback.call(null)
            return
        }
        val id = idNode.toJavaObject(String::class.java)
        val name = nameNode.toJavaObject(String::class.java)
        var icon: Bitmap? = null
        val iconNode = request["FileIcon"]
        if (iconNode != null) {
            try {
                val data = iconNode.toJavaObject(ByteArray::class.java)
                icon = AirDropThumbnailUtil.decode(data)
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding file icon", e)
            }
        }
        val session: ReceivingSession =
            object : ReceivingSession(ip, id, name, fileTypes, filePaths, icon) {
                override fun accept() {
                    val response = NSDictionary()
                    response.put("ReceiverModelName", "Android")
                    response.put("ReceiverComputerName", mConfigManager.name)
                    callback.call(response)
                }

                override fun reject() {
                    callback.call(null)
                    mReceivingSessions.remove(ip)
                }

                override fun cancel() {
                    if (stream != null) {
                        try {
                            stream!!.close()
                        } catch (ignored: IOException) {
                        }
                        stream = null
                    }
                    mReceivingSessions.remove(ip)
                }
            }
        mReceivingSessions[ip] = session
        mReceiverListener?.onAirDropRequest(session)
    }

    fun handleAskCanceled(ip: String) {
        val session = mReceivingSessions.remove(ip)
        if (session != null) {
            mReceiverListener?.onAirDropRequestCanceled(session)
        }
    }

    fun handleUpload(ip: String, stream: InputStream?, callback: ResultCallback) {
        val session = mReceivingSessions[ip]
        if (session == null) {
            Log.w(TAG, "Upload from $ip not accepted")
            callback.call(null)
            return
        }
        session.stream = stream
        val fileFactory: FileFactory = object : FileFactory {
            private val fileCount = session.paths.size
            private var fileIndex = 0
            override fun onFile(name: String, size: Long, input: InputStream) {
                val streamReadListener: GossipyInputStream.Listener =
                    object : GossipyInputStream.Listener {
                        private var bytesReceived: Long = 0
                        override fun onRead(length: Int) {
                            bytesReceived += length.toLong()
                            mMainThreadHandler.post {
                                if (fileIndex < fileCount && mReceivingSessions.containsKey(ip)) {
                                    mReceiverListener?.onAirDropTransferProgress(
                                        session, name,
                                        bytesReceived, size, fileIndex, fileCount
                                    )
                                }
                            }
                        }
                    }
                mReceiverListener?.onAirDropTransfer(
                    session,
                    name,
                    GossipyInputStream(input, streamReadListener)
                )
                fileIndex++
            }
        }
        mArchiveExecutor.execute {
            try {
                AirDropArchiveUtil.unpack(stream, HashSet(session.paths), fileFactory)
                mMainThreadHandler.post {
                    mReceiverListener!!.onAirDropTransferDone(session)
                    mReceivingSessions.remove(ip)
                    callback.call(NSDictionary())
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed receiving files", e)
                mMainThreadHandler.post {
                    mReceiverListener!!.onAirDropTransferFailed(session)
                    mReceivingSessions.remove(ip)
                    callback.call(null)
                }
            }
        }
    }

    companion object {
        const val STATUS_OK = 0
        const val STATUS_NO_BLUETOOTH = 1
        const val STATUS_NO_WIFI = 2
        private const val TAG = "AirDropManager"
    }
}
