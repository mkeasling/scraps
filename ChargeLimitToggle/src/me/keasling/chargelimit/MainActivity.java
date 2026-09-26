package me.keasling.chargelimit;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.StatusBarManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final String[] NAMESPACES = {
        LimitConfig.GLOBAL, LimitConfig.SECURE, LimitConfig.SYSTEM
    };
    private static final Pattern INTERESTING = Pattern.compile(
        "(?i).*(charg|protect_batt|battery_prot|batt.*limit|limit.*batt|battery_health).*");

    private LimitConfig config;
    private TextView statusView;
    private Button toggleButton;
    private EditText nsField, keyField, onField, offField;
    private LinearLayout resultsBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        config = LimitConfig.load(this);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    // ---------------------------------------------------------------- UI

    private void buildUi() {
        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        statusView = new TextView(this);
        statusView.setTextSize(16);
        statusView.setTextIsSelectable(true);
        root.addView(statusView);

        toggleButton = button(root, "Toggle", new View.OnClickListener() {
            public void onClick(View v) { doToggle(); }
        });
        button(root, "Copy adb grant command", new View.OnClickListener() {
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("adb", LimitConfig.grantCommand(MainActivity.this)));
                toast("Copied");
            }
        });
        if (Build.VERSION.SDK_INT >= 33) {
            button(root, "Add Quick Settings tile", new View.OnClickListener() {
                public void onClick(View v) { requestTile(); }
            });
        }
        if (Build.VERSION.SDK_INT >= 26) {
            button(root, "Add home-screen toggle shortcut", new View.OnClickListener() {
                public void onClick(View v) { requestShortcut(); }
            });
        }
        button(root, "Open system battery settings", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY));
            }
        });

        header(root, "Which setting to flip");
        nsField = field(root, "namespace (global / secure / system)", config.namespace);
        keyField = field(root, "setting key", config.key);
        onField = field(root, "value when limit is ON", config.onValue);
        offField = field(root, "value when limit is OFF", config.offValue);
        button(root, "Save", new View.OnClickListener() {
            public void onClick(View v) { saveFields(); }
        });
        for (final String[] preset : LimitConfig.PRESETS) {
            button(root, "Preset: " + preset[0], new View.OnClickListener() {
                public void onClick(View v) {
                    config.applyPreset(preset);
                    fillFields();
                    saveFields();
                }
            });
        }

        header(root, "Find the setting on this phone");
        note(root, "1. Tap Snapshot.  2. Tap 'Open system battery settings' and flip the "
            + "80% / battery-protection option.  3. Come back and tap 'Show changes'. "
            + "Tap a result to use it.");
        button(root, "1. Snapshot all settings", new View.OnClickListener() {
            public void onClick(View v) { takeSnapshot(); }
        });
        button(root, "3. Show changes since snapshot", new View.OnClickListener() {
            public void onClick(View v) { showDiff(); }
        });
        button(root, "List charging-related settings", new View.OnClickListener() {
            public void onClick(View v) { showInteresting(); }
        });
        resultsBox = new LinearLayout(this);
        resultsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultsBox);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        if (Build.VERSION.SDK_INT >= 30) {
            // Targeting API 35+ forces edge-to-edge; keep content clear of the system bars.
            scroll.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    Insets i = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    v.setPadding(i.left, i.top, i.right, i.bottom);
                    return insets;
                }
            });
        }
        setContentView(scroll);
    }

    private void refreshStatus() {
        StringBuilder sb = new StringBuilder();
        boolean perm = LimitConfig.hasPermission(this);
        if (!perm) {
            sb.append("⚠ Permission not granted yet. Connect to a computer with USB debugging "
                + "enabled and run once:\n\n")
              .append(LimitConfig.grantCommand(this)).append("\n\n");
        }
        if (config.isConfigured()) {
            String v = config.currentValue(this);
            sb.append("Setting: ").append(config.describe()).append('\n')
              .append("Current value: ").append(v == null ? "(not set / not present)" : v).append('\n')
              .append("Charge limit is ").append(config.isOn(this) ? "ON" : "OFF");
        } else {
            sb.append("No setting configured.");
        }
        statusView.setText(sb.toString());
        toggleButton.setEnabled(perm && config.isConfigured());
        toggleButton.setText(config.isOn(this) ? "Turn limit OFF" : "Turn limit ON");
        TileService.requestListeningState(this,
            new ComponentName(this, ChargeLimitTileService.class));
    }

    // ---------------------------------------------------------------- actions

    private void doToggle() {
        try {
            config.toggle(this);
        } catch (RuntimeException e) {
            toast("Failed: " + e.getMessage());
        }
        refreshStatus();
    }

    private void saveFields() {
        config.namespace = nsField.getText().toString().trim().toLowerCase();
        config.key = keyField.getText().toString().trim();
        config.onValue = onField.getText().toString().trim();
        config.offValue = offField.getText().toString().trim();
        config.save(this);
        toast("Saved");
        refreshStatus();
    }

    private void fillFields() {
        nsField.setText(config.namespace);
        keyField.setText(config.key);
        onField.setText(config.onValue);
        offField.setText(config.offValue);
    }

    private void requestTile() {
        StatusBarManager sbm = getSystemService(StatusBarManager.class);
        sbm.requestAddTileService(new ComponentName(this, ChargeLimitTileService.class),
            getString(R.string.tile_label), Icon.createWithResource(this, R.drawable.ic_tile),
            getMainExecutor(), new java.util.function.Consumer<Integer>() {
                public void accept(Integer result) { }
            });
    }

    private void requestShortcut() {
        ShortcutManager sm = getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            toast("Launcher doesn't support pinning; use the 'Toggle 80%' app icon instead");
            return;
        }
        Intent intent = new Intent(this, ToggleActivity.class).setAction(Intent.ACTION_MAIN);
        ShortcutInfo info = new ShortcutInfo.Builder(this, "toggle")
            .setShortLabel(getString(R.string.toggle_label))
            .setIcon(Icon.createWithResource(this, R.drawable.ic_toggle))
            .setIntent(intent)
            .build();
        sm.requestPinShortcut(info, null);
    }

    // ---------------------------------------------------------------- discovery

    private SharedPreferences snapshotPrefs() {
        return getSharedPreferences("snapshot", Context.MODE_PRIVATE);
    }

    private void takeSnapshot() {
        SharedPreferences.Editor e = snapshotPrefs().edit().clear();
        int n = 0;
        for (String ns : NAMESPACES) {
            for (Map.Entry<String, String> s : LimitConfig.listAll(getContentResolver(), ns).entrySet()) {
                e.putString(ns + "/" + s.getKey(), s.getValue() == null ? "\u0000" : s.getValue());
                n++;
            }
        }
        e.apply();
        resultsBox.removeAllViews();
        note(resultsBox, "Snapshot of " + n + " settings saved. Now change the battery "
            + "protection option in system settings, then come back.");
    }

    private void showDiff() {
        Map<String, ?> before = snapshotPrefs().getAll();
        resultsBox.removeAllViews();
        if (before.isEmpty()) {
            note(resultsBox, "Take a snapshot first.");
            return;
        }
        int found = 0;
        for (String ns : NAMESPACES) {
            Map<String, String> now = LimitConfig.listAll(getContentResolver(), ns);
            for (Map.Entry<String, String> s : now.entrySet()) {
                Object old = before.get(ns + "/" + s.getKey());
                String oldV = old == null || "\u0000".equals(old) ? null : (String) old;
                String newV = s.getValue();
                if (oldV == null ? newV != null : !oldV.equals(newV)) {
                    addDiffRow(ns, s.getKey(), oldV, newV);
                    found++;
                }
            }
        }
        if (found == 0) {
            note(resultsBox, "No changes detected. This phone may store the option somewhere "
                + "apps can't see (then only root could flip it).");
        }
    }

    private void addDiffRow(final String ns, final String key, final String before, final String after) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(ns + "/" + key + "\n" + before + "  →  " + after);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle(ns + "/" + key)
                    .setMessage("Is the 80% limit ON now (value " + after + ") or was it ON "
                        + "before (value " + before + ")?")
                    .setPositiveButton("ON now", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { use(ns, key, after, before); }
                    })
                    .setNegativeButton("ON before", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { use(ns, key, before, after); }
                    })
                    .show();
            }
        });
        resultsBox.addView(b);
    }

    private void showInteresting() {
        resultsBox.removeAllViews();
        int found = 0;
        for (String ns : NAMESPACES) {
            for (Map.Entry<String, String> s : LimitConfig.listAll(getContentResolver(), ns).entrySet()) {
                if (!INTERESTING.matcher(s.getKey()).matches()) continue;
                final String n = ns, k = s.getKey(), v = s.getValue();
                Button b = new Button(this);
                b.setAllCaps(false);
                b.setText(ns + "/" + k + " = " + v);
                b.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        nsField.setText(n);
                        keyField.setText(k);
                        toast("Filled in key; set the ON/OFF values and tap Save");
                    }
                });
                resultsBox.addView(b);
                found++;
            }
        }
        if (found == 0) note(resultsBox, "Nothing matched.");
    }

    private void use(String ns, String key, String onValue, String offValue) {
        config.namespace = ns;
        config.key = key;
        config.onValue = onValue == null ? "" : onValue;
        config.offValue = offValue == null ? "" : offValue;
        config.save(this);
        fillFields();
        toast("Now using " + ns + "/" + key);
        refreshStatus();
    }

    // ---------------------------------------------------------------- helpers

    private Button button(LinearLayout parent, String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(l);
        parent.addView(b);
        return b;
    }

    private EditText field(LinearLayout parent, String hint, String value) {
        TextView label = new TextView(this);
        label.setText(hint);
        parent.addView(label);
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setText(value);
        parent.addView(e);
        return e;
    }

    private void header(LinearLayout parent, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(24), 0, dp(8));
        parent.addView(t);
    }

    private void note(LinearLayout parent, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setPadding(0, dp(8), 0, dp(8));
        parent.addView(t);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
