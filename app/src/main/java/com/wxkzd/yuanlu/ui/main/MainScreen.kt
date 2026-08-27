package com.wxkzd.yuanlu.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.wxkzd.yuanlu.feature.discover.DiscoverScreen
import com.wxkzd.yuanlu.feature.discover.DiscoverViewModel
import com.wxkzd.yuanlu.feature.home.HomeScreen
import com.wxkzd.yuanlu.feature.home.HomeViewModel
import com.wxkzd.yuanlu.feature.profile.ProfileScreen
import com.wxkzd.yuanlu.feature.profile.ProfileViewModel

private data class TabItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val TABS = listOf(
    TabItem("Home", Icons.Filled.Home),
    TabItem("Discover", Icons.Filled.Search),
    TabItem("Me", Icons.Filled.Person)
)

@Composable
fun MainScreen(
    onOpenPodcast: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenEpisode: (String) -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    viewModel = hiltViewModel<HomeViewModel>(),
                    onOpenPodcast = onOpenPodcast,
                    onOpenEpisode = onOpenEpisode
                )
                1 -> DiscoverScreen(
                    viewModel = hiltViewModel<DiscoverViewModel>(),
                    onOpenPodcast = onOpenPodcast,
                    onOpenChannel = onOpenChannel
                )
                2 -> ProfileScreen(viewModel = hiltViewModel<ProfileViewModel>())
            }
        }
    }
}
