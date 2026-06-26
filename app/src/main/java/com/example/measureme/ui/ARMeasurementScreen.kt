package com.example.measureme.ui

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.*
import android.provider.MediaStore
import android.view.PixelCopy
import android.widget.Toast
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.measureme.data.supabase
import com.example.measureme.logic.CompassSensorManager
import com.example.measureme.logic.LevelMode
import com.example.measureme.logic.LevelSensorManager
import com.example.measureme.logic.MeasurementEngine
import com.example.measureme.logic.SavedMeasurement
import com.google.ar.core.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.LineNode
import io.github.sceneview.node.SphereNode
import io.github.sceneview.node.TextNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

enum class AppMode {
    DISTANCE, LEVEL, COMPASS
}

enum class HapticType {
    LIGHT, MEDIUM, STRONG
}

object AppHaptics {
    fun perform(context: Context, type: HapticType) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return

            when (type) {
                HapticType.LIGHT -> vibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
                HapticType.MEDIUM -> vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                HapticType.STRONG -> vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 40, 60), -1))
            }
        } catch (e: Exception) { }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ARMeasurementScreen(initialMode: AppMode = AppMode.DISTANCE) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val measurementEngine = remember { MeasurementEngine() }
    
    var currentMode by remember { mutableStateOf(initialMode) }
    val anchors = remember { mutableStateListOf<Anchor>() }
    val poseBuffer = remember { mutableListOf<Pose>() }
    var smoothedPose by remember { mutableStateOf<Pose?>(null) }
    
    var currentDistance by remember { mutableFloatStateOf(0f) }
    var arSession by remember { mutableStateOf<Session?>(null) }
    var trackingState by remember { mutableStateOf(TrackingState.STOPPED) }
    var isPlaneDetected by remember { mutableStateOf(false) }
    var centerHitResult by remember { mutableStateOf<HitResult?>(null) }
    
    var showHistory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showUnitSelector by remember { mutableStateOf(false) }
    var selectedUnit by remember { mutableStateOf(MeasurementEngine.UnitType.CM) }

    var isCapturing by remember { mutableStateOf(false) }
    var isMeasurementFinished by remember { mutableStateOf(false) }
    var finalResult by remember { mutableStateOf("") }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // Level & Compass State
    var roll by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }
    var azimuth by remember { mutableFloatStateOf(0f) }
    var compassAccuracy by remember { mutableStateOf("Unknown") }
    var levelMode by remember { mutableStateOf(LevelMode.FLOOR) }

    val levelManager = remember { LevelSensorManager(context) }
    val compassManager = remember { CompassSensorManager(context) }

    val isHitting by remember { derivedStateOf { centerHitResult != null } }
    val canAdd by remember { derivedStateOf { (isHitting || smoothedPose != null) && trackingState == TrackingState.TRACKING } }

    BackHandler(enabled = showHistory || showSettings || showUnitSelector) {
        showHistory = false
        showSettings = false
        showUnitSelector = false
    }

    LaunchedEffect(Unit) {
        try {
            supabase.auth.sessionStatus.collect {
                if (it is SessionStatus.Authenticated) {
                    measurementEngine.loadFromSupabase()
                }
            }
        } catch (e: Exception) {
            Log.e("ARMeasurement", "Error collecting session status", e)
        }
    }

    LaunchedEffect(levelMode) {
        levelManager.currentMode = levelMode
    }

    DisposableEffect(currentMode) {
        if (currentMode == AppMode.LEVEL) {
            levelManager.currentMode = levelMode
            levelManager.onLevelChanged = { r, p, _ -> roll = r; pitch = p }
            levelManager.start()
        } else if (currentMode == AppMode.COMPASS) {
            compassManager.onAzimuthChanged = { a -> azimuth = a }
            compassManager.onAccuracyChanged = { acc -> compassAccuracy = acc }
            compassManager.start()
        }
        onDispose {
            levelManager.stop()
            compassManager.stop()
        }
    }

    LaunchedEffect(isMeasurementFinished) {
        if (isMeasurementFinished) {
            AppHaptics.perform(context, HapticType.STRONG)
        }
    }

    LaunchedEffect(selectedUnit) {
        if (isMeasurementFinished && currentDistance > 0) {
            finalResult = measurementEngine.formatDistance(currentDistance, selectedUnit)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = with(density) { maxWidth.toPx() }
        val screenHeight = with(density) { maxHeight.toPx() }

        if (currentMode == AppMode.DISTANCE) {
            val rememberedSessionConfiguration: (Session, Config) -> Unit = remember {
                { session, config ->
                    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    config.focusMode = Config.FocusMode.AUTO
                    
                    // Explicitly disable depth to prevent internal Camera2 thermal null pointer crashes 
                    // on some devices (as seen in logs)
                    config.depthMode = Config.DepthMode.DISABLED
                    
                    config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
                }
            }

            ARSceneView(
                onSessionUpdated = { session, frame ->
                    arSession = session
                    trackingState = frame.camera.trackingState
                    
                    val planes = session.getAllTrackables(Plane::class.java)
                    isPlaneDetected = planes.any { it.trackingState == TrackingState.TRACKING }

                    if (isMeasurementFinished || currentMode != AppMode.DISTANCE) return@ARSceneView
                    
                    if (frame.camera.trackingState != TrackingState.TRACKING) {
                        centerHitResult = null
                        smoothedPose = null
                        return@ARSceneView
                    }

                    val hitResults = frame.hitTest(screenWidth / 2, screenHeight / 2)
                    
                    val bestHit = hitResults.find { it.trackable is Plane && (it.trackable as Plane).isPoseInPolygon(it.hitPose) }
                        ?: hitResults.find { it.trackable is Plane }
                        ?: hitResults.find { it.trackable is Point }
                        ?: hitResults.firstOrNull()

                    centerHitResult = bestHit

                    bestHit?.let { hit ->
                        val pose = hit.hitPose
                        if (!pose.tx().isNaN() && !pose.ty().isNaN() && !pose.tz().isNaN()) {
                            poseBuffer.add(pose)
                            if (poseBuffer.size > 15) poseBuffer.removeAt(0)

                            val buffer = poseBuffer.toList()
                            val count = buffer.size
                            if (count > 0) {
                                var sumX = 0.0; var sumY = 0.0; var sumZ = 0.0
                                for (p in buffer) {
                                    sumX += p.tx(); sumY += p.ty(); sumZ += p.tz()
                                }

                                smoothedPose = Pose(
                                    floatArrayOf((sumX / count).toFloat(), (sumY / count).toFloat(), (sumZ / count).toFloat()),
                                    pose.rotationQuaternion
                                )
                            }
                        }
                    } ?: run { smoothedPose = null }

                    if (anchors.size == 1 && anchors[0].trackingState == TrackingState.TRACKING) {
                        val currentPose = smoothedPose ?: centerHitResult?.hitPose
                        currentPose?.let { pose ->
                            if (!pose.tx().isNaN()) {
                                val newDist = measurementEngine.calculateDistance(anchors[0].pose, pose)
                                if (abs(newDist - currentDistance) > 0.002f) { // Throttling updates to 2mm
                                    currentDistance = newDist
                                }
                            }
                        }
                    }
                },
                sessionConfiguration = rememberedSessionConfiguration,
                content = {
                    val sphereMaterial = remember(materialLoader) { materialLoader.createUnlitColorInstance(Color.White.toArgb()) }
                    val lineMaterial = remember(materialLoader) { materialLoader.createUnlitColorInstance(Color.White.toArgb()) }
                    val previewLineMaterial = remember(materialLoader) { materialLoader.createUnlitColorInstance(Color.White.copy(alpha = 0.6f).toArgb()) }

                    anchors.forEach { anchor ->
                        if (anchor.trackingState == TrackingState.TRACKING) {
                            val p = anchor.pose
                            if (!p.tx().isNaN()) {
                                key(anchor) {
                                    AnchorNode(anchor = anchor) { SphereNode(radius = 0.010f, materialInstance = sphereMaterial) }
                                }
                            }
                        }
                    }

                    for (i in 0 until anchors.size - 1) {
                        val startAnchor = anchors[i]
                        val endAnchor = anchors[i + 1]
                        
                        if (startAnchor.trackingState == TrackingState.TRACKING && 
                            endAnchor.trackingState == TrackingState.TRACKING) {
                            
                            val startPose = startAnchor.pose
                            val endPose = endAnchor.pose
                            
                            if (!startPose.tx().isNaN() && !endPose.tx().isNaN()) {
                                val distance = measurementEngine.calculateDistance(startPose, endPose)
                                
                                if (distance > 0.005f) {
                                    key(startAnchor, endAnchor) {
                                        LineNode(
                                            start = Position(startPose.tx(), startPose.ty(), startPose.tz()),
                                            end = Position(endPose.tx(), endPose.ty(), endPose.tz()),
                                            materialInstance = lineMaterial
                                        )
                                        
                                        TextNode(
                                            text = measurementEngine.formatDistance(distance, selectedUnit),
                                            position = Position(
                                                (startPose.tx() + endPose.tx()) / 2,
                                                (startPose.ty() + endPose.ty()) / 2 + 0.05f,
                                                (startPose.tz() + endPose.tz()) / 2
                                            ),
                                            fontSize = 32f,
                                            backgroundColor = android.graphics.Color.argb(160, 0, 0, 0),
                                            textColor = android.graphics.Color.WHITE,
                                            widthMeters = 0.12f,
                                            heightMeters = 0.04f
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!isMeasurementFinished && anchors.size == 1) {
                        val startAnchor = anchors[0]
                        if (startAnchor.trackingState == TrackingState.TRACKING) {
                            val bestPose = smoothedPose ?: centerHitResult?.hitPose
                            bestPose?.let { pose ->
                                if (!pose.tx().isNaN() && !startAnchor.pose.tx().isNaN()) {
                                    val startPose = startAnchor.pose
                                    LineNode(
                                        start = Position(startPose.tx(), startPose.ty(), startPose.tz()),
                                        end = Position(pose.tx(), pose.ty(), pose.tz()),
                                        materialInstance = previewLineMaterial
                                    )
                                }
                            }
                        }
                    }
                }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }

        // Overlay Components
        Box(modifier = Modifier.fillMaxSize()) {
            // Flash Effect
            AnimatedVisibility(visible = isCapturing, enter = fadeIn(tween(50)), exit = fadeOut(tween(200)), label = "Flash") {
                Box(modifier = Modifier.fillMaxSize().background(Color.White))
            }

            // Top Bar
            if (!isMeasurementFinished) {
                TopBar(
                    currentMode = currentMode,
                    onHistoryClick = { AppHaptics.perform(context, HapticType.LIGHT); showHistory = true },
                    onSettingsClick = { AppHaptics.perform(context, HapticType.LIGHT); showSettings = true },
                    onClearClick = {
                        AppHaptics.perform(context, HapticType.MEDIUM)
                        measurementEngine.clearMeasurements()
                        val toDetach = anchors.toList()
                        anchors.clear()
                        toDetach.forEach { try { it.detach() } catch (e: Exception) {} }
                        currentDistance = 0f
                        isMeasurementFinished = false
                    },
                    onSaveClick = {
                        AppHaptics.perform(context, HapticType.MEDIUM)
                        val label = when (currentMode) {
                            AppMode.DISTANCE -> "Distance"
                            AppMode.LEVEL -> "Level ($levelMode)"
                            AppMode.COMPASS -> "Compass"
                        }
                        val value = when (currentMode) {
                            AppMode.DISTANCE -> measurementEngine.formatDistance(currentDistance, selectedUnit)
                            AppMode.LEVEL -> "${String.format(Locale.US, "%.1f", abs(roll))}°"
                            AppMode.COMPASS -> "${String.format(Locale.US, "%.1f", azimuth)}°"
                        }
                        if (value.isNotEmpty() && !value.startsWith("0.0")) {
                            measurementEngine.saveMeasurement(label, value)
                            scope.launch { snackbarHostState.showSnackbar("Measurement saved") }
                        }
                    },
                    onUnitClick = { showUnitSelector = !showUnitSelector },
                    modifier = Modifier.statusBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }

            if (showUnitSelector) {
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 80.dp, end = 24.dp)) {
                    UnitSelectorOverlay(
                        selectedUnit = selectedUnit,
                        onUnitSelected = {
                            selectedUnit = it
                            showUnitSelector = false
                            AppHaptics.perform(context, HapticType.LIGHT)
                        }
                    )
                }
            }

            // Central Tool UI
            Box(modifier = Modifier.align(Alignment.Center)) {
                when (currentMode) {
                    AppMode.DISTANCE -> {
                        if (!isMeasurementFinished) {
                            val reticleScale by animateFloatAsState(targetValue = if (isHitting) 1.2f else 1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "reticleScale")
                            Reticle(modifier = Modifier.scale(reticleScale), isHitting = isHitting)
                        } else {
                            ResultCard(value = finalResult, onClose = { 
                                AppHaptics.perform(context, HapticType.LIGHT)
                                isMeasurementFinished = false
                                // Safe cleanup: Detach and clear
                                val currentAnchors = anchors.toList()
                                anchors.clear()
                                currentAnchors.forEach { try { it.detach() } catch(e: Exception) {} }
                                currentDistance = 0f
                                poseBuffer.clear()
                                smoothedPose = null
                            })
                        }
                    }
                    AppMode.LEVEL -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AnimatedContent(
                                targetState = levelMode,
                                transitionSpec = {
                                    (fadeIn(tween(400)) + scaleIn(tween(400))).togetherWith(
                                        fadeOut(tween(400)) + scaleOut(tween(400))
                                    )
                                },
                                label = "LevelModeTransition"
                            ) { mode ->
                                ModernLevelUI(roll = roll, pitch = pitch, mode = mode)
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            LevelDetails(roll = roll, pitch = pitch, mode = levelMode)

                            Spacer(modifier = Modifier.height(32.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LevelModeToggle(selectedMode = levelMode, onModeSelected = { 
                                    levelMode = it 
                                    AppHaptics.perform(context, HapticType.LIGHT)
                                })
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                IconButton(
                                    onClick = { 
                                        levelManager.calibrate()
                                        AppHaptics.perform(context, HapticType.MEDIUM)
                                    },
                                    modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)
                                ) {
                                    Icon(Icons.Rounded.FilterCenterFocus, contentDescription = "Calibrate", tint = Color.White)
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                IconButton(
                                    onClick = { 
                                        levelManager.resetCalibration()
                                        AppHaptics.perform(context, HapticType.LIGHT)
                                    },
                                    modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = "Reset", tint = Color.White)
                                }
                            }
                        }
                    }
                    AppMode.COMPASS -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ModernCompassUI(azimuth = azimuth)
                            Spacer(modifier = Modifier.height(40.dp))
                            CompassDetails(azimuth = azimuth, accuracy = compassAccuracy)
                        }
                    }
                }
            }

            // Guidance Overlay
            if (currentMode == AppMode.DISTANCE && !isPlaneDetected && !isMeasurementFinished) {
                GuidanceOverlay(modifier = Modifier.fillMaxSize())
            }

            // HUD Display
            HUDDisplay(
                currentMode = currentMode,
                currentDistance = currentDistance,
                isMeasurementFinished = isMeasurementFinished,
                selectedUnit = selectedUnit,
                measurementEngine = measurementEngine,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp)
            )

            // Bottom Controls
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (currentMode == AppMode.DISTANCE) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ModernIconButton(
                            icon = Icons.Rounded.Delete,
                            onClick = {
                                AppHaptics.perform(context, HapticType.MEDIUM)
                                measurementEngine.clearMeasurements()
                                val toDetach = anchors.toList()
                                anchors.clear()
                                toDetach.forEach { try { it.detach() } catch (e: Exception) {} }
                                currentDistance = 0f
                                isMeasurementFinished = false
                            }
                        )

                        if (!isMeasurementFinished) {
                            MainActionButton(
                                canAdd = canAdd,
                                isCheck = anchors.size == 1,
                                onClick = {
                                    val session = arSession ?: return@MainActionButton
                                    if (trackingState != TrackingState.TRACKING) return@MainActionButton

                                    try {
                                        val poseToUse = smoothedPose ?: centerHitResult?.hitPose ?: return@MainActionButton
                                        
                                        if (anchors.size >= 2) {
                                            val toDetach = anchors.toList()
                                            anchors.clear()
                                            toDetach.forEach { try { it.detach() } catch (e: Exception) {} }
                                        }

                                        val anchor = session.createAnchor(poseToUse) ?: return@MainActionButton
                                        anchors.add(anchor)
                                        AppHaptics.perform(context, HapticType.MEDIUM)
                                        
                                        if (anchors.size == 2) {
                                            val dist = measurementEngine.calculateDistance(anchors[0].pose, anchors[1].pose)
                                            currentDistance = dist
                                            finalResult = measurementEngine.formatDistance(dist, selectedUnit)
                                            isMeasurementFinished = true
                                        }
                                    } catch (e: Exception) {
                                        Log.e("ARMeasurement", "Error placing point", e)
                                        scope.launch { snackbarHostState.showSnackbar("Failed to place point") }
                                    }
                                }
                            )
                        }

                        ModernIconButton(
                            icon = Icons.Rounded.CameraAlt,
                            onClick = {
                                val activity = context as? Activity
                                activity?.let {
                                    AppHaptics.perform(context, HapticType.MEDIUM)
                                    isCapturing = true
                                    captureAndShareScreenshot(it) { isCapturing = false }
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showHistory) {
            ModalBottomSheet(onDismissRequest = { showHistory = false }, containerColor = Color.White.copy(alpha = 0.95f), dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }) {
                HistoryContent(history = measurementEngine.savedHistory)
            }
        }

        if (showSettings) {
            SettingsScreen(onBack = { showSettings = false })
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp))
    }
}

@Composable
fun HUDDisplay(
    currentMode: AppMode,
    currentDistance: Float,
    isMeasurementFinished: Boolean,
    selectedUnit: MeasurementEngine.UnitType,
    measurementEngine: MeasurementEngine,
    modifier: Modifier = Modifier
) {
    val hudValue = remember(currentMode, currentDistance, isMeasurementFinished, selectedUnit) {
        if (currentMode == AppMode.DISTANCE && currentDistance > 0 && !isMeasurementFinished) {
            measurementEngine.formatDistance(currentDistance, selectedUnit)
        } else ""
    }

    AnimatedVisibility(
        visible = hudValue.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        HUDCard(value = hudValue)
    }
}

@Composable
fun LevelModeToggle(selectedMode: LevelMode, onModeSelected: (LevelMode) -> Unit) {
    Surface(
        color = Color.Black.copy(alpha = 0.4f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.padding(16.dp).border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            LevelModeItem("FLOOR", selectedMode == LevelMode.FLOOR) { onModeSelected(LevelMode.FLOOR) }
            LevelModeItem("WALL", selectedMode == LevelMode.WALL) { onModeSelected(LevelMode.WALL) }
        }
    }
}

@Composable
fun LevelModeItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (isSelected) Color.White else Color.Transparent, label = "bg")
    val content by animateColorAsState(if (isSelected) Color.Black else Color.White, label = "content")
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = content, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
fun ModernLevelUI(roll: Float, pitch: Float, mode: LevelMode) {
    val context = LocalContext.current
    val isLevel = if (mode == LevelMode.FLOOR) {
        abs(roll) < 0.5f && abs(pitch) < 0.5f
    } else {
        abs(roll) < 0.5f
    }

    var wasLevel by remember { mutableStateOf(false) }
    LaunchedEffect(isLevel) {
        if (isLevel && !wasLevel) {
            AppHaptics.perform(context, HapticType.STRONG)
        }
        wasLevel = isLevel
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(if (mode == LevelMode.WALL) 400.dp else 300.dp)) {
        if (mode == LevelMode.FLOOR) {
            FloorLevelUI(roll = roll, pitch = pitch, isLevel = isLevel)
        } else {
            WallLevelUI(roll = roll, isLevel = isLevel)
        }
    }
}

@Composable
fun FloorLevelUI(roll: Float, pitch: Float, isLevel: Boolean) {
    val animatedRoll by animateFloatAsState(roll, label = "roll")
    val animatedPitch by animateFloatAsState(pitch, label = "pitch")
    
    Box(contentAlignment = Alignment.Center) {
        // Outer glow
        Box(
            modifier = Modifier
                .size(220.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            if (isLevel) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Background Circle (Outer rim)
        Box(
            modifier = Modifier
                .size(190.dp)
                .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
        )
        
        // Markings
        Canvas(modifier = Modifier.size(190.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2
            
            // Draw crosshairs
            drawLine(Color.White.copy(alpha = 0.15f), Offset(0f, center.y), Offset(size.width, center.y), 1.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.15f), Offset(center.x, 0f), Offset(center.x, size.height), 1.dp.toPx())
            
            // Draw degree circles
            drawCircle(Color.White.copy(alpha = 0.1f), radius * 0.33f, style = Stroke(1.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.1f), radius * 0.66f, style = Stroke(1.dp.toPx()))
        }

        // Fixed Crosshair (Center point)
        Box(
            modifier = Modifier
                .size(40.dp)
                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
        )

        // Moving Bubble
        Box(
            modifier = Modifier
                .offset(x = (animatedRoll * 4).dp, y = (animatedPitch * 4).dp)
                .size(60.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            if (isLevel) Color(0xFF4CAF50).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.6f),
                            if (isLevel) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.2f)
                        )
                    ),
                    CircleShape
                )
                .border(2.dp, if (isLevel) Color(0xFF4CAF50) else Color.White, CircleShape)
                .blur(1.dp)
        )

        // Reflection on bubble
        Box(
            modifier = Modifier
                .offset(x = (animatedRoll * 4 - 8).dp, y = (animatedPitch * 4 - 8).dp)
                .size(12.dp)
                .background(Color.White.copy(alpha = 0.4f), CircleShape)
        )
    }
}

