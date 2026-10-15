# SDS011 Android Native

Native Android port of the SDS011 particulate matter sensor app.

This repository ports the QPython app to a dependency-light native Java
Android app:

- native Android Activity, no WebView; AndroidX Core provides secure file sharing
- Bluetooth Classic SPP connection to a paired SDS011/HC-05/HC-06 device
- automatic Bluetooth reconnect with exponential delays capped at 60 seconds
- sensor silence detection with reconnect (planned SDS011 sleep periods are
  excluded from the timeout)
- SDS011 frame decoder and sleep/work-period commands
- current PM2.5/PM10 display
- raw-value and mean-value in-memory plot with "Plot leeren"
- measurement interval controls, including the 5/15 min sleep-wake concept:
  15 s settling time, then 30 s usable 1 Hz values for averaging
- saved measurement interval and selected measurement mode
- CSV recording for trip, local, and stationary sessions; the final partial
  interval is written when a session stops
- GPS coordinates in trip CSV files and separate PM2.5/PM10 KML tracks with
  measured concentration as altitude; stale fixes break the track
- location foreground service and wake lock during trip recording for
  screen-off tracking
- sensor.community upload for every new stationary mean (requires the
  configured sensor ID and registered sensor coordinates)
- persistent Bluetooth device selection

CSV files use semicolon separators and German decimal commas. CSV and KML
files are saved in the app-specific `Documents/Feinstaub` directory, and the
CSV path is shown in the measurement card. After stopping a measurement, tap
**Messdateien öffnen / teilen** and select a CSV or KML file, including recordings
from earlier app versions. **Öffnen mit …** launches a compatible installed app
(for example Google Earth for KML); **Teilen …** opens Android's share sheet for
apps such as Gmail or WhatsApp. **Speichern unter …** copies the file to a
location chosen in Android's document picker, such as Downloads. These exported
copies remain accessible independently of the app and survive its removal.
Opening and sharing use a read-only content URI with a temporary access grant,
provided by AndroidX Core's FileProvider, scoped to the recording directories.
The original recordings stay in app storage; uninstalling the app removes them.
Stop the current measurement before opening the file list so KML documents
are finalized. No broad storage permission or PC connection is needed.

Trip recording requests location
permission at runtime; fixes older than 15 seconds are omitted. Settings and
recordings require no storage permission. Android may limit location delivery
while the app is in the background; measurements run in a foreground service
with a persistent notification and partial wake lock, and trip recording also
requests location updates there. Stationary uploads send public data over
HTTPS: configure the sensor.community `X-Sensor` ID before starting that
mode. Enter the registered sensor latitude/longitude in the measurement
settings; coordinate edits are saved locally immediately. sensor.community
resolves the upload location from the sensor ID, so these coordinates are not
included in the upload payload. When a fresh device location is available,
stationary recording is blocked beyond 500 m from the configured site.
Bluetooth device selection, measurement mode, interval, sensor ID, and
coordinates persist between app launches. Network errors, HTTP 429, and server
errors are retried twice with increasing delays; other HTTP errors are
reported directly.

## Build

The project is a standard Gradle Android project.

Local requirements:

- Android Studio or Android command line SDK
- JDK 17 or newer
- Android SDK platform 35

Open this folder in Android Studio and let it sync the project, or build from
the command line with a Gradle installation:

```powershell
gradle assembleDebug
```

If a Gradle wrapper is added later, run:

```powershell
.\gradlew.bat assembleDebug
```
