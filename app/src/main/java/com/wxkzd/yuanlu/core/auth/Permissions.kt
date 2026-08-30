package com.wxkzd.yuanlu.core.auth

/**
 * yuanlu 权限系统的客户端复刻，单一事实来源。
 * 对齐 yuanlu 仓库 core/auth/guard.ts 与 lib/client/auth-utils.ts 的判断口径。
 *
 * ── 用户类（UserRole，guard.ts VALID_ROLES）──
 * USER     普通登录用户
 * PREMIUM  高级会员
 * ADMIN    管理员（隐含会员权限）
 *
 * ── 服务端口径（guard.ts；Android 走同一批 API，自动继承其强制力）──
 * requireAuth      : 写操作需登录（评论发布/点赞、收藏、有道翻译、音频/文稿下载），401 "请先登录"
 * requireAdmin     : ADMIN 专属（后台管理类接口），403 "权限不足，需要管理员权限"
 * requirePremium   : PREMIUM/ADMIN 或有效订阅，403 "权限不足，需要高级会员权限"（音频下载、文稿 PDF）
 * canAccessEpisode : 非专享剧集人人可访问；专享剧集需会员资格，否则 detail 剥离音频/字幕、audio-proxy 403
 * 字幕接口         : 游客仅 3 分钟预览且不签发音频直链；登录后完整签发
 * 有道翻译         : 登录用户免费额度每日 N 次（403 code=QUOTA_EXCEEDED），会员不限
 * 评论             : isCommentAllowed=false 的账号禁言（403）
 * 移动端 Bearer JWT : 每次请求服务端实时回查 DB 的 role 与 isLoginAllowed
 *
 * ── 客户端口径（本对象实现）──
 * 与 Web 前端一致：仅依据会话（JWT）中的静态 role 判断 PREMIUM/ADMIN，
 * 不做订阅表回查；订阅导致的动态提权由服务端 isPremiumUser 兜底，
 * 因此客户端的锁样式最多保守显示，不会放行无权操作。
 */
object Permissions {
    const val ROLE_USER = "USER"
    const val ROLE_PREMIUM = "PREMIUM"
    const val ROLE_ADMIN = "ADMIN"

    /** 会员资格（客户端口径）：PREMIUM 或 ADMIN */
    fun isMember(role: String?): Boolean =
        role == ROLE_PREMIUM || role == ROLE_ADMIN

    fun isAdmin(role: String?): Boolean = role == ROLE_ADMIN

    /** canAccessEpisode 客户端口径：非专享人人可看；专享需登录且为会员 */
    fun canAccessEpisode(isExclusive: Boolean, loggedIn: Boolean, role: String?): Boolean =
        !isExclusive || (loggedIn && isMember(role))

    /**
     * 锁图标专用判定：会员专属剧集 + 已登录的非会员。
     * 游客与会员一律显示播放/麦克风等正向图标，点击时再引导登录或升级。
     */
    fun isExclusiveLockedForUser(isExclusive: Boolean, loggedIn: Boolean, role: String?): Boolean =
        isExclusive && loggedIn && !isMember(role)
}
