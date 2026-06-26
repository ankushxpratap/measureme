package com.example.measureme.logic

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

enum class LevelMode {
    FLOOR, WALL
}

class LevelSensorManager(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    var onLevelChanged: ((roll: Float, pitch: Float, mode: LevelMode) -> Unit)? = null
    var currentMode: LevelMode = LevelMode.FLOOR

    private var smoothedRoll = 0f
    private var smoothedPitch = 0f
    private val alpha = 0.15f // Smoother filtering

    // Calibration offsets
    private var rollOffset = 0f
    private var pitchOffset = 0f

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientationValues = FloatArray(3)

    fun start() {
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun calibrate() {
        rollOffset = smoothedRoll
        pitchOffset = smoothedPitch
    }

    fun resetCalibration() {
        rollOffset = 0f
        pitchOffset = 0f
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            var roll: Float
            var pitch: Float

            if (currentMode == LevelMode.FLOOR) {
                SensorManager.getOrientation(rotationMatrix, orientationValues)
                pitch = Math.toDegrees(orientationValues[1].toDouble()).toFloat()
                roll = Math.toDegrees(orientationValues[2].toDouble()).toFloat()
            } else {
                SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    SensorManager.AXIS_X,
                    SensorManager.AXIS_Z,
                    remappedMatrix
                )
                SensorManager.getOrientation(remappedMatrix, orientationValues)
                pitch = Math.toDegrees(orientationValues[1].toDouble()).toFloat()
                roll = Math.toDegrees(orientationValues[2].toDouble()).toFloat()
            }

            // Apply smoothing filter
            smoothedRoll = alpha * roll + (1 - alpha) * smoothedRoll
            smoothedPitch = alpha * pitch + (1 - alpha) * smoothedPitch

            // Apply calibration offsets
            var finalRoll = smoothedRoll - rollOffset
            var finalPitch = smoothedPitch - pitchOffset

            // Snap to zero for perceived 100% accuracy when very close
            if (Math.abs(finalRoll) < 0.3f) finalRoll = 0f
            if (Math.abs(finalPitch) < 0.3f) finalPitch = 0f

            onLevelChanged?.invoke(finalRoll, finalPitch, currentMode)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
