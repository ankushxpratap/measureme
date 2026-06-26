package com.example.measureme.logic

import androidx.compose.runtime.mutableStateListOf
import com.example.measureme.data.supabase
import com.google.ar.core.Anchor
import com.google.ar.core.Pose
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.sqrt

data class Measurement(
    val id: String = UUID.randomUUID().toString(),
    val startAnchor: Anchor,
    val endAnchor: Anchor,
    val distanceMeters: Float,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SavedMeasurement(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val value: String,
    val timestamp: Long = System.currentTimeMillis(),
    @SerialName("user_id")
    val userId: String? = null
)

class MeasurementEngine {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _measurements = mutableListOf<Measurement>()
    val measurements: List<Measurement> get() = _measurements
    
    private val _savedHistory = mutableStateListOf<SavedMeasurement>()
    val savedHistory: List<SavedMeasurement> get() = _savedHistory

    fun calculateDistance(startPose: Pose, endPose: Pose): Float {
        val dx = startPose.tx() - endPose.tx()
        val dy = startPose.ty() - endPose.ty()
        val dz = startPose.tz() - endPose.tz()
        return sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
    }

    fun calculateDistance2D(startPose: Pose, endPose: Pose): Float {
        val dx = startPose.tx() - endPose.tx()
        val dz = startPose.tz() - endPose.tz()
        return sqrt((dx * dx + dz * dz).toDouble()).toFloat()
    }

    fun addMeasurement(startAnchor: Anchor, endAnchor: Anchor) {
        val distance = calculateDistance(startAnchor.pose, endAnchor.pose)
        _measurements.add(Measurement(startAnchor = startAnchor, endAnchor = endAnchor, distanceMeters = distance))
    }

    fun clearMeasurements() {
        _measurements.forEach {
            it.startAnchor.detach()
            it.endAnchor.detach()
        }
        _measurements.clear()
    }

    fun undoLast() {
        if (_measurements.isNotEmpty()) {
            val last = _measurements.removeAt(_measurements.size - 1)
            last.startAnchor.detach()
            last.endAnchor.detach()
        }
    }

    fun formatDistance(meters: Float, unit: UnitType = UnitType.CM): String {
        return when (unit) {
            UnitType.MM -> String.format("%.0f mm", meters * 1000)
            UnitType.CM -> String.format("%.1f cm", meters * 100)
            UnitType.M -> String.format("%.2f m", meters)
            UnitType.IN -> String.format("%.1f in", meters * 39.3701f)
            UnitType.FT -> String.format("%.2f ft", meters * 3.28084f)
            UnitType.YD -> String.format("%.2f yd", meters * 1.09361f)
        }
    }

    fun saveMeasurement(label: String, value: String) {
        val currentUser = try {
            supabase.auth.currentUserOrNull()
        } catch (e: Exception) {
            null
        }
        val measurement = SavedMeasurement(
            id = UUID.randomUUID().toString(),
            label = label,
            value = value,
            timestamp = System.currentTimeMillis(),
            userId = currentUser?.id
        )
        
        _savedHistory.add(0, measurement)

        if (currentUser != null) {
            scope.launch {
                try {
                    supabase.postgrest["measurements"].insert(measurement)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun loadFromSupabase() {
        val currentUser = try {
            supabase.auth.currentUserOrNull() ?: return
        } catch (e: Exception) {
            return
        }
        scope.launch {
            try {
                val results = supabase.postgrest["measurements"]
                    .select {
                        filter {
                            eq("user_id", currentUser.id)
                        }
                    }
                    .decodeList<SavedMeasurement>()
                
                launch(Dispatchers.Main) {
                    _savedHistory.clear()
                    _savedHistory.addAll(results.sortedByDescending { it.timestamp })
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteMeasurement(measurement: SavedMeasurement) {
        _savedHistory.remove(measurement)
        
        val currentUser = try {
            supabase.auth.currentUserOrNull()
        } catch (e: Exception) {
            null
        }

        if (currentUser != null) {
            scope.launch {
                try {
                    supabase.postgrest["measurements"].delete {
                        filter {
                            eq("id", measurement.id)
                            eq("user_id", currentUser.id)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    enum class UnitType {
        MM, CM, M, IN, FT, YD
    }
}
