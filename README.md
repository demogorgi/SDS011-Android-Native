# SDS011 Android Native

Native Android port of the SDS011 particulate matter sensor app.

This repository ports the QPython app to a dependency-light native Java
Android app:

- native Android Activity, no WebView and no AndroidX requirement
- Bluetooth Classic SPP connection to a paired SDS011/HC-05/HC-06 device
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

Still to port from the Python app:

- sensor.community upload
- persistent device selection and fuller error/backoff handling

CSV files use semicolon separators and German decimal commas. CSV and KML
files are saved in the app-specific `Documents/Feinstaub` directory, and the
CSV path is shown in the measurement card. Trip recording requests location
permission at runtime; fixes older than 15 seconds are omitted. Settings and
recordings require no storage permission. Android may limit location delivery
while the app is in the background; a foreground tracking service is still to
be added for reliable screen-off recording.

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
