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

    /** 发送邮箱注册验证码（对齐 Web /api/auth/send-verification-code，5 分钟有效） */
    suspend fun sendEmailVerificationCode(email: String): Result<Unit>

    /**
     * 邮箱注册（对齐 Web 注册对话框）：先 verify-code 校验邮箱验证码，
     * 通过后 sign-up 创建账号；邮箱已注册/验证码错误由后端文案透出。
     */
    suspend fun signUp(email: String, code: String, password: String): Result<Unit>

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

    /**
     * 上报增量收听秒数（POST api/auth/update-activity，需登录）。
     * 服务端累加 user_daily_activity.listeningSeconds 并驱动每日打卡判定；
     * 由 ListeningTimeReporter 在播放中按 30s 批量调用（对齐 Web GlobalAudio 心跳）。
     */
    suspend fun reportListeningSeconds(seconds: Int): Result<Unit>

    /** 成就列表 */
    suspend fun getAchievements(): Result<List<AchievementItem>>

    // ---------- 账号与安全（个人中心 Security tab） ----------

    /** 发送绑定手机验证码（scene=BIND，60s 限频由后端控制） */
    suspend fun sendBindPhoneCode(phone: String): Result<SmsSendStatus>

    /** 绑定手机号：scene=BIND 验证码校验与手机号碰撞检查在后端完成 */
    suspend fun bindPhone(phone: String, code: String): Result<Unit>

    /** 发送绑定邮箱验证码（对齐 Web /api/auth/bind-email/send，5 分钟有效） */
    suspend fun sendBindEmailCode(email: String): Result<Unit>

    /** 绑定邮箱并同时设置登录密码（对齐 Web /api/auth/bind-email/confirm） */
    suspend fun bindEmail(email: String, code: String, password: String): Result<Unit>

    /** 注销账号：服务端级联删除全部数据；成功后调用方需清理本地会话（logout） */
    suspend fun deleteAccount(): Result<Unit>
}
