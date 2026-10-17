package de.demogorgi.sds011native;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

public final class MeasurementForegroundService extends Service implements LocationListener {
    public static final String EXTRA_TRACK_LOCATION = "track_location";
    private static final String CHANNEL_ID = "measurement";
    private static volatile Location latestLocation;
    private LocationManager locationManager;
    private PowerManager.WakeLock wakeLock;
    private boolean trackLocation;

    public static Location getLatestLocation() {
        return latestLocation;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        trackLocation = intent != null
                && intent.getBooleanExtra(EXTRA_TRACK_LOCATION, false);
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int type = trackLocation
                    ? android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                        | android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    : android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
            startForeground(1, notification, type);
        } else {
            startForeground(1, notification);
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "SDS011Native:Measurement");
            wakeLock.acquire();
        }
        if (trackLocation) {
            latestLocation = null;
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            requestUpdates();
        } else {
            latestLocation = null;
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onLocationChanged(Location location) {
        latestLocation = new Location(location);
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, android.os.Bundle extras) {
    }

    @Override
    public void onDestroy() {
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
        latestLocation = null;
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void requestUpdates() {
        if (locationManager == null
                || (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)) {
            stopSelf();
            return;
        }
        try {
            Criteria criteria = new Criteria();
            boolean fineLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
            criteria.setAccuracy(fineLocation
                    ? Criteria.ACCURACY_FINE : Criteria.ACCURACY_COARSE);
            String provider = locationManager.getBestProvider(criteria, true);
            if (provider != null) {
                Location previous = locationManager.getLastKnownLocation(provider);
                if (previous != null) {
                    latestLocation = previous;
                }
                locationManager.requestLocationUpdates(provider, 5000L, 0.0f,
                        this, Looper.getMainLooper());
            }
            if (fineLocation && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    && !LocationManager.GPS_PROVIDER.equals(provider)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        5000L, 0.0f, this, Looper.getMainLooper());
            }
        } catch (SecurityException exc) {
            stopSelf();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.foreground_channel), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(
                    getString(R.string.foreground_description));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(trackLocation
                        ? getString(R.string.foreground_trip_running) : getString(R.string.foreground_measurement_running))
                .setContentText(trackLocation ? getString(R.string.foreground_gps_active) : getString(R.string.foreground_sensor_connected))
                .setOngoing(true)
                .build();
    }
}
