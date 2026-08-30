package com.wxkzd.yuanlu.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * api/comment 系列与 api/dictionary/youdao 的请求/响应建模。
 * 评论接口为裸数组/裸对象，字段名以后端 yuanlu 仓库路由源码为准。
 */

@Serializable
data class CommentProfileDto(
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val learnLevel: String? = null
)

@Serializable
data class CommentUserDto(
    val userid: String? = "",
    val email: String? = null,
    @SerialName("user_profile") val profile: CommentProfileDto? = null
)

@Serializable
data class CommentDto(
    val commentid: Int = 0,
    val userid: String = "",
    val episodeid: String = "",
    val commentText: String = "",
    val commentAt: String? = null,
    val parentId: Int? = null,
    val User: CommentUserDto? = null,
    val likesCount: Int = 0,
    val isLiked: Boolean = false
)

@Serializable
data class CreateCommentRequestDto(
    val episodeid: String,
    val content: String,
    val parentId: Int? = null
)

@Serializable
data class LikeCommentRequestDto(val commentId: Int)

@Serializable
data class LikeCommentResponseDto(val liked: Boolean = false)

@Serializable
data class TranslateRequestDto(val word: String)

/** /api/dictionary/youdao 响应：definition 为多段译文以 "; " 拼接 */
@Serializable
data class YoudaoResponseDto(
    val word: String? = null,
    val definition: String? = null,
    val speakUrl: String? = null,
    val webUrl: String? = null,
    val mobileUrl: String? = null
)
