package com.ayzen.scan.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayzen.scan.MainViewModel
import com.ayzen.scan.R
import com.ayzen.scan.ui.components.AppBackground
import com.ayzen.scan.ui.create.CreateScreen
import com.ayzen.scan.ui.history.HistoryScreen
import com.ayzen.scan.ui.scan.ScanScreen
import com.ayzen.scan.ui.settings.SettingsScreen
import com.ayzen.scan.ui.theme.AyzenTheme

private data class NavItem(val icon: ImageVector, @StringRes val label: Int)

private val navItems = listOf(
    NavItem(Icons.Rounded.QrCodeScanner, R.string.nav_scan),
    NavItem(Icons.Rounded.QrCode2, R.string.nav_create),
    NavItem(Icons.Rounded.History, R.string.nav_history),
    NavItem(Icons.Rounded.Settings, R.string.nav_settings)
)

@Composable
fun AppRoot(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    AyzenTheme(settings.themeMode) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        BackHandler(enabled = tab != 0) { tab = 0 }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = { AppBottomBar(selected = tab, onSelect = { tab = it }) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> ScanScreen(vm)
                    1 -> AppBackground { CreateScreen(vm) }
                    2 -> AppBackground { HistoryScreen(vm) }
                    else -> AppBackground { SettingsScreen(vm) }
                }
            }
        }
    }
}

@Composable
private fun AppBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 0.dp
    ) {
        navItems.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(stringResource(item.label), fontSize = 11.sp, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = MaterialTheme.colorScheme.secondary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
