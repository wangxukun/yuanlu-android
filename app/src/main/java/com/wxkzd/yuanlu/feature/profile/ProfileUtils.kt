package com.wxkzd.yuanlu.feature.profile

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 个人中心纯逻辑（JVM 可测），口径逐条对齐 Web：
 * - components/main/profile 各组件（里程换算/里程碑/成就排序）
 * - components/auth/AccountSecurityTab.tsx（手机号/邮箱脱敏）
 * - app/(main)/auth/personal-center/page.tsx（等级映射/默认简介/加入日期）
 */
object ProfileUtils {

    /** 步行速度 5km/h → 1 小时收听 = 5km 里程（Web StatsOverview/MilestoneRoadmap 同值） */
    const val KM_PER_HOUR = 5

    /** Web 未设置简介时的默认座右铭 */
    const val DEFAULT_BIO = "路虽远行则将至，事虽难做则可成。"

    /** 昵称长度上限（超出即校验失败） */
    const val NICKNAME_MAX_LENGTH = 20

    /** 简介长度上限 */
    const val BIO_MAX_LENGTH = 100

    /** 远路里程碑（km → 名称），与 Web MILESTONES 一致 */
    val MILESTONES: List<Pair<Double, String>> = listOf(
        1.0 to "起步",
        5.0 to "小径",
        21.1 to "半马",
        42.2 to "全马",
        100.0 to "远路"
    )

    private val LEVEL_MAPPING: Map<String, String> = mapOf(
        "Beginner" to "初级",
        "Intermediate" to "中级",
        "Advanced" to "高级",
        "General" to "未分级"
    )

    /** 学习水平选项（表单值 → 展示文案），顺序对齐 Web 下拉框 */
    val LEARN_LEVELS: List<Pair<String, String>> = listOf(
        "General" to "未分级",
        "Beginner" to "初级",
        "Intermediate" to "中级",
        "Advanced" to "高级"
    )

    /** 累计收听小时 → 里程 */
    fun totalKm(totalHours: Double): Double = totalHours * KM_PER_HOUR

    /** 里程展示：固定一位小数（Web toFixed(1)） */
    fun formatKm(km: Double): String = String.format(Locale.US, "%.1f", km)

    /** 小时数展示：整数值省略小数点（JS `${12.0}h` 渲染为 "12h" 的口径） */
    fun formatHours(hours: Double): String =
        if (hours % 1.0 == 0.0) hours.toInt().toString() else hours.toString()

    /** 水平键 → 中文；null 视为中级（Web fetchProfile 的 `data.learnLevel || "中级"` 口径） */
    fun levelLabel(level: String?): String =
        LEVEL_MAPPING[level] ?: level?.takeIf { it.isNotBlank() } ?: "中级"

    /** 昵称为空时回退邮箱前缀，再回退 User（Web 头部标题同口径） */
    fun displayName(nickname: String?, email: String?): String =
        nickname?.takeIf { it.isNotBlank() } ?: email?.substringBefore("@")?.takeIf { it.isNotBlank() } ?: "User"

    /** 简介展示：空时回退默认座右铭（Web `data.bio || DEFAULT` 口径） */
    fun displayBio(bio: String?): String = bio?.takeIf { it.isNotBlank() } ?: DEFAULT_BIO

    /** 手机号脱敏：138****8000；位数不符时原样返回（Web replace 未命中即不变） */
    fun maskPhone(phone: String?): String =
        phone?.replace(Regex("^(\\d{3})\\d{4}(\\d{4})$"), "$1****$2") ?: ""

    /** 邮箱是否为注册占位邮箱（绑定邮箱前 password 一栏显示"未设置"的判定依据） */
    fun isPlaceholderEmail(email: String?): Boolean =
        email?.endsWith("@placeholder.yuanlu.com") == true

    /** 邮箱脱敏：a****b@example.com（中间最多打 4 个星，Web 同口径） */
    fun maskEmail(email: String?): String {
        if (email.isNullOrBlank()) return ""
        if (isPlaceholderEmail(email)) return ""
        val atIndex = email.indexOf('@')
        if (atIndex <= 0) return email
        val first = email.substring(0, 1)
        val middle = email.substring(1, atIndex)
        val domain = email.substring(atIndex)
        return first + "*".repeat(middle.length.coerceAtMost(4)) + domain
    }

    /** 昵称校验：null = 通过；返回用户可读错误文案 */
    fun validateNickname(nickname: String): String? = when {
        nickname.isBlank() -> "请输入昵称"
        nickname.length > NICKNAME_MAX_LENGTH -> "昵称不能超过 $NICKNAME_MAX_LENGTH 个字"
        else -> null
    }

    /** 简介校验：null = 通过 */
    fun validateBio(bio: String): String? =
        if (bio.length > BIO_MAX_LENGTH) "简介不能超过 $BIO_MAX_LENGTH 个字" else null

    /** 成就排序：已解锁排前、解锁与否之间保持稳定（Web sort 同为稳定比较） */
    fun sortAchievements(items: List<com.wxkzd.yuanlu.domain.model.AchievementItem>): List<com.wxkzd.yuanlu.domain.model.AchievementItem> =
        items.sortedBy { if (it.unlocked) 0 else 1 }

    /** 周活动图 Y 轴上限：最大值向上取整到 30 的倍数，最小 60（recharts 自适应的移动端等价口径） */
    fun chartYMax(minutes: List<Int>): Int {
        val max = (minutes.maxOrNull() ?: 0).coerceAtLeast(0)
        if (max <= 60) return 60
        return ((max + 29) / 30) * 30
    }

    /** 加入日期展示：ISO → yyyy/MM/dd（Web lib/tools formatDate）；解析失败回退"未知日期" */
    fun formatDate(iso: String?): String {
        if (iso.isNullOrBlank()) return "未知日期"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date: Date? = parser.parse(iso)
            if (date != null) {
                SimpleDateFormat("yyyy/MM/dd", Locale.US).format(date)
            } else {
                "未知日期"
            }
        } catch (_: Exception) {
            "未知日期"
        }
    }
}
