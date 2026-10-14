# SDS011 Android Native

Native Android port of the SDS011 particulate matter sensor app.

This repository is the start of the QPython/web UI migration. The first cut
contains a dependency-light Java Android app:

- native Android Activity, no WebView and no AndroidX requirement
- Bluetooth Classic SPP connection to a paired SDS011/HC-05/HC-06 device
- SDS011 frame decoder and sleep/work-period commands
- current PM2.5/PM10 display
- raw-value and mean-value in-memory plot with "Plot leeren"
- measurement interval controls, including the 5/15 min sleep-wake concept:
  15 s settling time, then 30 s usable 1 Hz values for averaging

Still to port from the Python app:

- CSV/KML file writing
- GPS and trip track handling
- sensor.community upload
- persistent settings
- fuller error/backoff handling

## Build

The project is a standard Gradle Android project.

Local requirements:

- Android Studio or Android command line SDK
- JDK 17 or newer
- Android SDK platform 35

This machine currently has only Java 8 in `PATH` and no Android SDK/Gradle
installation, so a local APK build cannot run here yet. After installing
Android Studio, open this folder and let Android Studio sync the project, or
build from the command line with a Gradle installation:

```powershell
gradle assembleDebug
```

If a Gradle wrapper is added later, use:

```powershell
.\gradlew.bat assembleDebug
```
