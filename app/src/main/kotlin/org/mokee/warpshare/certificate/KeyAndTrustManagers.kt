package org.mokee.warpshare.certificate

import javax.net.ssl.KeyManager
import javax.net.ssl.TrustManager

class KeyAndTrustManagers(
    @JvmField val keyManagers: Array<KeyManager>,
    @JvmField val trustManagers: Array<TrustManager>
) 