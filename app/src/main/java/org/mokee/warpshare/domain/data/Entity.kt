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

import android.app.Application
import android.content.ContentResolver
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.io.File
import java.io.InputStream
import java.util.concurrent.ExecutionException

class Entity(val app: Application, inputUri: Uri?, inputType: String?) {
    val uri: Uri? = initUri(inputUri)
    private var cursor = initCursor(uri)
    val name = initName()
    val path = initPath()
    val type = initType(inputType)
    val size = initSize()
    val ok = uri != null && size > 0

    private fun initUri(uri:Uri?): Uri? {
        var tempUri = uri
        if(uri == null){
            return null
        }
        if (ContentResolver.SCHEME_FILE == uri.scheme) {
            tempUri = generateContentUri(uri)
        }
        return tempUri
    }

    private fun initCursor(uri:Uri?):Cursor?{
        if(uri == null) return null

        try {
            return app.contentResolver.query(uri, null, null, null, null)?.apply {
                moveToFirst()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed resolving uri: $uri", e)
        }
        return null
    }

    init{
        cursor?.close()
        cursor = null
    }

    private fun initName():String{
        return cursor?.let {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            it.getStringOrNull(nameIndex) ?: ""
        }?:""
    }

    private fun initPath():String{
        return if(name.isBlank()) ""
        else "./$name"
    }


    private fun initType(inputType:String?):String{
        return if(inputType.isNullOrBlank()){
            if(uri != null){
                app.contentResolver.getType(uri)?: ""
            }else{
                ""
            }
        }else{
            inputType
        }
    }

    private fun initSize():Long{
        return cursor?.let {
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            it.getLongOrNull(sizeIndex) ?: -1
        }?:-1
    }

    private fun generateContentUri(uri: Uri?): Uri? {
        val path = uri?.path
        if (path.isNullOrBlank()) {
            Log.e(TAG, "Empty uri path: $uri")
            return null
        }
        val file = File(path)
        if (!file.exists()) {
            Log.e(TAG, "File not exists: $uri")
            return null
        }
        return FileProvider.getUriForFile(app, "org.mokee.warpshare.files", file)
    }

    fun stream(): InputStream? {
        if (uri == null) return null
        return app.contentResolver.openInputStream(uri)
    }

    fun thumbnail(size: Int): Bitmap? {
        return try {
            Glide.with(app)
                .asBitmap()
                .load(uri)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .submit(size, size)
                .get()
        } catch (e: ExecutionException) {
            Log.e(TAG, "Failed generating thumbnail", e)
            null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Failed generating thumbnail", e)
            null
        }
    }

    companion object {
        private const val TAG = "Entity"
    }
}
