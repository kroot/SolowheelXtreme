cd \android_sdk\platform-tools
adb forward tcp:4444 localabstract:/adb-hub
adb connect localhost:4444