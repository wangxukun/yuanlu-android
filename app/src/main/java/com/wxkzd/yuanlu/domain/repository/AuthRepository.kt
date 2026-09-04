package com.wxkzd.yuanlu.domain.repository

import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.AchievementItem
import com.wxkzd.yuanlu.domain.model.ProfileStats
import com.wxkzd.yuanlu.domain.model.UserProfile
import com.wxkzd.yuanlu.domain.model.WeeklyActivityItem

/** 短信验证码发送结果：requireCaptcha=true 表示触发阿里云滑块风控 */
data class SmsSendStatus(
    val requireCaptcha: Boolean = false
)

interface AuthRepository {
    suspend fun loginWithPassword(email: String, password: String): Result<Unit>
    suspend fun loginWithSms(phone: String, code: String): Result<Unit>
    suspend fun logout()

    /** 发送登录短信验证码（60s 限频由后端控制） */
    suspend fun sendSmsCode(phone: String): Result<SmsSendStatus>

    /** 当前登录用户资料 */
    suspend fun getProfile(): Result<UserProfile>

    // ---------- 个人中心 ----------

    /**
     * 更新个人资料与学习目标（multipart，对齐 Web PUT api/user/profile）。
     * avatarJpeg 非空时作为头像文件上传；返回更新后的资料行。
     */
    suspend fun updateProfile(
        nickname: String,
        bio: String,
        learnLevel: String,
        dailyStudyGoalMins: Int,
        weeklyListeningGoalHours: Int,
        weeklyWordsGoal: Int,
        avatarJpeg: ByteArray?
    ): Result<UserProfile>

    /** 旅程概览统计 */
    suspend fun getStatsOverview(): Result<ProfileStats>

    /** 每日活动图表：weekOffset 0=本周 1=上周 */
    suspend fun getWeeklyActivity(weekOffset: Int): Result<List<WeeklyActivityItem>>

    /** 成就列表 */
    suspend fun getAchievements(): Result<List<AchievementItem>>
}
