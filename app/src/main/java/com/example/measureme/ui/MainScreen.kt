package com.example.measureme.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.measureme.data.supabase
import com.example.measureme.logic.MeasurementEngine
import com.example.measureme.ui.ARMeasurementScreen
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus

sealed class NavItem(val route: String, val icon: ImageVector, val label: String) {
    object Home : NavItem("home", Icons.Rounded.Home, "Home")
    object Tools : NavItem("tools", Icons.Rounded.Widgets, "Tools")
    object Projects : NavItem("projects", Icons.Rounded.Folder, "Projects")
    object Settings : NavItem("settings", Icons.Rounded.Settings, "Settings")
}

@Composable
fun MainScreen() {
    var currentTab by remember { mutableStateOf<NavItem>(NavItem.Home) }
    var activeTool by remember { mutableStateOf<AppMode?>(null) }
    val measurementEngine = remember { MeasurementEngine() }

    BackHandler(enabled = activeTool != null || currentTab != NavItem.Home) {
        if (activeTool != null) {
            activeTool = null
        } else if (currentTab != NavItem.Home) {
            currentTab = NavItem.Home
        }
    }

    LaunchedEffect(Unit) {
        supabase.auth.sessionStatus.collect {
            if (it is SessionStatus.Authenticated) {
                measurementEngine.loadFromSupabase()
            }
        }
    }

    Scaffold(
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Background Bar (Pill style like Distance Switcher)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp)),
                    shape = RoundedCornerShape(32.dp),
                    color = Color.Black.copy(alpha = 0.85f),
                    shadowElevation = 12.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NavItemButton(NavItem.Home, currentTab == NavItem.Home) { currentTab = NavItem.Home }
                        NavItemButton(NavItem.Tools, currentTab == NavItem.Tools) { currentTab = NavItem.Tools }
                        
                        Spacer(modifier = Modifier.width(72.dp))
                        
                        NavItemButton(NavItem.Projects, currentTab == NavItem.Projects) { currentTab = NavItem.Projects }
                        NavItemButton(NavItem.Settings, currentTab == NavItem.Settings) { currentTab = NavItem.Settings }
                    }
                }

                // Central Plus/Camera Floating Button
                Box(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { activeTool = AppMode.DISTANCE },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Open AR Camera",
                        modifier = Modifier.size(36.dp),
                        tint = Color.Black
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color.Black)) {
            when (currentTab) {
                NavItem.Home -> HomeScreen(measurementEngine = measurementEngine, onStartMeasure = { activeTool = AppMode.DISTANCE })
                NavItem.Tools -> ToolsScreen(onToolSelected = { activeTool = it })
                NavItem.Projects -> ProjectsScreen(measurementEngine)
                NavItem.Settings -> SettingsScreen(measurementEngine = measurementEngine)
            }
        }
    }

    AnimatedVisibility(
        visible = activeTool != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        activeTool?.let { mode ->
            Box(modifier = Modifier.fillMaxSize()) {
                ARMeasurementScreen(initialMode = mode)
                IconButton(
                    onClick = { activeTool = null },
                    modifier = Modifier.statusBarsPadding().padding(top = 16.dp, start = 16.dp).align(Alignment.TopStart),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }
        }
    }
}

@Composable
fun NavItemButton(item: NavItem, isSelected: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(if (isSelected) Color.White else Color.White.copy(alpha = 0.4f))
    val scale = if (isSelected) 1.2f else 1.0f
    
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier.size(24.dp * scale)
        )
    }
}
