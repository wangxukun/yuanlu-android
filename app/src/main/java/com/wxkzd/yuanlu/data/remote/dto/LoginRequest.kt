package com.wxkzd.yuanlu.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val type: String, // "sms" or "password"
    val phone: String? = null,
    val code: String? = null,
    val email: String? = null,
    val password: String? = null
)

@Serializable
data class LoginResponseData(
    val token: String
)
