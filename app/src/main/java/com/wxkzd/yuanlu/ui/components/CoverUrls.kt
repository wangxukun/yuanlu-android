package com.wxkzd.yuanlu.ui.components

/** OSS 未签名时的占位值，直接显示字母兜底封面 */
const val DEFAULT_COVER = "default_cover_url"

/**
 * 封面 URL 是否可加载：过滤 null / 空串 / 占位值 / 非 http 链接，
 * 以及私有 OSS 桶上未携带签名的直链（如 episode/detail 返回的裸 coverUrl，
 * 请求必 403——与其交给 Image 加载失败渲染空白，不如提前判无效走占位兜底）。
 */
fun isLoadableCoverUrl(url: String?): Boolean {
    val trimmed = url?.trim().orEmpty()
    if (trimmed.isEmpty() || trimmed == DEFAULT_COVER) return false
    if (!trimmed.startsWith("http")) return false
    val host = trimmed.substringAfter("//").substringBefore('/')
    val unsignedOss = host.endsWith(".aliyuncs.com") &&
        !trimmed.contains("signature=", ignoreCase = true)
    return !unsignedOss
}

/**
 * 详情页封面取值兜底链：episode.coverUrl || podcast.coverUrl || null（null 时 CoverImage 走字母占位）。
 * 两侧入参均先过 [isLoadableCoverUrl]，空串/未签名等无效值自动跳过。
 */
fun resolveEpisodeCoverUrl(episodeCoverUrl: String?, podcastCoverUrl: String?): String? =
    episodeCoverUrl?.takeIf { isLoadableCoverUrl(it) }
        ?: podcastCoverUrl?.takeIf { isLoadableCoverUrl(it) }
