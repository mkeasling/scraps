# Charge Limit Toggle

A tiny Android app that flips the phone's built-in "limit charging to 80%"
(battery protection) option on/off from:

- a **Quick Settings tile** ("Charge limit") — highlighted when the limit is on, and
- a **home-screen icon** ("Toggle 80%") that flips it and shows a toast.

It doesn't control charging itself; it flips the same system setting the
Settings app does. Writing that setting needs `WRITE_SECURE_SETTINGS`, which a
normal app can't get on its own, so you grant it once over adb (no root needed).

## Install

1. Download `ChargeLimitToggle.apk` onto the phone and open it (allow "install
   unknown apps" for your browser/files app when asked).
2. Grant the permission once from a computer with
   [platform-tools](https://developer.android.com/tools/releases/platform-tools)
   and USB debugging on (Settings → About phone → tap Build number 7× →
   Developer options → USB debugging):

   ```
   adb shell pm grant me.keasling.chargelimit android.permission.WRITE_SECURE_SETTINGS
   ```

   (Wireless debugging works too.) The grant survives reboots and app updates;
   only an uninstall removes it.
3. Open **Charge Limit**. It auto-picks a preset for your phone:
   - Samsung: `global/protect_battery` (1 = on, 0 = off)
   - Pixel: `secure/charge_optimization_mode` (1 = limit to 80%, 0 = adaptive) — *unverified guess*
4. Check it's right: tap **Toggle** in the app and confirm the system
   Settings → Battery screen changes. If not, use **Find the setting on this
   phone** below.
5. Tap **Add Quick Settings tile** (Android 13+; otherwise edit the Quick
   Settings panel and drag "Charge limit" in) and/or use the **Toggle 80%**
   app icon / **Add home-screen toggle shortcut**.

## Find the setting on this phone

OEMs name this setting differently (and change it between OS versions), so
the app can discover it:

1. Tap **Snapshot all settings**.
2. Tap **Open system battery settings** and turn the 80%/protection option on
   or off.
3. Come back, tap **Show changes since snapshot**, and tap the entry that
   changed. Say whether the limit is on *now* or was on *before*; the app
   stores those as the on/off values.

**List charging-related settings** shows keys whose names look relevant; you
can also type a namespace/key/values in manually and tap **Save**.

If nothing changes in step 3, the phone keeps the option somewhere apps can't
reach, and this approach won't work there without root.

## Build

No Gradle/Android Studio needed; see `build.sh` (Ubuntu packages `aapt`,
`zipalign`, `apksigner`, `dalvik-exchange`, `android-sdk-platform-23`, a JDK,
and an `org.robolectric:android-all` jar from Maven Central for API 24+ classes):

```
ANDROID_ALL_JAR=~/android-all-16-robolectric-13921718.jar ./build.sh
```

`debug.keystore` (password `android`) is committed so rebuilt APKs install as
updates over the existing one.
