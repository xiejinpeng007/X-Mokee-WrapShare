package org.mokee.warpshare.ui.share

import android.app.Application
import android.content.ClipData
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import org.mokee.warpshare.MainPeerState
import org.mokee.warpshare.base.Entity


class ShareViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    val sendList: List<Entity>

    val peerState: MainPeerState = MainPeerState()

    init {
        val type = savedStateHandle.get<String>("type")
        sendList = savedStateHandle.get<ClipData>("clipData")?.run {
            (0 until this.itemCount).map {
                val entity = Entity(application, this.getItemAt(it).uri, type)
                if (entity.ok()) {
                    entity
                } else {
                    null
                }
            }.filterNotNull()
        } ?: emptyList()
    }
}