package com.example.measureme.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.measureme.logic.MeasurementEngine

@Composable
fun HomeScreen(measurementEngine: MeasurementEngine, onStartMeasure: () -> Unit) {
    val history = measurementEngine.savedHistory
    
    val categories = remember(history) {
        history.groupBy { 
            when {
                it.label.contains("Distance", true) -> "Distance"
                it.label.contains("Level", true) -> "Level"
                else -> "Compass"
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(72.dp))
            Text(
                text = "Welcome to\nMeasureMe",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1).sp
                ),
                color = Color.White,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }

        item {
            HeroCard(onStartMeasure)
            Spacer(modifier = Modifier.height(40.dp))
        }

        item {
            Text(
                text = "Projects",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        if (categories.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.02f)
                ) {
                    Text(
                        "No projects yet. Start measuring to see them here.",
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.3f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            val folderOrder = listOf("Distance", "Level", "Compass").filter { categories.containsKey(it) }
            items(folderOrder) { type ->
                HomeFolderCard(
                    name = type,
                    count = categories[type]?.size ?: 0,
                    icon = when (type) {
                        "Distance" -> Icons.Rounded.Architecture
                        "Level" -> Icons.Rounded.AlignHorizontalCenter
                        else -> Icons.Rounded.Navigation
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        
        item { Spacer(modifier = Modifier.height(120.dp)) }
    }
}

@Composable
fun HomeFolderCard(name: String, count: Int, icon: ImageVector) {
    Surface(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.05f)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(name, fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Text("$count items", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.2f))
        }
    }
}

@Composable
fun HeroCard(onStartMeasure: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(32.dp)),
        shape = RoundedCornerShape(32.dp),
        color = Color.White
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text(
                    "Ready to\nmeasure?",
                    color = Color.Black,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 36.sp,
                    letterSpacing = (-1).sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Try our most accurate AR tool.",
                    color = Color.Black.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onStartMeasure,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Start Now", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
