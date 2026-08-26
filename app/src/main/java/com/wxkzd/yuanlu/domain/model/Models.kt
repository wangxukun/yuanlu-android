package com.wxkzd.yuanlu.domain.model

data class SubtitleWord(
    val text: String,
    val start: Double,
    val end: Double
)

data class Subtitle(
    val id: Int,
    val textEn: String,
    val textCn: String?,
    val startSeconds: Double,
    val endSeconds: Double,
    val speaker: String? = null,
    val words: List<SubtitleWord>? = null
)

data class Episode(
    val episodeid: String,
    val title: String,
    val coverUrl: String,
    val audioUrl: String,
    val duration: Int
)
