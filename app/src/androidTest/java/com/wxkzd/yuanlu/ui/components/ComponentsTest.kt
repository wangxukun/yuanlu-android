package com.wxkzd.yuanlu.ui.components

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.Podcast
import com.wxkzd.yuanlu.theme.YuanluTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 纯 Composable 冒烟测试：列表行/卡片用假数据可正常渲染 */
@RunWith(AndroidJUnit4::class)
class ComponentsTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun episodeRow_showsTitleAndPodcast() {
        val episode = Episode(
            episodeid = "e1",
            title = "Morning News",
            podcastTitle = "The Daily",
            duration = 900
        )
        composeTestRule.setContent {
            YuanluTheme {
                EpisodeRow(episode = episode, onClick = {})
            }
        }
        composeTestRule.onNodeWithText("Morning News").assertExists()
        composeTestRule.onNodeWithText("The Daily · 15 min").assertExists()
    }

    @Test
    fun podcastCard_showsTitleAndEpisodeCount() {
        val podcast = Podcast(
            podcastid = "p1",
            title = "All Ears English",
            episodeCount = 12,
            platform = "Apple Podcasts"
        )
        composeTestRule.setContent {
            YuanluTheme {
                PodcastCard(podcast = podcast, onClick = {})
            }
        }
        composeTestRule.onNodeWithText("All Ears English").assertExists()
        composeTestRule.onNodeWithText("12 episodes · Apple Podcasts").assertExists()
    }
}
