package com.wxkzd.yuanlu.feature.favorites

import android.util.Log
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局收藏状态中心（对齐 Web 端跨页收藏一致性的客户端实现）：
 *
 * Web 端依赖服务端 revalidatePath 在导航时重取数据；Android 端 Nav3 的返回栈会保留
 * 各页 ViewModel，必须用一个单例状态源把「任何页面发生的收藏变更」广播出去：
 * - 详情页（播客/剧集）观察自己 key 的覆盖值，图标即时翻转；
 * - 「我的收藏」列表观察 revision，任何变更后回到列表自动重取服务端数据；
 * - 所有写请求（insert/delete）只从这里发出，乐观更新 + 失败回滚 + 统一 Toast。
 */
@Singleton
class FavoriteCenter @Inject constructor(
    private val repository: ContentRepository
) {
    enum class Target { PODCAST, EPISODE }

    data class Key(val target: Target, val id: String)

    private val centerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 本地已知的收藏态覆盖表：key -> 是否已收藏（服务端 seed + 本地变更，权威于页面缓存） */
    private val _states = MutableStateFlow<Map<Key, Boolean>>(emptyMap())
    val states: StateFlow<Map<Key, Boolean>> = _states.asStateFlow()

    /** 请求在途的 key 集合：详情页据此禁用收藏按钮（对齐 Web isLoadingFavorite） */
    private val _pendingKeys = MutableStateFlow<Set<Key>>(emptySet())
    val pendingKeys: StateFlow<Set<Key>> = _pendingKeys.asStateFlow()

    /** 每次成功/回滚的收藏变更自增；列表页监听它触发重取 */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** 一次性 Toast 消息（收藏成功/已取消收藏/操作失败） */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** 当前已知收藏态；未知（未 seed）返回 null，由调用方回退到服务端初值 */
    fun knownState(key: Key): Boolean? = _states.value[key]

    /** 服务端事实回填：仅当本地尚无该 key 的记录时生效（不打扰既有观察者） */
    fun seedIfAbsent(key: Key, favorited: Boolean) {
        _states.update { current -> if (current.containsKey(key)) current else current + (key to favorited) }
    }

    /** 登出时清空（切换账号/游客态下不能残留上一账号的收藏态） */
    fun clear() {
        _states.value = emptyMap()
        _pendingKeys.value = emptySet()
    }

    fun togglePodcast(id: String) = toggle(Key(Target.PODCAST, id), "播客")

    fun toggleEpisode(id: String) = toggle(Key(Target.EPISODE, id), "单集")

    /**
     * 列表页显式取消收藏：无论中心是否已 seed 该项，一律按「已收藏 → 移除」执行。
     * （toggle 依赖已知状态翻转，状态未知时会把删除误判成插入，这里消除该隐患）
     */
    fun removePodcast(id: String) = setFavorite(Key(Target.PODCAST, id), favorited = false, label = "播客")

    fun removeEpisode(id: String) = setFavorite(Key(Target.EPISODE, id), favorited = false, label = "单集")

    private fun toggle(key: Key, label: String) {
        val previous = _states.value[key] ?: false
        setFavorite(key, favorited = !previous, label = label)
    }

    private fun setFavorite(key: Key, favorited: Boolean, label: String) {
        if (key in _pendingKeys.value) return
        val previous = _states.value[key] ?: !favorited
        // 乐观翻转（对齐 Web setFavorite 后请求、失败回滚）
        _states.update { it + (key to favorited) }
        _pendingKeys.update { it + key }
        centerScope.launch {
            try {
                val result = if (key.target == Target.PODCAST) {
                    if (favorited) repository.addPodcastFavorite(key.id) else repository.removePodcastFavorite(key.id)
                } else {
                    if (favorited) repository.addEpisodeFavorite(key.id) else repository.removeEpisodeFavorite(key.id)
                }
                when (result) {
                    is Result.Success -> {
                        _revision.update { it + 1 }
                        _messages.emit(if (favorited) "收藏${label}成功" else "已取消收藏")
                    }
                    is Result.Error -> {
                        // 回滚并通知列表重取
                        _states.update { it + (key to previous) }
                        _revision.update { it + 1 }
                        Log.w(TAG, "favorite mutation failed: ${key.target} ${key.id} code=${result.code}")
                        _messages.emit(result.message.ifBlank { "操作失败，请重试" })
                    }
                    Result.NetworkError -> {
                        _states.update { it + (key to previous) }
                        _revision.update { it + 1 }
                        _messages.emit("网络错误，请重试")
                    }
                }
            } catch (t: Throwable) {
                // 兜底：协程异常（含 Error）也不允许把状态/在途标记留在中间态
                _states.update { it + (key to previous) }
                _revision.update { it + 1 }
                Log.e(TAG, "favorite mutation crashed", t)
                _messages.emit("操作失败，请重试")
            } finally {
                _pendingKeys.update { it - key }
            }
        }
    }

    private companion object {
        const val TAG = "FavoriteCenter"
    }
}
