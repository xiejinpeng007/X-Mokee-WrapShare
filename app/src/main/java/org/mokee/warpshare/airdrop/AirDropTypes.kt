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

import android.text.TextUtils
import org.mokee.warpshare.base.Entity
import java.util.Locale

internal object AirDropTypes {
    fun getEntryType(entity: Entity): String {
        val mime = entity.type()
        if (!TextUtils.isEmpty(mime)) {
            if (mime.startsWith("image/")) {
                val name = entity.name().lowercase(Locale.getDefault())
                return if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                    "public.jpeg"
                } else if (name.endsWith(".jp2")) {
                    "public.jpeg-2000"
                } else if (name.endsWith(".gif")) {
                    "com.compuserve.gif"
                } else if (name.endsWith(".png")) {
                    "public.png"
                } else {
                    "public.image"
                }
            } else if (mime.startsWith("audio/")) {
                return "public.audio"
            } else if (mime.startsWith("video/")) {
                return "public.video"
            }
        }
        return "public.content"
    }

    fun getMimeType(entryType: String?): String {
        return when (entryType) {
            "public.jpeg" -> "image/jpeg"
            "public.jpeg-2000" -> "image/jp2"
            "com.compuserve.gif" -> "image/gif"
            "public.png" -> "image/png"
            "public.image" -> "image/*"
            "public/audio" -> "audio/*"
            "public/video" -> "video/*"
            else -> "*/*"
        }
    }
}
