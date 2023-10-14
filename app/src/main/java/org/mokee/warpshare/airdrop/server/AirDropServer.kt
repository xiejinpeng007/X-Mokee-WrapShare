package org.mokee.warpshare.airdrop.server

import android.util.Log
import com.dd.plist.NSDictionary
import com.koushikdutta.async.AsyncNetworkSocket
import com.koushikdutta.async.AsyncSSLSocketWrapper
import com.koushikdutta.async.ByteBufferList
import com.koushikdutta.async.DataEmitter
import com.koushikdutta.async.callback.CompletedCallback
import com.koushikdutta.async.callback.DataCallback
import com.koushikdutta.async.http.body.AsyncHttpRequestBody
import com.koushikdutta.async.http.server.AsyncHttpServer
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import com.koushikdutta.async.http.server.HttpServerRequestCallback
import com.koushikdutta.async.http.server.UnknownRequestBody
import okio.Buffer
import okio.Pipe
import okio.buffer
import org.mokee.warpshare.airdrop.AirDropManager
import org.mokee.warpshare.certificate.CertificateManager
import org.mokee.warpshare.di.AppModule2
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress

class AirDropServer internal constructor(
    private val mCertificateManager: CertificateManager,
    private val mParent: AirDropManager
) {
    private var mServer: AsyncHttpServer? = null
    fun start(host: String): Int {
        mServer = AsyncHttpServer()
        mServer?.listenSecure(PORT, mCertificateManager.sSLContext)
        mServer?.post("/Discover", object : NSDictionaryHttpServerRequestCallback() {
            override fun onRequest(
                remote: InetAddress, request: NSDictionary,
                response: NSDictionaryHttpServerResponse
            ) {
                handleDiscover(remote, request, response)
            }
        })
        mServer?.post("/Ask", object : NSDictionaryHttpServerRequestCallback() {
            override fun onRequest(
                remote: InetAddress, request: NSDictionary,
                response: NSDictionaryHttpServerResponse
            ) {
                handleAsk(remote, request, response)
            }

            override fun onCanceled(remote: InetAddress) {
                handleAskCanceled(remote)
            }
        })
        mServer?.post("/Upload", object : InputStreamHttpServerRequestCallback() {
            override fun onRequest(
                remote: InetAddress, request: InputStream,
                response: NSDictionaryHttpServerResponse
            ) {
                handleUpload(remote, request, response)
            }
        })
        Log.d(TAG, "Server running at $host:$PORT")
        return PORT
    }

    fun stop() {
        mServer?.stop()
    }

    private fun handleDiscover(
        remote: InetAddress, request: NSDictionary?,
        response: NSDictionaryHttpServerResponse?
    ) {
        val address = remote.hostAddress ?: return

        mParent.handleDiscover(
            address,
            request,
            object : ResultCallback {
                override fun call(result: NSDictionary?) {
                    if (result != null) {
                        response?.send(result)
                    } else {
                        response?.send(401)
                    }
                }
            })
    }

    private fun handleAsk(
        remote: InetAddress, request: NSDictionary,
        response: NSDictionaryHttpServerResponse?
    ) {
        val address = remote.hostAddress ?: return
        mParent.handleAsk(address, request, object : ResultCallback {
            override fun call(result: NSDictionary?) {
                if (result != null) {
                    response?.send(result)
                } else {
                    response?.send(401)
                }
            }
        })
    }

    private fun handleAskCanceled(remote: InetAddress) {
        val address = remote.hostAddress ?: return
        mParent.handleAskCanceled(address)
    }

    private fun handleUpload(
        remote: InetAddress, request: InputStream,
        response: NSDictionaryHttpServerResponse
    ) {
        val address = remote.hostAddress ?: return
        mParent.handleUpload(address, request, object : ResultCallback {
            override fun call(result: NSDictionary?) {
                if (result != null) {
                    response.send(200)
                } else {
                    response.send(401)
                }
            }
        })
    }

    private abstract inner class InputStreamHttpServerRequestCallback : HttpServerRequestCallback {
        override fun onRequest(
            request: AsyncHttpServerRequest,
            response: AsyncHttpServerResponse
        ) {
            Log.d(TAG, "Request: " + request.method + " " + request.path)
            val socketWrapper = request.socket as AsyncSSLSocketWrapper
            val socket = socketWrapper.socket as AsyncNetworkSocket
            val address = socket.remoteAddress.address
            val body = request.getBody<AsyncHttpRequestBody<*>>() as UnknownRequestBody
            val emitter = body.emitter
            val pipe = Pipe(Long.MAX_VALUE)
            emitter.dataCallback = DataCallback { emitter1: DataEmitter?, bb: ByteBufferList ->
                try {
                    Buffer().use { buffer ->
                        buffer.write(bb.allByteArray)
                        bb.recycle()
                        pipe.sink.write(buffer, buffer.size)
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Failed receiving upload", e)
                    socketWrapper.close()
                }
            }
            request.endCallback = CompletedCallback { ex: Exception? ->
                try {
                    pipe.sink.flush()
                    pipe.sink.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Failed receiving upload", e)
                    response.code(500).end()
                }
            }
            onRequest(
                address,
                pipe.source.buffer().inputStream(),
                object : NSDictionaryHttpServerResponse {
                    override fun send(code: Int) {
                        response.code(code).end()
                    }

                    override fun send(nsDictionary: NSDictionary?) {
                        throw UnsupportedOperationException()
                    }
                })
        }

        protected abstract fun onRequest(
            remote: InetAddress, request: InputStream,
            response: NSDictionaryHttpServerResponse
        )
    }

    companion object {
        private const val TAG = "AirDropServer"
        private const val PORT = 8770
        const val MIME_OCTET_STREAM = "application/octet-stream"
    }
}