package org.mokee.warpshare.ui.main

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.mokee.warpshare.R
import org.mokee.warpshare.TriggerReceiver
import org.mokee.warpshare.WarpShareApplication
import org.mokee.warpshare.base.DiscoverListener
import org.mokee.warpshare.base.Entity
import org.mokee.warpshare.base.Peer
import org.mokee.warpshare.base.SendListener
import org.mokee.warpshare.base.withCopy
import org.mokee.warpshare.di.AppModule

class MainViewModel(val app: Application) : AndroidViewModel(app), DiscoverListener {
    val appModule = AppModule(app as WarpShareApplication)

    var mShouldKeepDiscovering = false
//    private set

    private var peerList = listOf<Peer>()

    var mIsInSetup = false
    var mIsDiscovering = false
    var mPeerPicked: String? = null
    private val _peerListLiveData = MutableLiveData(listOf<Peer>())
    val peerListLiveData: LiveData<List<Peer>>
        get() = _peerListLiveData

    fun sendFile(peer: Peer, entities: List<Entity>) {
        handleSendConfirming(peer)
        val listener: SendListener = object : SendListener {
            override fun onAccepted() {
                handleSending(peer)
            }

            override fun onRejected() {
                handleSendRejected(peer)
            }

            override fun onProgress(bytesSent: Long, bytesTotal: Long) {
                peer.withCopy {
                    it.status.bytesSent = bytesSent
                    it.status.bytesTotal = bytesTotal
                    updatePeer(it)
                }
            }

            override fun onSent() {
                handleSendSucceed(peer)
            }

            override fun onSendFailed() {
                handleSendFailed(peer)
            }
        }
        peer.status.sending = appModule.send(peer, entities, listener)
    }

    fun handleSendConfirming(peer: Peer) {
        peer.withCopy {
            it.status.apply {
                status = R.string.status_waiting_for_confirm
                bytesTotal = -1
                bytesSent = 0
            }
            updatePeer(it)
        }
        mShouldKeepDiscovering = true
    }

    fun handleSendRejected(peer: Peer) {
        peer.withCopy {
            it.status.status = R.string.status_rejected
            updatePeer(it)
        }
        mShouldKeepDiscovering = false
    }

    fun handleSending(peer: Peer) {
        peer.withCopy {
            it.status.status = R.string.status_sending
            updatePeer(it)
        }
    }

    fun handleSendSucceed(peer: Peer) {
        peer.withCopy {
            it.status.status = 0
            updatePeer(it)
        }
        mShouldKeepDiscovering = false
    }

    fun handleSendFailed(peer: Peer) {
        peer.withCopy {
            it.status.status = 0
            updatePeer(it)
        }
        mShouldKeepDiscovering = false
    }


    fun findPeerById(id: String?): Peer? {
        return peerList.find { it.id == id }
    }

    fun getSelectedPeer(): Peer? {
        return findPeerById(mPeerPicked)
    }

    override fun onPeerFound(peer: Peer) {
        Log.d(TAG, "Found: " + peer.id + " (" + peer.name + ")")
        if (peerList.find { it.id == peer.id } == null) {
            peerList = peerList + peer
            _peerListLiveData.value = peerList
        }
    }

    override fun onPeerDisappeared(peer: Peer) {
        Log.d(TAG, "Disappeared: " + peer.id + " (" + peer.name + ")")
        peerList = peerList.filter { it.id != peer.id }
        _peerListLiveData.value = peerList
    }

    fun updatePeer(peer: Peer) {
        Log.d(TAG, "updatePeer: $peer")
        val oldPos = peerList.indexOfFirst { it.id == peer.id }
        if (oldPos == -1) return
        peerList = peerList.toMutableList().apply {
            set(oldPos, peer)
        }
        _peerListLiveData.value = peerList
    }

    fun onSuccessConfigAirDrop(context: Context) {
        mIsInSetup = false
        val intent = TriggerReceiver.getTriggerIntent(context)
        appModule.mAirDropManager.registerTrigger(intent)
    }


    override fun onCleared() {
        super.onCleared()
        appModule.destroy()
    }

    companion object {
        const val TAG = "MainViewModel"
    }
}