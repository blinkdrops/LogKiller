# LogKiller

Android TV application that resolves the persistent log storage bug affecting millions of Android TV devices worldwide.

## Problem Description

On millions of Android TV devices, the system property `persist.logd.logpersistd.size` defaults to 512 MB. This reserves 512 MB of internal storage for persistent system logs. On devices with 4-8 GB of storage, this allocation causes:

- Insufficient storage errors during normal operation
- Failed Over-The-Air (OTA) updates due to space constraints
- Degraded system performance from storage pressure

Google addressed this issue in AOSP (November 2024) by reducing the default value to 60 MB. However, most existing devices will never receive this update through official manufacturer channels due to discontinued support.

## Technical Solution

LogKiller modifies the system properties at runtime to reduce the log buffer size from 512 MB to 60 MB, freeing approximately 452 MB of storage space. The application provides dual execution paths for both rooted and non-rooted devices.

## System Requirements

- Android TV device running Android 7.0 (API 24) or higher
- Root access OR ADB debugging enabled
- Minimum 4 GB internal storage recommended

## Installation Methods

### Method 1: Build from Source

1. Clone this repository
2. Open the project in Android Studio Arctic Fox or later
3. Sync Gradle files to download dependencies
4. Build and install on your Android TV device via USB or network ADB

### Method 2: Sideload Pre-built APK

1. Download the latest APK from the releases section
2. Enable "Unknown Sources" in your Android TV security settings
3. Install using a file manager application or via ADB:

```bash
adb install logkiller.apk
```

## Usage Instructions

### With Root Access

1. Launch LogKiller on your Android TV device
2. The application will automatically detect root access availability
3. Press "FIX NOW" to apply the system property changes
4. The application will execute the following operations:
   - Set `persist.logd.logpersistd.size` to 60
   - Set `logd.logpersistd.size` to 60 (trampoline workaround for compatibility)
   - Clear existing log files in `/data/misc/logd/`
   - Clear RAM log buffers using `logcat -c`
5. Reboot your device for changes to take full effect

### Without Root Access (ADB Method)

1. Launch LogKiller on your Android TV device
2. Press "FIX NOW" button
3. The application will display the required ADB commands
4. Press "COPY ADB COMMANDS" to copy them to the clipboard
5. On your development computer, connect to the device and execute:

```bash
adb shell setprop persist.logd.logpersistd.size 60
adb shell setprop logd.logpersistd.size 60
adb shell rm -rf /data/misc/logd/*
adb shell logcat -c
```

6. Reboot your device to ensure changes persist

## Application Features

- Automatic detection of current log buffer size via reflection and shell fallbacks
- Real-time storage analysis using StatFs
- Dual execution path support (root and ADB methods)
- One-click fix application for rooted devices
- Copy-pasteable ADB command generation for non-rooted devices
- Restore defaults functionality to revert changes
- D-pad navigation optimized for Android TV remote controls
- Landscape orientation locked for TV compatibility
- State persistence across application restarts and reboots
- Comprehensive error handling with user-friendly status messages

## Technical Implementation Details

### System Properties Modified

| Property | Default Value | Fixed Value |
|----------|---------------|-------------|
| persist.logd.logpersistd.size | 512 | 60 |
| logd.logpersistd.size | 512 | 60 |

### Storage Impact Analysis

- Before fix: 512 MB reserved for persistent logs
- After fix: 60 MB reserved for persistent logs
- Typical storage savings: Approximately 452 MB

### Files and Directories Affected

- `/data/misc/logd/` - System log directory (contents cleared during fix application)

### Permission Requirements

The application requests the following permissions:

- `READ_LOGS` - To read system log properties (system-level permission, may require manual grant)
- `WRITE_EXTERNAL_STORAGE` - For storage space calculation
- `READ_EXTERNAL_STORAGE` - For storage space calculation
- `INTERNET` - Reserved for future update verification features

Note: The READ_LOGS permission is a system-level permission that may not be granted on all devices. The application includes fallback mechanisms using shell commands when this permission is unavailable.

## Troubleshooting Guide

### Application displays "Logging is off. Nothing to fix."

This indicates that persistent logging is already disabled on your device via the `persist.logd.logpersistd.buffer` property. No action is required.

### Application displays "Device is already optimized"

Your device already has a log limit of 60 MB or lower. This may indicate a previous fix application or manufacturer configuration. No action is required.

### Root command execution fails

If root commands fail with an error, use the ADB method instead. Connect your device to a computer with ADB installed and execute the provided commands manually.

### Fix does not persist after device reboot

Some device manufacturers may reset system properties during boot. If the fix does not persist:

1. Re-run the fix after each reboot
2. Consider creating a custom init.d script for automatic application at boot
3. Use a Magisk module for persistent modifications on rooted devices

### Insufficient permissions error

If you encounter permission errors, manually grant the required permissions via ADB:

```bash
adb shell pm grant com.logkiller android.permission.READ_LOGS
adb shell pm grant com.logkiller android.permission.WRITE_EXTERNAL_STORAGE
```

## Build Instructions

### Prerequisites

- Android Studio Arctic Fox (2020.3.1) or later
- JDK 11 or later
- Android SDK Platform 33
- Android SDK Build-Tools 33.0.0 or later

### Build Process

Execute the following command from the project root directory:

```bash
./gradlew assembleDebug
```

For a release build:

```bash
./gradlew assembleRelease
```

The generated APK files will be located at:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release-unsigned.apk`

## Project Structure

```
LogKiller/
├── app/
│   ├── src/main/
│   │   ├── java/com/logkiller/
│   │   │   └── MainActivity.kt
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml
│   │   │   ├── values/colors.xml
│   │   │   ├── values/strings.xml
│   │   │   └── drawable/
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── README.md
```

## License

This project is provided as-is for educational and troubleshooting purposes. Use at your own risk.

## Disclaimer

Modifying system properties can potentially affect system stability and behavior. While this fix has been tested on multiple Android TV devices, results may vary depending on device manufacturer, Android version, and custom ROM implementations.

The authors and contributors are not responsible for any damage, data loss, or system instability resulting from the use of this application. Users assume all risks associated with system modifications.

It is strongly recommended to backup important data before making any system-level changes.

## Version History

### Version 1.0

- Initial release
- Root and ADB execution paths
- Storage analysis and reporting
- State persistence
- TV-optimized user interface
