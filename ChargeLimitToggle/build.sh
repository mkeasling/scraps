#!/usr/bin/env bash
# Builds ChargeLimitToggle.apk without the Android SDK/Gradle.
# Needs (Ubuntu): apt install aapt zipalign apksigner dalvik-exchange android-sdk-platform-23 openjdk-17-jdk
# plus a full framework jar for compiling (API 24+ classes like TileService):
#   ANDROID_ALL_JAR=path/to/android-all-16-robolectric-13921718.jar  (Maven Central, org.robolectric:android-all)
set -euo pipefail
cd "$(dirname "$0")"

PLATFORM_JAR=${PLATFORM_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
: "${ANDROID_ALL_JAR:?set ANDROID_ALL_JAR to an org.robolectric:android-all jar}"
KEYSTORE=${KEYSTORE:-debug.keystore}
OUT=build

rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes"

if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias chargelimit -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Charge Limit Toggle" >/dev/null
fi

# Resources + manifest -> R.java and a resource-only APK
aapt package -f -m -J "$OUT/gen" -M AndroidManifest.xml -S res -I "$PLATFORM_JAR" \
  -F "$OUT/unaligned.apk"

# android-all supplies android.* up to API 36; the API 23 jar supplies java.*;
# stubs/ fills in the few java.* classes newer than API 23 (compile-only).
mkdir -p "$OUT/stubs"
javac -source 8 -target 8 -nowarn -Xlint:-options -bootclasspath "$PLATFORM_JAR" \
  -d "$OUT/stubs" $(find stubs -name '*.java')
javac -source 8 -target 8 -nowarn -Xlint:-options \
  -bootclasspath "$ANDROID_ALL_JAR:$PLATFORM_JAR:$OUT/stubs" -d "$OUT/classes" \
  $(find src "$OUT/gen" -name '*.java')

dalvik-exchange --dex --output="$OUT/classes.dex" "$OUT/classes"
(cd "$OUT" && aapt add unaligned.apk classes.dex >/dev/null)

zipalign -f 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out ChargeLimitToggle.apk "$OUT/aligned.apk"
apksigner verify ChargeLimitToggle.apk
echo "Built $(pwd)/ChargeLimitToggle.apk"
