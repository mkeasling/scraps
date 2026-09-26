package me.keasling.chargelimit;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Settings;

import java.util.Map;
import java.util.TreeMap;

/**
 * Which system setting controls the charge limit, and the values that mean on/off.
 * Different OEMs use different keys, so this is user-configurable with presets.
 */
public final class LimitConfig {
    public static final String GLOBAL = "global";
    public static final String SECURE = "secure";
    public static final String SYSTEM = "system";

    /** {label, namespace, key, onValue, offValue} */
    public static final String[][] PRESETS = {
        {"Samsung (Protect battery)", GLOBAL, "protect_battery", "1", "0"},
        {"Pixel (Limit to 80%)", SECURE, "charge_optimization_mode", "1", "0"},
    };

    private static final String PREFS = "config";

    public String namespace;
    public String key;
    public String onValue;
    public String offValue;

    public static LimitConfig load(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        LimitConfig c = new LimitConfig();
        c.namespace = p.getString("namespace", null);
        c.key = p.getString("key", null);
        c.onValue = p.getString("on", "1");
        c.offValue = p.getString("off", "0");
        if (c.key == null) {
            c.applyDetectedPreset(ctx);
        }
        return c;
    }

    public void save(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("namespace", namespace)
            .putString("key", key)
            .putString("on", onValue)
            .putString("off", offValue)
            .apply();
    }

    /**
     * Picks the first preset whose key exists on this device, else guesses by manufacturer.
     * Not saved, so detection re-runs once the adb grant makes hidden settings readable.
     */
    private void applyDetectedPreset(Context ctx) {
        String[] chosen = "samsung".equalsIgnoreCase(android.os.Build.MANUFACTURER)
            ? PRESETS[0] : PRESETS[1];
        for (String[] preset : PRESETS) {
            if (read(ctx.getContentResolver(), preset[1], preset[2]) != null) {
                chosen = preset;
                break;
            }
        }
        applyPreset(chosen);
    }

    public void applyPreset(String[] preset) {
        namespace = preset[1];
        key = preset[2];
        onValue = preset[3];
        offValue = preset[4];
    }

    public boolean isConfigured() {
        return key != null && !key.isEmpty() && namespace != null;
    }

    public String currentValue(Context ctx) {
        return read(ctx.getContentResolver(), namespace, key);
    }

    public boolean isOn(Context ctx) {
        String v = currentValue(ctx);
        return v != null && v.equals(onValue);
    }

    /** Flips the setting and returns the new state (true = limit on). */
    public boolean toggle(Context ctx) {
        boolean newState = !isOn(ctx);
        write(ctx.getContentResolver(), namespace, key, newState ? onValue : offValue);
        return newState;
    }

    public String describe() {
        return namespace + "/" + key + "  (on=" + onValue + ", off=" + offValue + ")";
    }

    public static boolean hasPermission(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
            == PackageManager.PERMISSION_GRANTED;
    }

    public static String grantCommand(Context ctx) {
        return "adb shell pm grant " + ctx.getPackageName()
            + " android.permission.WRITE_SECURE_SETTINGS";
    }

    public static String read(ContentResolver cr, String ns, String key) {
        try {
            if (GLOBAL.equals(ns)) return Settings.Global.getString(cr, key);
            if (SECURE.equals(ns)) return Settings.Secure.getString(cr, key);
            if (SYSTEM.equals(ns)) return Settings.System.getString(cr, key);
        } catch (RuntimeException e) {
            // Some keys are unreadable on some builds; treat as missing.
        }
        return null;
    }

    public static void write(ContentResolver cr, String ns, String key, String value) {
        boolean ok;
        if (GLOBAL.equals(ns)) ok = Settings.Global.putString(cr, key, value);
        else if (SECURE.equals(ns)) ok = Settings.Secure.putString(cr, key, value);
        else if (SYSTEM.equals(ns)) ok = Settings.System.putString(cr, key, value);
        else throw new IllegalArgumentException("Unknown namespace: " + ns);
        if (!ok) throw new IllegalStateException("Could not write " + ns + "/" + key);
    }

    /** Every setting in a namespace, name -> value. */
    public static Map<String, String> listAll(ContentResolver cr, String ns) {
        Map<String, String> out = new TreeMap<String, String>();
        Cursor c = null;
        try {
            c = cr.query(Uri.parse("content://settings/" + ns),
                new String[] {"name", "value"}, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    out.put(c.getString(0), c.getString(1));
                }
            }
        } catch (RuntimeException e) {
            // Listing may be restricted; return whatever we got.
        } finally {
            if (c != null) c.close();
        }
        return out;
    }
}
