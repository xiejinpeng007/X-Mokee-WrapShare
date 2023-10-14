package org.mokee.warpshare.airdrop

import android.graphics.Bitmap
import java.io.InputStream
import java.util.Locale

abstract class ReceivingSession internal constructor(
    @JvmField val ip: String,
    val id: String,
    @JvmField val name: String,
    @JvmField val types: List<String>,
    @JvmField val paths: List<String>,
    @JvmField val preview: Bitmap?
) {
    private val targetFileNames: MutableMap<String, String> = HashMap()
    var stream: InputStream? = null

    init {
        for (path in paths) {
            targetFileNames[path] = assignFileName(path)
        }
    }

    abstract fun accept()
    abstract fun reject()
    abstract fun cancel()
    private fun assignFileName(fileName: String): String {
        var tempFileName = fileName
        val segments =
            fileName.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        tempFileName = segments[segments.size - 1]
        return String.format(
            Locale.US, "%s_%d_%s",
            id, System.currentTimeMillis(), tempFileName
        )
    }

    fun getFileName(path: String): String {
        return targetFileNames[path]!!
    }
}