package org.mokee.warpshare.presentation.share

import android.app.Application
import android.content.ClipData
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import org.mokee.warpshare.domain.data.PeerState
import org.mokee.warpshare.domain.data.Entity


class ShareViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    val sendList: List<Entity>

    val peerState: PeerState = PeerState()

    init {
        val type = savedStateHandle.get<String>("type")
        sendList = savedStateHandle.get<ClipData>("clipData")?.run {
            (0 until this.itemCount).mapNotNull {
                val entity = Entity(application, this.getItemAt(it).uri, type)
                if (entity.ok) {
                    entity
                } else {
                    null
                }
            }
        } ?: emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("ShareViewModel", "onCleared: ")
    }
}