@Composable
fun WallLevelUI(roll: Float, isLevel: Boolean) {
    val animatedRoll by animateFloatAsState(roll, label = "roll")
    val color = if (isLevel) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.15f)
    
    Box(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .height(260.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Tilted Liquid/Horizon
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            
            withTransform({
                rotate(degrees = -animatedRoll, pivot = center)
            }) {
                // Bottom half fill
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(color, color.copy(alpha = 0.3f))
                    ),
                    topLeft = Offset(-width, height / 2),
                    size = androidx.compose.ui.geometry.Size(width * 3, height * 2)
                )
                
                // Horizon line
                drawLine(
                    color = if (isLevel) Color.White else color.copy(alpha = 0.8f),
                    start = Offset(-width, height / 2),
                    end = Offset(width * 2, height / 2),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        // Static Scale/Ticks
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Static reference marks could be added here for more detail
        }

        // Center Indicator (Glass look)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.05f), Color.Transparent, Color.Black.copy(alpha = 0.1f))
                    )
                )
        )

        // Degree readout
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${String.format(Locale.US, "%.1f", abs(roll))}°",
                color = Color.White,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 72.sp,
                    shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 8f)
                )
            )
            if (isLevel) {
                Text(
                    "PERFECTLY LEVEL",
                    color = Color(0xFF4CAF50),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                )
            }
        }
    }
}

