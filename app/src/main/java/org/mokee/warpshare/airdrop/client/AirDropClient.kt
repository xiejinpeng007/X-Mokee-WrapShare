package org.mokee.warpshare.airdrop.client

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dd.plist.NSDictionary
import com.dd.plist.PropertyListFormatException
import com.dd.plist.PropertyListParser
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.source
import org.mokee.warpshare.certificate.CertificateManager
import org.xml.sax.SAXException
import java.io.IOException
import java.io.InputStream
import java.lang.Exception
import java.net.NetworkInterface
import java.text.ParseException
import javax.net.ssl.X509TrustManager
import javax.xml.parsers.ParserConfigurationException

internal class AirDropClient(certificateManager: CertificateManager) {
    private val mHandler = Handler(Looper.getMainLooper())
    private val mHttpClient: OkHttpClient
    private var mInterface: NetworkInterface? = null

    init {
        mHttpClient = OkHttpClient.Builder()
            .socketFactory(LinkLocalAddressSocketFactory())
            .sslSocketFactory(
                certificateManager.sslContext.socketFactory,
                certificateManager.trustManagers[0] as X509TrustManager
            )
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    fun setNetworkInterface(networkInterface: NetworkInterface?) {
        mInterface = networkInterface
    }

    fun post(url: String, nSDictionary: NSDictionary, callback: AirDropClientCallback): Call? {
        val buffer = Buffer()
        try {
            PropertyListParser.saveAsBinary(nSDictionary, buffer.outputStream())
        } catch (e: IOException) {
            callback.onFailure(e)
            return null
        }
        val finalBody = buffer.readByteString()
            .toRequestBody("application/octet-stream".toMediaType())
        val call = post(url, finalBody, callback)
        buffer.close()
        return call
    }

    fun post(url: String, input: InputStream, callback: AirDropClientCallback): Call {
        return post(
            url, object : RequestBody() {
                override fun contentType(): MediaType {
                    return "application/x-cpio".toMediaType()
                }

                @Throws(IOException::class)
                override fun writeTo(sink: BufferedSink) {
                    sink.writeAll(input.source())
                    input.close()
                }
            },
            callback
        )
    }

    private fun post(url: String, body: RequestBody, callback: AirDropClientCallback): Call {
        val call = mHttpClient.newCall(
            Request.Builder()
                .url(url)
                .post(body)
                .build()
        )
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) {
                    Log.w(TAG, "Request canceled", e)
                } else {
                    Log.e(TAG, "Request failed: $url", e)
                    postFailure(callback, e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val statusCode = response.code
                if (statusCode != 200) {
                    postFailure(callback, IOException("Request failed: $statusCode"))
                    return
                }
                val responseBody = response.body
                if (responseBody == null) {
                    postFailure(callback, IOException("Response body null"))
                    return
                }
                try {
                    val root = PropertyListParser.parse(responseBody.byteStream()) as? NSDictionary
                    if (root != null) {
                        postResponse(callback, root)
                    }
                } catch (e: Exception) {
                    postFailure(callback, IOException(e))
                }
            }
        })
        return call
    }

    private fun postResponse(callback: AirDropClientCallback, response: NSDictionary) {
        mHandler.post { callback.onResponse(response) }
    }

    private fun postFailure(callback: AirDropClientCallback, e: IOException) {
        mHandler.post { callback.onFailure(e) }
    }

    companion object {
        private const val TAG = "AirDropClient"
    }
}