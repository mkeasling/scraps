package me.keasling.chargelimit;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Home-screen icon: flips the charge limit and immediately closes. */
public class ToggleActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LimitConfig config = LimitConfig.load(this);
        if (!LimitConfig.hasPermission(this) || !config.isConfigured()) {
            startActivity(new Intent(this, MainActivity.class));
        } else {
            try {
                boolean on = config.toggle(this);
                Toast.makeText(this, on ? "Charge limit ON (80%)" : "Charge limit OFF (100%)",
                    Toast.LENGTH_SHORT).show();
            } catch (RuntimeException e) {
                Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        finish();
    }
}