@Composable
fun LevelDetails(roll: Float, pitch: Float, mode: LevelMode) {
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            DetailItem(label = "ROLL", value = "${String.format(Locale.US, "%.1f", roll)}°")
            if (mode == LevelMode.FLOOR) {
                DetailItem(label = "PITCH", value = "${String.format(Locale.US, "%.1f", pitch)}°")
            }
            DetailItem(label = "MODE", value = mode.name)
        }
    }
}

@Composable
fun DetailItem(label: String, value: String) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
fun CompassDetails(azimuth: Float, accuracy: String) {
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DetailItem(label = "HEADING", value = "${String.format(Locale.US, "%.1f", azimuth)}°")
            DetailItem(label = "ACCURACY", value = accuracy)
            DetailItem(label = "SENSOR", value = "Fusion")
        }
    }
}

@Composable
fun ModernCompassUI(azimuth: Float) {
    val context = LocalContext.current
    val animatedAzimuth by animateFloatAsState(azimuth, label = "azimuth")
    val normalizedAzimuth = (azimuth + 360) % 360

    val cardinalIndex = remember(normalizedAzimuth) {
        val index = ((normalizedAzimuth + 20) / 90).toInt() % 4
        val isClose = abs(normalizedAzimuth - (index * 90)) < 3f || abs(normalizedAzimuth - 360) < 3f
        if (isClose) index else -1
    }

    LaunchedEffect(cardinalIndex) {
        if (cardinalIndex != -1) {
            AppHaptics.perform(context, HapticType.LIGHT)
        }
    }

    Box(contentAlignment = Alignment.Center) {
        // Outer Shadow/Glow
        Box(
            modifier = Modifier
                .size(340.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.05f), Color.Transparent)
                    )
                )
        )

        // Rotating Dial
        Canvas(modifier = Modifier.size(300.dp).rotate(-animatedAzimuth)) {
            val radius = size.width / 2
            val tickColor = Color.White.copy(alpha = 0.5f)
            
            for (i in 0 until 360 step 2) {
                val angle = Math.toRadians(i.toDouble())
                val isMajor = i % 30 == 0
                val isMedium = i % 10 == 0 && !isMajor
                
                val tickLength = when {
                    isMajor -> 20.dp.toPx()
                    isMedium -> 12.dp.toPx()
                    else -> 6.dp.toPx()
                }
                
                val start = Offset(
                    (radius + (radius - tickLength) * Math.sin(angle)).toFloat(),
                    (radius - (radius - tickLength) * Math.cos(angle)).toFloat()
                )
                val end = Offset(
                    (radius + radius * Math.sin(angle)).toFloat(),
                    (radius - radius * Math.cos(angle)).toFloat()
                )
                drawLine(if (isMajor) Color.White else tickColor, start, end, if (isMajor) 2.5.dp.toPx() else 1.dp.toPx())
            }
        }

        // Cardinal markers (Fixed)
        Box(modifier = Modifier.size(280.dp)) {
            listOf(0f to "N", 90f to "E", 180f to "S", 270f to "W").forEach { (angle, label) ->
                // These are rotating with the dial in this design
            }
        }

        // Center Indicator (Pointer) - Fixed at top
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 24.dp)
                    .background(Color.Red, RoundedCornerShape(2.dp))
            )
        }

        // Heading Text
        val heading = when {
            normalizedAzimuth >= 337.5 || normalizedAzimuth < 22.5 -> "NORTH"
            normalizedAzimuth >= 22.5 && normalizedAzimuth < 67.5 -> "NORTH EAST"
            normalizedAzimuth >= 67.5 && normalizedAzimuth < 112.5 -> "EAST"
            normalizedAzimuth >= 112.5 && normalizedAzimuth < 157.5 -> "SOUTH EAST"
            normalizedAzimuth >= 157.5 && normalizedAzimuth < 202.5 -> "SOUTH"
            normalizedAzimuth >= 202.5 && normalizedAzimuth < 247.5 -> "SOUTH WEST"
            normalizedAzimuth >= 247.5 && normalizedAzimuth < 292.5 -> "WEST"
            else -> "NORTH WEST"
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${String.format(Locale.US, "%.0f", normalizedAzimuth)}°",
                color = Color.White,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.ExtraLight,
                    fontSize = 80.sp
                )
            )
            Text(
                text = heading,
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
            )
        }
    }
}

