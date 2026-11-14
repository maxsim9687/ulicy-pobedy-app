package space.maxsim.ulicy

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

data class Fix(val lat: Double, val lon: Double, val acc: Float)
data class Orientation(val azimuth: Double, val pitch: Double, val roll: Double, val ok: Boolean)

/** GPS через системный LocationManager (без сервисов Google). */
@SuppressLint("MissingPermission")
@Composable
fun rememberLocation(enabled: Boolean): State<Fix?> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf<Fix?>(null) }
    DisposableEffect(enabled) {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { l: Location ->
            val cur = state.value
            // берём новую точку, если она точнее или свежая
            if (cur == null || l.accuracy <= cur.acc * 1.5f || l.provider == LocationManager.GPS_PROVIDER)
                state.value = Fix(l.latitude, l.longitude, l.accuracy)
        }
        if (enabled) {
            try {
                listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { prov ->
                    if (lm.isProviderEnabled(prov)) {
                        lm.getLastKnownLocation(prov)?.let { if (state.value == null) state.value = Fix(it.latitude, it.longitude, it.accuracy) }
                        lm.requestLocationUpdates(prov, 1000L, 0f, listener, Looper.getMainLooper())
                    }
                }
            } catch (_: SecurityException) { }
        }
        onDispose { try { lm.removeUpdates(listener) } catch (_: Exception) { } }
    }
    return state
}

/** Ориентация задней камеры: азимут (компас) и наклон, по датчику вращения. */
@Composable
fun rememberOrientation(enabled: Boolean): State<Orientation> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(Orientation(0.0, 0.0, 0.0, false)) }
    DisposableEffect(enabled) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        val rot = FloatArray(9); val remap = FloatArray(9); val out = FloatArray(3)
        var az = Double.NaN; var pitch = 0.0
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                // телефон вертикально, камера смотрит вперёд
                SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_X, SensorManager.AXIS_Z, remap)
                SensorManager.getOrientation(remap, out)
                val a = (Math.toDegrees(out[0].toDouble()) + 360) % 360
                val p = Math.toDegrees(out[1].toDouble())
                // сглаживание, чтобы табличка не дрожала
                az = if (az.isNaN()) a else (az + Geo.angleDiff(a, az) * 0.15 + 360) % 360
                pitch += (p - pitch) * 0.15
                state.value = Orientation(az, pitch, Math.toDegrees(out[2].toDouble()), true)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (enabled && sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
    return state
}
