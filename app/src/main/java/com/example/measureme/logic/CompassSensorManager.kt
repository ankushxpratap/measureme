package com.example.measureme.logic

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class CompassSensorManager(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var lastAzimuth = 0f
    private val alpha = 0.12f // Even smoother but responsive

    var onAzimuthChanged: ((azimuth: Float) -> Unit)? = null
    var onAccuracyChanged: ((accuracy: String) -> Unit)? = null

    fun start() {
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magneticSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)
            
            var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (azimuth < 0) azimuth += 360f

            val delta = azimuth - lastAzimuth
            val smoothedDelta = if (delta > 180) delta - 360 else if (delta < -180) delta + 360 else delta
            
            lastAzimuth += alpha * smoothedDelta
            var finalAzimuth = (lastAzimuth + 360) % 360
            
            // Snap to cardinal points for perceived accuracy
            if (Math.abs(finalAzimuth - 0) < 0.5f || Math.abs(finalAzimuth - 360) < 0.5f) finalAzimuth = 0f
            else if (Math.abs(finalAzimuth - 90) < 0.5f) finalAzimuth = 90f
            else if (Math.abs(finalAzimuth - 180) < 0.5f) finalAzimuth = 180f
            else if (Math.abs(finalAzimuth - 270) < 0.5f) finalAzimuth = 270f
            
            onAzimuthChanged?.invoke(finalAzimuth)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            val accuracyStr = when (accuracy) {
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
                SensorManager.SENSOR_STATUS_UNRELIABLE -> "Unreliable"
                else -> "Unknown"
            }
            onAccuracyChanged?.invoke(accuracyStr)
        }
    }
}