@Composable
fun HUDCard(value: String) {
    Surface(
        modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.6f)
    ) {
        Box(modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp
                ),
                color = Color.White
            )
        }
    }
}

@Composable
fun ResultCard(value: String, onClose: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = Color.White.copy(alpha = 0.95f),
        shadowElevation = 24.dp,
        modifier = Modifier.padding(32.dp)
    ) {
        Box {
            IconButton(
                onClick = { onClose() },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.Gray)
            }
            
            Column(
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Measurement Complete", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                Text(text = value, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black), color = Color.Black)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ModernIconButton(icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick, 
        shape = CircleShape, 
        color = Color.Black.copy(alpha = 0.4f),
        modifier = Modifier.size(56.dp).border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
    ) {
        Box(contentAlignment = Alignment.Center) { 
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp)) 
        }
    }
}

@Composable
fun MainActionButton(canAdd: Boolean, isCheck: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (canAdd) 1f else 0.9f, label = "scale")
    Box(
        modifier = Modifier
            .scale(scale)
            .size(88.dp)
            .border(1.dp, Color.White.copy(alpha = if(canAdd) 0.5f else 0.2f), CircleShape)
            .clip(CircleShape)
            .background(if (canAdd) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.2f))
            .clickable(enabled = canAdd, onClick = onClick), 
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isCheck) Icons.Rounded.Check else Icons.Rounded.Add, 
            contentDescription = null, 
            tint = if(canAdd) Color.Black else Color.White.copy(alpha = 0.6f), 
            modifier = Modifier.size(38.dp)
        )
    }
}

