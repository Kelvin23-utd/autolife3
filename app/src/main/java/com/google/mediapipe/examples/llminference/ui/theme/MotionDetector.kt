package com.google.mediapipe.examples.llminference

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import kotlin.math.abs

class MotionDetector(private val context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val motionStorage = MotionStorage(context)

    private var lastAcceleration = 0.0
    private var stepCount = 0
    private var lastAltitude = 0.0
    private var currentSpeed = 0.0

    private var motionCallback: ((List<String>) -> Unit)? = null

    // Function to get motion history
    fun getMotionHistory(): String? {
        return motionStorage.getMotionHistory()
    }

    // Function to clear motion history
    fun clearMotionHistory(): Boolean {
        return motionStorage.clearMotionHistory()
    }

    // Initialize sensors
    fun startDetection(callback: (List<String>) -> Unit) {
        motionCallback = callback

        // Set up accelerometer
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let { accelerometer ->
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }

        // Set up step counter
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let { stepSensor ->
            sensorManager.registerListener(
                this,
                stepSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }

        // Set up pressure sensor for altitude
        sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let { pressureSensor ->
            sensorManager.registerListener(
                this,
                pressureSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }

        // Set up location updates for speed
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000, // Update interval in milliseconds
                1f,   // Minimum distance in meters
                locationListener
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted", e)
        }
    }

    fun stopDetection() {
        sensorManager.unregisterListener(this)
        try {
            locationManager.removeUpdates(locationListener)
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted", e)
        }
        motionCallback = null
    }

    // Main motion detection algorithm
    private fun detectMotion(
        stepCount: Int,
        acceleration: Double,
        altitudeChange: Double,
        speed: Double
    ): List<String> {
        val motions = mutableListOf<String>()

        // Check for stationary state
        if (stepCount <= 2 && acceleration <= 0.1 &&
            abs(altitudeChange) <= 0.1 && speed <= 0.1) {
            motions.add("stationary")
        }
        // Check for limited motion
        else if (stepCount <= 10 && abs(altitudeChange) <= 1.0 && speed < 0.5) {
            motions.add("limited motion")
        }

        // Check for jogging/running
        if (stepCount >= 140 && speed >= 2.0 && speed <= 5.0) {
            motions.add("jogging/running")
        }

        // Check for walking
        if (stepCount >= 50 && speed < 1.8) {
            motions.add("walking")
        }

        // Check for cycling
        if (stepCount >= 50 && speed >= 4.0) {
            motions.add("cycling")
        }

        // Check for vehicular motion
        if ((stepCount <= 5 && speed > 2.0) || speed > 5.0) {
            motions.add("vehicle/subway/ferry/train")
        }

        // Check for elevator/escalator
        if (stepCount <= 10 && altitudeChange > 2.5 && speed < 2.0) {
            motions.add("escalator/elevator")
        }

        return motions
    }

    // Sensor event handling
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                // Get magnitude of acceleration excluding gravity
                lastAcceleration = event.values[0].toDouble()
            }
            Sensor.TYPE_STEP_COUNTER -> {
                stepCount = event.values[0].toInt()
            }
            Sensor.TYPE_PRESSURE -> {
                // Convert pressure to altitude using barometric formula
                val pressure = event.values[0]
                val altitude = SensorManager.getAltitude(
                    SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                    pressure
                ).toDouble()
                val altitudeChange = altitude - lastAltitude
                lastAltitude = altitude

                // Detect motion with current sensor values
                val motions = detectMotion(
                    stepCount = stepCount,
                    acceleration = lastAcceleration,
                    altitudeChange = altitudeChange,
                    speed = currentSpeed
                )

                // Save motion detection to storage
                motionStorage.saveMotionDetection(motions)

                // Notify callback with detected motions
                motionCallback?.invoke(motions)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentSpeed = location.speed.toDouble() // Speed in meters/second
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    companion object {
        private const val TAG = "MotionDetector"
    }
}