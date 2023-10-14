package org.mokee.warpshare.ui.main

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.mokee.warpshare.R
import org.mokee.warpshare.base.DiscoverListener
import org.mokee.warpshare.base.SendListener
import org.mokee.warpshare.di.AppModule
import org.mokee.warpshare.domain.data.Entity
import org.mokee.warpshare.domain.data.Peer
import org.mokee.warpshare.ui.receiver.TriggerReceiver

class MainViewModel(val app: Application) : AndroidViewModel(app), DiscoverListener {
    val appModule = AppModule

    var mShouldKeepDiscovering = false
//    private set

    private var peerList = listOf<Peer>()

    var mIsInSetup = false
    var mIsDiscovering = false
    var mPeerPicked: Peer? = null
    private val _peerListLiveData = MutableLiveData(listOf<Peer>())
    val peerListLiveData: LiveData<List<Peer>>
        get() = _peerListLiveData

    private val _peerUpdateFlow = MutableSharedFlow<Peer?>()

    val peerUpdateFlow: SharedFlow<Peer?> = _peerUpdateFlow.shareIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        replay = 1
    )

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
                peer.also {
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

    private fun handleSendConfirming(peer: Peer) {
        peer.also {
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
        peer.also {
            it.status.status = R.string.status_rejected
            updatePeer(it)
        }
        mShouldKeepDiscovering = false
    }

    fun handleSending(peer: Peer) {
        peer.also {
            it.status.status = R.string.status_sending
            updatePeer(it)
        }
    }

    fun handleSendSucceed(peer: Peer) {
        peer.also {
            it.status.status = R.string.toast_completed
            updatePeer(it)
        }
        mShouldKeepDiscovering = false
    }

    fun handleSendFailed(peer: Peer) {
        peer.also {
            it.status.status = 0
            updatePeer(it)
        }
        mShouldKeepDiscovering = false
    }


    fun findPeerById(id: String?): Peer? {
        return peerList.find { it.id == id }
    }

    fun ensurePickedPeer(): Peer? {
        return findPeerById(mPeerPicked?.id)
    }

    override fun onPeerFound(peer: Peer) {
        Log.d(TAG, "Found: " + peer.id + " (" + peer.name + ")")
        if (peerList.find { it.id == peer.id } == null) {
            peerList = peerList + peer
            _peerListLiveData.value = peerList
        }
    }

    override fun onPeerDisappeared(peer: Peer) {
        Log.d(TAG, "Disappeared:${Thread.currentThread().name} " + peer.id + " (" + peer.name + ")")
        peerList = peerList.filter { it.id != peer.id }
        _peerListLiveData.value = peerList
    }

    fun updatePeer(peer: Peer) {
        viewModelScope.launch {
            _peerUpdateFlow.emit(peer)
        }
    }

    fun onSuccessConfigAirDrop(context: Context) {
        mIsInSetup = false
        val intent = TriggerReceiver.getTriggerIntent(context)
        appModule.mAirDropManager.registerTrigger(intent)
    }

    companion object {
        const val TAG = "MainViewModel"
    }
}