#!/bin/bash

cd /home/ynqjzrjzrj/coding/android

echo "Cleaning Gradle locks..."
rm -f ~/.gradle/wrapper/dists/gradle-8.0-bin/*/gradle-8.0-bin.zip.lck

echo "Building APK..."
./gradlew clean assembleDebug --no-daemon --stacktrace

if [ $? -eq 0 ]; then
    echo "Build successful!"
    APK_PATH=$(find app/build/outputs/apk/debug -name "*.apk" | head -1)
    if [ -n "$APK_PATH" ]; then
        echo "APK found at: $APK_PATH"
        echo "Installing to device..."
        adb install -r "$APK_PATH"
        echo "Done!"
    else
        echo "APK not found!"
    fi
else
    echo "Build failed!"
fi
