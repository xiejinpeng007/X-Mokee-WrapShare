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

import okio.buffer
import okio.sink
import okio.source
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream
import org.apache.commons.compress.archivers.cpio.CpioConstants
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.mokee.warpshare.GossipyInputStream
import org.mokee.warpshare.base.Entity
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

internal object AirDropArchiveUtil {
    @Throws(IOException::class)
    fun pack(
        entities: List<Entity>, output: OutputStream?,
        streamReadListener: GossipyInputStream.Listener?
    ) {
        GzipCompressorOutputStream(output).use { gzip ->
            CpioArchiveOutputStream(gzip, CpioConstants.FORMAT_OLD_ASCII).use { cpio ->
                for (entity in entities) {
                    val entry = CpioArchiveEntry(CpioConstants.FORMAT_OLD_ASCII, entity.path())
                    entry.mode =
                        (CpioConstants.C_ISREG or CpioConstants.C_IRUSR or CpioConstants.C_IWUSR or CpioConstants.C_IRGRP or CpioConstants.C_IROTH).toLong()
                    entry.time = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
                    val stream: InputStream =
                        GossipyInputStream(entity.stream(), streamReadListener)
                    val source = stream.source().buffer()
                    val size = entity.size()
                    if (size == -1L) {
                        val content = source.readByteString()
                        entry.size = content.size.toLong()
                        cpio.putArchiveEntry(entry)
                        content.write(cpio)
                    } else {
                        entry.size = size
                        cpio.putArchiveEntry(entry)
                        source.readAll(cpio.sink())
                    }
                    cpio.closeArchiveEntry()
                }
            }
        }
    }

    @Throws(IOException::class)
    fun unpack(input: InputStream?, paths: Set<String?>, factory: FileFactory) {
        GzipCompressorInputStream(input).use { gzip ->
            CpioArchiveInputStream(gzip).use { cpio ->
                var entry: CpioArchiveEntry
                while (cpio.nextCPIOEntry.also { entry = it } != null) {
                    if (entry.isRegularFile && paths.contains(entry.name)) {
                        factory.onFile(entry.name, entry.size, cpio)
                    }
                }
            }
        }
    }

    interface FileFactory {
        fun onFile(name: String, size: Long, input: InputStream)
    }
}