@Composable
fun TopBar(
    currentMode: AppMode,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onClearClick: () -> Unit,
    onSaveClick: () -> Unit,
    onUnitClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                ModernIconButton(
                    icon = Icons.Rounded.MoreVert,
                    onClick = { showMenu = true }
                )
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.9f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Save Measurement", color = Color.White) },
                        onClick = { showMenu = false; onSaveClick() },
                        leadingIcon = { Icon(Icons.Rounded.Save, contentDescription = null, tint = Color.White) }
                    )
                    DropdownMenuItem(
                        text = { Text("Units", color = Color.White) },
                        onClick = { showMenu = false; onUnitClick() },
                        leadingIcon = { Icon(Icons.Rounded.Architecture, contentDescription = null, tint = Color.White) }
                    )
                }
            }
        }
    }
}

@Composable
fun UnitSelectorOverlay(
    selectedUnit: MeasurementEngine.UnitType,
    onUnitSelected: (MeasurementEngine.UnitType) -> Unit
) {
    Surface(
        modifier = Modifier
            .width(220.dp)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 16.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val units = listOf(
                listOf(MeasurementEngine.UnitType.MM to "mm", MeasurementEngine.UnitType.CM to "cm", MeasurementEngine.UnitType.M to "m"),
                listOf(MeasurementEngine.UnitType.IN to "in", MeasurementEngine.UnitType.FT to "ft", MeasurementEngine.UnitType.YD to "yd")
            )
            
            units.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { (unit, label) ->
                        val isSelected = unit == selectedUnit
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) Color.Black else Color(0xFFF5F5F5))
                                .clickable { onUnitSelected(unit) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Reticle(isHitting: Boolean, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(if (isHitting) 1f else 0.5f, label = "alpha")
    Box(modifier = modifier.size(60.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            val color = Color.White.copy(alpha = alpha)
            val length = 12.dp.toPx()
            drawLine(color, Offset(size.width/2, 0f), Offset(size.width/2, length), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(size.width/2, size.height), Offset(size.width/2, size.height - length), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(0f, size.height/2), Offset(length, size.height/2), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(size.width, size.height/2), Offset(size.width - length, size.height/2), strokeWidth, StrokeCap.Round)
            drawCircle(color, 3.dp.toPx())
        }
    }
}

fun captureAndShareScreenshot(activity: Activity, onComplete: () -> Unit) {
    val window = activity.window
    val view = window.decorView
    if (view.width <= 0 || view.height <= 0) { onComplete(); return }
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    try {
        PixelCopy.request(window, bitmap, { result ->
            if (result == PixelCopy.SUCCESS) {
                val timestamp = System.currentTimeMillis()
                val fileName = "measure_$timestamp.png"

                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MeasureMe")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }
                    val resolver = activity.contentResolver
                    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    val itemUri = resolver.insert(collection, values)
                    itemUri?.let { uri ->
                        resolver.openOutputStream(uri).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out!!)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            values.clear()
                            values.put(MediaStore.Images.Media.IS_PENDING, 0)
                            resolver.update(uri, values, null, null)
                        }
                        Toast.makeText(activity, "Saved to Gallery", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) { }

                val file = File(activity.cacheDir, fileName)
                try {
                    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    activity.startActivity(Intent.createChooser(intent, "Share Measurement"))
                } catch (e: Exception) { }
            }
            onComplete()
        }, Handler(Looper.getMainLooper()))
    } catch (e: Exception) { onComplete() }
}

@Composable
fun GuidanceOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlaneDetectionAnimation()
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Detecting Plane...",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
        ) {
            GuidanceCarousel()
        }
    }
}

