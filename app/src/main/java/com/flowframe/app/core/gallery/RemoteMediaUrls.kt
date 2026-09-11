package com.flowframe.app.core.gallery

import java.net.InetAddress
import java.net.URI
import java.util.Locale

object RemoteMediaUrls {
    fun validate(rawUrl: String, resolve: (String) -> Array<InetAddress> = InetAddress::getAllByName): URI {
        val uri = runCatching { URI(rawUrl) }.getOrElse { throw IllegalArgumentException("媒体地址格式无效") }
        require(uri.scheme.equals("https", true) && !uri.isOpaque) { "媒体地址必须使用 HTTPS" }
        require(uri.rawUserInfo == null && uri.port in setOf(-1, 443)) { "媒体地址不能包含账号或非标准端口" }
        val host = uri.host?.trimEnd('.')?.lowercase(Locale.ROOT) ?: throw IllegalArgumentException("媒体地址缺少域名")
        require(host.contains('.') && !host.endsWith(".local") && !host.matches(Regex("[0-9.]+")) && ':' !in host) { "媒体地址域名无效" }
        val addresses = resolve(host)
        require(addresses.isNotEmpty() && addresses.all(::isPublicAddress)) { "媒体地址指向了不安全的网络位置" }
        return uri
    }

    private fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address.map { it.toInt() and 255 }
        if (bytes.size == 4) {
            return bytes[0] !in setOf(0, 10, 127) && bytes[0] < 224 &&
                !(bytes[0] == 100 && bytes[1] in 64..127) &&
                !(bytes[0] == 169 && bytes[1] == 254) &&
                !(bytes[0] == 172 && bytes[1] in 16..31) &&
                !(bytes[0] == 192 && bytes[1] in setOf(0, 168))
        }
        return bytes.size == 16 && bytes[0] and 0xfe != 0xfc && !(bytes[0] == 0xfe && bytes[1] and 0xc0 == 0x80)
    }
}
