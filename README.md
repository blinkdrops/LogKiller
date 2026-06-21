# LogKiller

Android TV application that resolves the persistent log storage bug on Android TV devices.

## Problem

On millions of Android TV devices, the system property `persist.logd.logpersistd.size` defaults to 512 MB. This reserves 512 MB of internal storage for persistent system logs. On devices with 4-8 GB of storage, this causes:

- Insufficient storage errors
- Failed OTA updates
- Degraded system performance

Google addressed this in AOSP (November 2024) by reducing the default to 60 MB, but most existing devices will never receive this update through official channels.

## Solution

LogKiller modifies the system properties at runtime to reduce the log buffer size from 512 MB to 60 MB, freeing approximately 452 MB of storage space.

## Requirements

- Android TV device running Android 7.0 (API 24) or higher
- Root access OR ADB debugging enabled

## Installation

### Method 1: Build from Source

1. Open the project in Android Studio
2. Sync Gradle files
3. Build and install on your Android TV device

### Method 2: Sideload APK

1. Download the APK from releases
2. Enable "Unknown Sources" on your Android TV
3. Install using a file manager or ADB:
   ```
   adb install logkiller.apk
   ```

## Usage

### With Root Access

1. Launch LogKiller on your Android TV
2. The app will automatically detect root access
3. Press "FIX NOW" to apply the fix
4. The app will:
   - Set `persist.logd.logpersistd.size` to 60
   - Set `logd.logpersistd.size` to 60 (trampoline workaround)
   - Clear existing log files in `/data/misc/logd/`
   - Clear RAM log buffers
5. Reboot your device for changes to take full effect

### Without Root (ADB Method)

1. Launch LogKiller on your Android TV
2. Press "FIX NOW"
3. The app will display ADB commands
4. Press "COPY ADB COMMANDS" to copy them to clipboard
5. On your computer, execute the commands:
   ```
   adb shell setprop persist.logd.logpersistd.size 60
   adb shell setprop logd.logpersistd.size 60
   adb shell rm -rf /data/misc/logd/*
   adb shell logcat -c
   ```
6. Reboot your device

## Features

- Automatic detection of current log buffer size
- Real-time storage analysis
- Root and ADB support
- One-click fix application
- Restore defaults option
- D-pad navigation optimized for Android TV
- Landscape orientation locked for TV compatibility
- State persistence across reboots

## Technical Details

### System Properties Modified

| Property | Default | Fixed Value |
|----------|---------|-------------|
| persist.logd.logpersistd.size | 512 | 60 |
| logd.logpersistd.size | 512 | 60 |

### Storage Impact

- Before: 512 MB reserved for logs
- After: 60 MB reserved for logs
- Savings: ~452 MB

### Files Modified

- `/data/misc/logd/` - Log directory (cleared during fix)

## Permissions

The app requests the following permissions:

- `READ_LOGS` - To read log properties (system-level, may be denied)
- `WRITE_EXTERNAL_STORAGE` - For storage calculation
- `READ_EXTERNAL_STORAGE` - For storage calculation
- `INTERNET` - Reserved for future update checks

## Troubleshooting

### App shows "Logging is off. Nothing to fix."

Persistent logging is already disabled on your device. No action needed.

### App shows "Device is already optimized"

Your device already has a log limit of 60 MB or lower. No action needed.

### Root command failed

If root commands fail, use the ADB method instead. Connect your device to a computer and execute the provided commands.

### Fix doesn't persist after reboot

Some devices may reset system properties on reboot. Re-run the fix after each reboot, or use a custom init.d script or Magisk module for persistence.

### Insufficient permissions error

Ensure you have granted all requested permissions. On some devices, you may need to grant permissions via ADB:
```
adb shell pm grant com.logkiller android.permission.READ_LOGS
```

## Building

### Prerequisites

- Android Studio Arctic Fox or later
- JDK 11 or later
- Android SDK 33

### Build Commands

```bash
./gradlew assembleDebug
```

The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`

## License

This project is provided as-is for educational and troubleshooting purposes. Use at your own risk.

## Disclaimer

Modifying system properties can potentially affect system stability. While this fix has been tested on multiple devices, results may vary. The authors are not responsible for any damage or data loss resulting from the use of this application.

Always backup important data before making system modifications.