@Composable
fun PlaneDetectionAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "planeDetection")
    val slide by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 50f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phoneSlide"
    )

    Canvas(modifier = Modifier.size(120.dp, 80.dp)) {
        val width = size.width
        val height = size.height
        val color = Color.White
        val stroke = 1.5.dp.toPx()

        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(width * 0.2f, height * 0.2f)
            lineTo(width * 0.8f, height * 0.2f)
            lineTo(width, height * 0.8f)
            lineTo(0f, height * 0.8f)
            close()
        }
        drawPath(path, color, style = Stroke(stroke))

        for (i in 1..3) {
            val ratio = i / 4f
            drawLine(color, Offset(width * (0.2f + 0.6f * ratio), height * 0.2f), Offset(width * ratio, height * 0.8f), stroke)
        }
        for (i in 1..2) {
            val ratio = i / 3f
            val y = height * (0.2f + 0.6f * ratio)
            val xStart = width * (0.2f - 0.2f * ratio)
            val xEnd = width * (0.8f + 0.2f * ratio)
            drawLine(color, Offset(xStart, y), Offset(xEnd, y), stroke)
        }

        val density = this
        withTransform({
            translate(left = with(density) { slide.dp.toPx() }, top = 0f)
        }) {
            val phoneW = 20.dp.toPx()
            val phoneH = 36.dp.toPx()
            val phoneX = (width - phoneW) / 2
            val phoneY = (height - phoneH) / 2 + 10.dp.toPx()
            
            drawRoundRect(
                color = Color.Black,
                topLeft = Offset(phoneX, phoneY),
                size = androidx.compose.ui.geometry.Size(phoneW, phoneH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(phoneX, phoneY),
                size = androidx.compose.ui.geometry.Size(phoneW, phoneH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                style = Stroke(2.dp.toPx())
            )
            drawLine(color, Offset(phoneX + 4.dp.toPx(), phoneY + 4.dp.toPx()), Offset(phoneX + phoneW - 4.dp.toPx(), phoneY + 4.dp.toPx()), 1.dp.toPx())
        }
    }
}

