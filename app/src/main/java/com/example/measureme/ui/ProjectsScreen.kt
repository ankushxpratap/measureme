package com.example.measureme.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.measureme.logic.MeasurementEngine
import com.example.measureme.logic.SavedMeasurement

@Composable
fun ProjectsScreen(measurementEngine: MeasurementEngine) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val history = measurementEngine.savedHistory.sortedByDescending { it.timestamp }
    
    val categories = remember(history) {
        history.groupBy { 
            when {
                it.label.contains("Distance", true) -> "Distance"
                it.label.contains("Level", true) -> "Level"
                else -> "Compass"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(modifier = Modifier.height(72.dp))
        
        AnimatedContent(
            targetState = selectedCategory,
            transitionSpec = {
                fadeIn().togetherWith(fadeOut())
            },
            label = "FolderTransition"
        ) { category ->
            if (category == null) {
                MainHistoryView(categories, history.size) { selectedCategory = it }
            } else {
                FolderDetailView(
                    category = category,
                    measurements = categories[category] ?: emptyList(),
                    onBack = { selectedCategory = null },
                    onDelete = { measurementEngine.deleteMeasurement(it) }
                )
            }
        }
    }
}

@Composable
fun MainHistoryView(categories: Map<String, List<SavedMeasurement>>, totalCount: Int, onCategoryClick: (String) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-1).sp
                    ),
                    color = Color.White
                )
                Text(
                    text = "$totalCount items stored in cloud",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
            Box(
                modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        if (categories.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No history yet.", color = Color.White.copy(alpha = 0.3f), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item {
                    Text(
                        text = "Directories",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                val folderOrder = listOf("Distance", "Level", "Compass")
                folderOrder.filter { categories.containsKey(it) }.forEach { type ->
                    item {
                        FolderCard(
                            name = type,
                            count = categories[type]?.size ?: 0,
                            icon = when (type) {
                                "Distance" -> Icons.Rounded.Architecture
                                "Level" -> Icons.Rounded.AlignHorizontalCenter
                                else -> Icons.Rounded.Navigation
                            },
                            onClick = { onCategoryClick(type) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FolderCard(name: String, count: Int, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.05f)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(56.dp).background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = Color.White)
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(name, fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text("$count items", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f))
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.2f))
        }
    }
}

@Composable
fun FolderDetailView(
    category: String,
    measurements: List<SavedMeasurement>,
    onBack: (() -> Unit),
    onDelete: ((SavedMeasurement) -> Unit)? = null
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = category,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1).sp
                ),
                color = Color.White
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            items(measurements) { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.05f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.4f))
                            Text(item.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color.White, letterSpacing = (-0.5).sp)
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                                    .format(java.util.Date(item.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.3f)
                            )
                            if (onDelete != null) {
                                IconButton(
                                    onClick = { onDelete(item) },
                                    modifier = Modifier.padding(top = 4.dp).size(32.dp)
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
