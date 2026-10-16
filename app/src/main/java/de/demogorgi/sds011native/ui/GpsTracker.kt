package de.demogorgi.sds011native.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import de.demogorgi.sds011native.ui.state.GpsStatus

class GpsTracker(private val onUpdate: (GpsStatus, Double, Double) -> Unit) : LocationListener {

    private var manager: LocationManager? = null

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        if (manager != null) return
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return
        manager = lm
        onUpdate(GpsStatus.SEARCHING, 0.0, 0.0)
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, this)
    }

    fun stop() {
        manager?.removeUpdates(this)
        manager = null
    }

    override fun onLocationChanged(location: Location) {
        onUpdate(GpsStatus.FIX, location.latitude, location.longitude)
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderDisabled(provider: String) {
        onUpdate(GpsStatus.NONE, 0.0, 0.0)
    }
}
