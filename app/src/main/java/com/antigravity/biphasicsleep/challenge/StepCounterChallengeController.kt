package com.antigravity.biphasicsleep.challenge

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Physical Step Counter Challenge (Module 4) using Android TYPE_STEP_DETECTOR
 * with an automatic accelerometer step-peak fallback for devices/emulators
 * without a dedicated hardware step sensor.
 */
class StepCounterChallengeController(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepDetectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val accelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _stepsDetected = MutableStateFlow(0)
    val stepsDetected: StateFlow<Int> = _stepsDetected.asStateFlow()

    private val _sensorSourceLabel = MutableStateFlow("")
    val sensorSourceLabel: StateFlow<String> = _sensorSourceLabel.asStateFlow()

    private var lastAccelStepTimestampMs: Long = 0L

    fun startListening() {
        _stepsDetected.value = 0
        if (stepDetectorSensor != null) {
            _sensorSourceLabel.value = "Hardware Step Detector (TYPE_STEP_DETECTOR)"
            sensorManager.registerListener(
                this,
                stepDetectorSensor,
                SensorManager.SENSOR_DELAY_FASTEST
            )
        } else if (accelerometerSensor != null) {
            _sensorSourceLabel.value = "Accelerometer Motion Peak Detector (Fallback)"
            sensorManager.registerListener(
                this,
                accelerometerSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        } else {
            _sensorSourceLabel.value = "Manual / Simulator Step Mode"
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    fun recordSimulatedStep() {
        _stepsDetected.value += 1
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> {
                _stepsDetected.value += event.values.size.coerceAtLeast(1)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt((x * x + y * y + z * z).toDouble())
                val now = System.currentTimeMillis()
                // Detect distinct walking/movement peak (> 13.2 m/s^2 with >= 380ms debounce)
                if (magnitude > 13.2 && (now - lastAccelStepTimestampMs) > 380L) {
                    lastAccelStepTimestampMs = now
                    _stepsDetected.value += 1
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