data class GuidanceStep(val title: String, val description: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GuidanceCarousel() {
    val steps = listOf(
        GuidanceStep("Ensure Good Lighting", "Good lighting helps the app detect surfaces better. Avoid shadows and dark spots."),
        GuidanceStep("Move Smoothly", "Slowly move around the object to help the app map it precisely."),
        GuidanceStep("Point at Surface", "Aim the reticle at a flat surface like a floor or table to start measuring.")
    )
    
    val pagerState = rememberPagerState(pageCount = { steps.size })
    
    LaunchedEffect(Unit) {
        while(true) {
            delay(4000)
            val next = (pagerState.currentPage + 1) % steps.size
            pagerState.animateScrollToPage(next)
        }
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = Color.White.copy(alpha = 0.9f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text(
                "Need better accuracy?",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.Black
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.height(100.dp)
        ) { page ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = steps[page].title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = steps[page].description,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(steps.size) { i ->
                val active = pagerState.currentPage == i
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (active) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (active) Color.White else Color.White.copy(alpha = 0.3f))
                )
            }
        }
    }
}

@Composable
fun HistoryContent(history: List<SavedMeasurement>) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text("History", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(16.dp))
        if (history.isEmpty()) {
            Text("No saved measurements", color = Color.Gray)
        } else {
            androidx.compose.foundation.lazy.LazyColumn {
                items(history.size) { index ->
                    val item = history[index]
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(item.label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(item.value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                    if (index < history.size - 1) HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))
                }
            }
        }
    }
}
