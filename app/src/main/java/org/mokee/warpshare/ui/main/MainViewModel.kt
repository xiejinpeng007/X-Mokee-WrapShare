package org.mokee.warpshare.ui.main

import android.app.Application
import android.content.ClipData
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.mokee.warpshare.MainPeerState
import org.mokee.warpshare.R
import org.mokee.warpshare.WarpShareApplication
import org.mokee.warpshare.airdrop.AirDropPeer
import org.mokee.warpshare.base.DiscoverListener
import org.mokee.warpshare.base.Entity
import org.mokee.warpshare.base.Peer
import org.mokee.warpshare.base.SendListener
import org.mokee.warpshare.di.AppModule
import org.mokee.warpshare.nearbysharing.NearSharePeer

class MainViewModel(val app:Application) : AndroidViewModel(app), DiscoverListener {
    val appModule = AppModule(app as WarpShareApplication)

    var mShouldKeepDiscovering = false
//    private set

    private var peerList = listOf<Peer>()

    var mPeerPicked: String? = null
    private val _peerListLiveData = MutableLiveData(listOf<Peer>())
    val peerListLiveData: LiveData<List<Peer>>
        get() = _peerListLiveData

    private val _peerChangeFlow = MutableSharedFlow<Peer>(
        replay = 0,
        extraBufferCapacity = 10_000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val peerChangeFlow = _peerChangeFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        initialValue = _peerChangeFlow
    )

    fun sendFile(peer: Peer, uri: Uri, type: String) {
        val entity = Entity(app, uri, type)
        if (!entity.ok()) {
            Log.w(tag, "No file was selected")
            handleSendFailed(peer)
            return
        }
        val entities: MutableList<Entity> = ArrayList()
        entities.add(entity)
        sendFile(peer, entities)
    }

    public fun sendFile(peer: Peer, clipData: ClipData?, type: String) {
        if (clipData == null) {
            Log.w(tag, "ClipData should not be null")
            handleSendFailed(peer)
            return
        }
        val entities: MutableList<Entity> = ArrayList()
        for (i in 0 until clipData.itemCount) {
            val entity = Entity(app, clipData.getItemAt(i).uri, type)
            if (entity.ok()) {
                entities.add(entity)
            }
        }
        if (entities.isEmpty()) {
            Log.w(tag, "No file was selected")
            handleSendFailed(peer)
            return
        }
        sendFile(peer, entities)
    }

    private fun sendFile(peer: Peer, entities: List<Entity>) {
        handleSendConfirming(peer)
        val listener: SendListener = object : SendListener {
            override fun onAccepted() {
                handleSending(peer)
            }

            override fun onRejected() {
                handleSendRejected(peer)
            }

            override fun onProgress(bytesSent: Long, bytesTotal: Long) {
                peer.status.bytesSent = bytesSent
                peer.status.bytesTotal = bytesTotal
                _peerChangeFlow.tryEmit(peer)
            }

            override fun onSent() {
                handleSendSucceed(peer)
            }

            override fun onSendFailed() {
                handleSendFailed(peer)
            }
        }
        peer.status.sending = when (peer) {
            is AirDropPeer -> {
                appModule.mAirDropManager.send(peer, entities, listener)
            }

            is NearSharePeer -> {
                appModule.mNearShareManager.send(peer, entities, listener)
            }

            else -> {
                null
            }
        }
    }

    fun handleSendConfirming(peer: Peer) {
        peer.status.apply {
            status = R.string.status_waiting_for_confirm
            bytesTotal = -1
            bytesSent = 0
        }
        _peerChangeFlow.tryEmit(peer)
        mShouldKeepDiscovering = true
    }

    fun handleSendRejected(peer: Peer) {
        peer.status.status = R.string.status_rejected
        _peerChangeFlow.tryEmit(peer)
        mShouldKeepDiscovering = false
    }

    fun handleSending(peer: Peer) {
        peer.status.status = R.string.status_sending
        _peerChangeFlow.tryEmit(peer)
    }

    fun handleSendSucceed(peer: Peer) {
        peer.status.status = 0
        _peerChangeFlow.tryEmit(peer)
        mShouldKeepDiscovering = false
    }

    fun handleSendFailed(peer: Peer) {
        peer.status.status = 0
        _peerChangeFlow.tryEmit(peer)
        mShouldKeepDiscovering = false
    }


    fun findPeerById(id:String?): Peer? {
        return peerList.find { it.id == id }
    }

    override fun onPeerFound(peer: Peer) {
        Log.d(tag, "Found: " + peer.id + " (" + peer.name + ")")
        peerList = peerList + peer
        _peerListLiveData.value = peerList
    }

    override fun onPeerDisappeared(peer: Peer) {
        Log.d(tag, "Disappeared: " + peer.id + " (" + peer.name + ")")
        peerList = peerList.filter { it.id != peer.id }
        _peerListLiveData.value = peerList
    }


    override fun onCleared() {
        super.onCleared()
        appModule.destroy()
    }

    companion object {
        const val tag = "MainViewModel"
    }
}