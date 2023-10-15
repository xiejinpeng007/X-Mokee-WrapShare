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
package org.mokee.warpshare.domain.data

open class Peer(
    open val id: String,
    open val name: String,
    open val status: PeerState = PeerState(),
) {

    fun basicCopy(
        id: String = this.id,
        name: String = this.name,
        status: PeerState = this.status.copy(),
    ): Peer {
        return Peer(id, name, status)
    }

    override fun toString(): String {
        return "Peer(id='$id', name='$name', status=$status)"
    }

}

fun Peer.copyPeer(): Peer {
    return when (this) {
        is AirDropPeer -> {
            this.copy()
        }

        is NearSharePeer -> {
            this.copy()
        }

        else -> {
            this.basicCopy()
        }
    }
}

fun Peer.withCopy(action:(Peer) -> Unit) {
    action(this.copyPeer())
}