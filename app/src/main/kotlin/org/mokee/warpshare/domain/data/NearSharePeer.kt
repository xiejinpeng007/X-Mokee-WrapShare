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

import com.microsoft.connecteddevices.remotesystems.RemoteSystem

data class NearSharePeer(
    override val id: String,
    override val name: String,
    override val status: PeerState = PeerState(),
    val remoteSystem: RemoteSystem
) : Peer(id, name, status) {
    companion object {
        fun from(system: RemoteSystem): NearSharePeer {
            return NearSharePeer(
                id = system.id,
                name = system.displayName ?: "",
                remoteSystem = system
            )
        }
    }
}