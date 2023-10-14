package org.mokee.warpshare.airdrop.server

import android.util.Log
import com.dd.plist.NSDictionary
import com.dd.plist.PropertyListParser
import com.koushikdutta.async.AsyncNetworkSocket
import com.koushikdutta.async.AsyncSSLSocketWrapper
import com.koushikdutta.async.ByteBufferList
import com.koushikdutta.async.DataEmitter
import com.koushikdutta.async.callback.CompletedCallback
import com.koushikdutta.async.callback.DataCallback
import com.koushikdutta.async.http.body.AsyncHttpRequestBody
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import com.koushikdutta.async.http.server.HttpServerRequestCallback
import com.koushikdutta.async.http.server.UnknownRequestBody
import okio.Buffer
import java.net.InetAddress

internal abstract class NSDictionaryHttpServerRequestCallback : HttpServerRequestCallback {
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
        val buffer = Buffer()
        socket.closedCallback = CompletedCallback { onCanceled(address) }
        emitter.dataCallback =
            DataCallback { _: DataEmitter?, bb: ByteBufferList -> buffer.write(bb.allByteArray) }
        request.endCallback = CompletedCallback { ex: Exception? ->
            buffer.flush()
            if (ex != null) {
                Log.e(TAG, "Failed receiving request", ex)
                buffer.close()
                response.code(500).end()
                return@CompletedCallback
            }
            val req: NSDictionary
            req = try {
                PropertyListParser.parse(buffer.readByteArray()) as NSDictionary
            } catch (e: Exception) {
                Log.e(TAG, "Failed deserializing request", e)
                response.code(500).end()
                return@CompletedCallback
            } finally {
                buffer.close()
            }
            onRequest(address, req, object : NSDictionaryHttpServerResponse {
                override fun send(code: Int) {
                    response.code(code).end()
                }

                override fun send(res: NSDictionary?) {
                    try {
                        val buffer1 = Buffer()
                        PropertyListParser.saveAsBinary(res, buffer1.outputStream())
                        response.send(AirDropServer.MIME_OCTET_STREAM, buffer1.readByteArray())
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed serializing response", e)
                        response.code(500).end()
                    }
                }
            })
        }
    }

    abstract fun onRequest(
        remote: InetAddress, request: NSDictionary,
        response: NSDictionaryHttpServerResponse
    )

    protected open fun onCanceled(remote: InetAddress) {}

    companion object {
        const val TAG = "NSDictionaryHttpServerRequestCallback"
    }
}