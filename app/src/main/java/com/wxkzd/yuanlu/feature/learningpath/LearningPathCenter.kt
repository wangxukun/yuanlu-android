package com.wxkzd.yuanlu.feature.learningpath

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 学习路径跨页刷新中心（借鉴 FavoriteCenter 的跨页同步思路，这里只做事实广播）：
 * 详情页「编辑/删除/添加剧集/移除剧集」成功后 bump revision，
 * 列表页 collect revision 变化即静默刷新——保证从详情返回列表时数据不陈旧。
 * 列表页自身的「创建路径」也走同一通道，避免双份刷新逻辑。
 */
@Singleton
class LearningPathCenter @Inject constructor() {

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** 任一路径数据变更后调用（创建/编辑/删除/添加剧集/移除剧集成功） */
    fun notifyChanged() {
        _revision.update { it + 1 }
    }
}
