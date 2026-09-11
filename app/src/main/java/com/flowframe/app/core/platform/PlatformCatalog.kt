package com.flowframe.app.core.platform

import com.flowframe.app.core.model.Platform

data class PlatformInfo(
    val platform: Platform,
    val displayName: String,
    val homeUrl: String,
    val supportsGallery: Boolean,
    val supportDescription: String,
)

/** The UI and the network boundary share this finite platform catalog. */
object PlatformCatalog {
    val entries = listOf(
        PlatformInfo(Platform.DOUYIN, "抖音", "https://www.douyin.com/", true, "公开单视频与纯图文"),
        PlatformInfo(Platform.BILIBILI, "哔哩哔哩", "https://www.bilibili.com/", false, "公开单视频"),
        PlatformInfo(Platform.XIAOHONGSHU, "小红书", "https://www.xiaohongshu.com/", true, "试验性公开页面适配，实际下载尚待验证"),
        PlatformInfo(Platform.WEIBO, "微博", "https://weibo.com/", true, "公开单视频与纯图文"),
        PlatformInfo(Platform.KUAISHOU, "快手", "https://www.kuaishou.com/", true, "试验性公开分享页适配，实际下载尚待验证"),
    )

    fun get(platform: Platform): PlatformInfo = entries.first { it.platform == platform }

    fun pageHosts(platform: Platform): Set<String> = when (platform) {
        Platform.DOUYIN -> setOf("douyin.com", "www.douyin.com", "m.douyin.com", "v.douyin.com", "iesdouyin.com", "www.iesdouyin.com")
        Platform.BILIBILI -> setOf("bilibili.com", "www.bilibili.com", "m.bilibili.com", "b23.tv", "bili2233.cn", "www.bili2233.cn")
        Platform.XIAOHONGSHU -> setOf("xiaohongshu.com", "www.xiaohongshu.com", "xhslink.com", "www.xhslink.com")
        Platform.WEIBO -> setOf("weibo.com", "www.weibo.com", "m.weibo.cn", "video.weibo.com", "t.cn", "passport.weibo.com")
        Platform.KUAISHOU -> setOf("kuaishou.com", "www.kuaishou.com", "m.kuaishou.com", "v.kuaishou.com")
    }
}
