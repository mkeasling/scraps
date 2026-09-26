package me.keasling.chargelimit;

import android.app.PendingIntent;
import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class ChargeLimitTileService extends TileService {

    @Override
    public void onStartListening() {
        refresh();
    }

    @Override
    public void onClick() {
        LimitConfig config = LimitConfig.load(this);
        if (!LimitConfig.hasPermission(this) || !config.isConfigured()) {
            openApp();
            return;
        }
        try {
            config.toggle(this);
        } catch (RuntimeException e) {
            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    private void refresh() {
        Tile tile = getQsTile();
        if (tile == null) return;
        LimitConfig config = LimitConfig.load(this);
        if (!LimitConfig.hasPermission(this) || !config.isConfigured()) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel(getString(R.string.tile_label));
            setSubtitle(tile, "Setup needed");
        } else {
            boolean on = config.isOn(this);
            tile.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setLabel(getString(R.string.tile_label));
            setSubtitle(tile, on ? "80% limit on" : "Charging to 100%");
        }
        tile.updateTile();
    }

    private static void setSubtitle(Tile tile, String text) {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            tile.setSubtitle(text);
        }
    }

    @SuppressWarnings("deprecation")
    private void openApp() {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            // The Intent overload throws for apps targeting API 34+.
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            startActivityAndCollapse(i);
        }
    }
}
