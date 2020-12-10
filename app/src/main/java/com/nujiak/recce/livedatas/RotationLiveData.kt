package com.nujiak.recce.livedatas

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.lifecycle.LiveData

class RotationLiveData(context: Context, private var rotation: Int) :
    LiveData<RotationLiveData.RotationData>(), SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var lastAccuracy: Int = 0

    override fun onActive() {
        super.onActive()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_UI,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensorManager.registerListener(
                this,
                magneticField,
                SensorManager.SENSOR_DELAY_UI,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    fun updateRotation(newRotation: Int) {
        rotation = newRotation
        updateOrientationAngles()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        }
        updateOrientationAngles()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        lastAccuracy = accuracy

        val (azi, pit, rol) = orientationAngles
        value = RotationData(azi, pit, rol, lastAccuracy)
    }

    private fun updateOrientationAngles() {
        // Update rotation matrix, which is needed to update orientation angles.
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        // "rotationMatrix" now has up-to-date information.
        var remappedRotationMatrix = FloatArray(9)
        when (rotation) {
            Surface.ROTATION_90 -> {
                SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    SensorManager.AXIS_Y,
                    SensorManager.AXIS_MINUS_X,
                    remappedRotationMatrix
                )
            }
            Surface.ROTATION_180 -> {
                SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    SensorManager.AXIS_MINUS_Y,
                    SensorManager.AXIS_MINUS_X,
                    remappedRotationMatrix
                )
            }
            Surface.ROTATION_270 -> {
                SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    SensorManager.AXIS_MINUS_Y,
                    SensorManager.AXIS_X,
                    remappedRotationMatrix
                )
            }
            else -> remappedRotationMatrix = rotationMatrix
        }

        SensorManager.getOrientation(remappedRotationMatrix, orientationAngles)
        // "orientationAngles" now has up-to-date information.

        updateValue()
    }

    private fun updateValue() {
        val (azi, pit, rol) = orientationAngles
        value = RotationData(azi, pit, rol, lastAccuracy)
    }

    override fun onInactive() {
        super.onInactive()
        sensorManager.unregisterListener(this)
    }

    data class RotationData(
        val azimuth: Float,
        val pitch: Float,
        val roll: Float,
        val accuracy: Int
    )
}