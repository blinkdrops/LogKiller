# LogKiller

Android TV utility application for adjusting persistent log buffer configuration on devices where `persist.logd.logpersistd.size` is set to values that may consume significant storage space.

## Overview

This application provides a user interface for inspecting and modifying the Android logging system's persistent buffer size configuration. Some Android TV device manufacturers configure the persistent log buffer (`persist.logd.logpersistd.size`) with values up to 512 MB, which can consume storage space on devices with limited internal memory.

Google updated the AOSP default to 60 MB in November 2024, but devices that do not receive regular system updates will retain their original configuration. This tool allows users to manually adjust this setting without requiring custom ROMs or waiting for manufacturer updates.

## Important Notes

### Does Your Device Need This?

This tool is only relevant if:
- Your device has `persist.logd.logpersistd.size` configured to a value larger than 60 MB
- Persistent logging is enabled (the `persist.logd.logpersistd.buffer` property is set)
- You are experiencing storage constraints on a device with limited internal memory

Many Android TV devices have persistent logging disabled by default, or already use reasonable buffer sizes. The application will detect your current configuration and inform you if no action is needed.

### Technical Limitations

- **Root Required for Automatic Application**: The app can automatically apply changes only on rooted devices. Without root, it generates ADB commands that must be executed manually from a computer.
  
- **ADB Property Restrictions**: On many retail Android devices, the standard ADB shell user cannot modify `persist.*` properties due to security restrictions. Even with ADB access, you may encounter "permission denied" errors when attempting to set these properties. This is intentional Android security behavior, not an app bug.

- **Property Persistence**: Some device manufacturers reset system properties during boot. Changes made by this tool may not persist across reboots on all devices. For permanent modifications on rooted devices, consider creating an init.d script or Magisk module.

- **No Remote Operations**: This application performs all operations locally on the device. It does not contact remote servers, collect telemetry, or transmit any data.

## System Requirements

- Android TV device running Android 7.0 (API 24) or higher
- For automatic fix: Root access with su binary available
- For manual fix: ADB debugging enabled and a computer with ADB tools
- Note: ADB method may not work on all devices due to property permission restrictions

## Installation

### Build from Source

1. Clone this repository
2. Open in Android Studio Arctic Fox or later
3. Sync Gradle dependencies
4. Build and install via USB or network ADB

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Sideload Pre-built APK

1. Download the latest APK from releases
2. Enable "Unknown Sources" in Android TV security settings
3. Install using a file manager or via ADB:

```bash
adb install logkiller.apk
```

## Usage

### With Root Access

1. Launch LogKiller on your Android TV device
2. The app displays current log buffer size and folder usage
3. Press "FIX NOW" to apply changes automatically
4. The app executes:
   - `setprop persist.logd.logpersistd.size 60`
   - `setprop logd.logpersistd.size 60`
   - `rm -rf /data/misc/logd/*` (clear existing logs)
   - `logcat -c` (clear RAM buffers)
5. Reboot recommended for full effect

### Without Root (ADB Method)

1. Launch LogKiller on your Android TV device
2. Press "FIX NOW"
3. Press "COPY ADB COMMANDS" to copy commands to clipboard
4. On your computer, connect to device and execute:

```bash
adb shell setprop persist.logd.logpersistd.size 60
adb shell setprop logd.logpersistd.size 60
adb shell rm -rf /data/misc/logd/*
adb shell logcat -c
```

5. Reboot your device

**Note**: If you receive "permission denied" errors, your device does not allow ADB to modify persist properties. This is expected behavior on many retail devices. Root access would be required for automatic application.

## Features

- Reads current log buffer configuration via reflection with shell fallbacks
- Displays actual disk usage of `/data/misc/logd/` directory
- Shows available and total storage information
- Dual execution paths (root automatic, ADB manual commands)
- Copy-pasteable ADB command generation
- Restore defaults functionality (512 MB)
- State persistence across app restarts
- D-pad navigation optimized for Android TV remotes
- Landscape orientation locked for TV compatibility
- Comprehensive error handling with sanitized messages

## Technical Details

### Properties Modified

| Property | Typical Default | Target Value |
|----------|----------------|--------------|
| persist.logd.logpersistd.size | Varies by device (often 256-512) | 60 |
| logd.logpersistd.size | Varies by device | 60 |

### Storage Impact

- Before: Configured buffer size (varies, commonly 256-512 MB)
- After: 60 MB
- Actual savings depend on how much log data has accumulated

### Directories Affected

- `/data/misc/logd/` - System log storage directory (contents optionally cleared)

### Permissions

The app requests these permissions:

- `READ_LOGS` - System-level permission for log access (may require manual grant via ADB)
- `WRITE_EXTERNAL_STORAGE` - Storage analysis
- `READ_EXTERNAL_STORAGE` - Storage analysis
- `INTERNET` - Not currently used; reserved for future offline update verification

Note: `READ_LOGS` is a signature-level permission on modern Android versions. If not granted, the app falls back to shell commands for property reading.

## Troubleshooting

### "Logging is off. Nothing to fix."

Your device has persistent logging disabled via `persist.logd.logpersistd.buffer`. No action needed.

### "Device is already optimized"

Your current log limit is 60 MB or lower. No action needed.

### ADB commands fail with "permission denied"

Your device restricts ADB from modifying persist properties. This is normal security behavior on retail devices. Root access is required for automatic modification, or you may need to use a custom recovery or engineering bootloader.

### Fix doesn't persist after reboot

Some manufacturers reset properties at boot. Solutions:
- Re-run the fix after each reboot
- Create an init.d script (rooted devices)
- Use a Magisk module for persistent changes

### App shows error reading properties

Try granting permissions manually:

```bash
adb shell pm grant com.logkiller android.permission.READ_LOGS
```

If this fails, the permission is restricted on your device. The app will use shell command fallbacks.

## Build Instructions

### Prerequisites

- Android Studio Arctic Fox (2020.3.1) or later
- JDK 11 or later
- Android SDK Platform 33
- Android SDK Build-Tools 33.0.0+

### Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

Output locations:
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

## Security Considerations

This application:
- Executes only predefined shell commands
- Does not accept user input for command construction
- Sanitizes all error messages before display
- Does not transmit data externally
- Requires explicit user action to modify system properties
- Provides clear warnings about ADB limitations

## Disclaimer

Modifying system properties can affect device behavior and stability. While this tool uses standard Android commands and has been tested on multiple devices, results vary by manufacturer, Android version, and device configuration.

The authors provide this software as-is without warranty. Users assume all risks associated with system modifications. Backup important data before making changes.

This tool is intended for troubleshooting and educational purposes. Use responsibly and only on devices you own or have authorization to modify.

## Version History

### Version 1.0

- Initial release
- Root and ADB execution paths
- Storage analysis and reporting
- State persistence
- TV-optimized interface
- Comprehensive error handling
- Message sanitization for security
