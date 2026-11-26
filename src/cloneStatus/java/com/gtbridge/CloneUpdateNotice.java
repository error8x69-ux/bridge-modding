package com.gtbridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.Method;

final class CloneUpdateNotice {
    private static final long CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L;
    private static final long RETRY_INTERVAL_MS = 5 * 60 * 1000L;
    private Activity foreground;
    private AlertDialog dialog;
    private OriginalBridgeUpdates.Release pending;
    private boolean checking;
    private long nextAttempt;

    void resume(Activity activity) {
        if (!"de.smartrepair.smartbridge.MainActivity".equals(activity.getClass().getName()) || demoActive(activity)) return;
        foreground = activity;
        showPending();
        if (checking || SystemClock.elapsedRealtime() < nextAttempt) return;
        SharedPreferences preferences = activity.getSharedPreferences("bridge-modding-updates", Context.MODE_PRIVATE);
        long code;
        try {
            code = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).getLongVersionCode();
        } catch (android.content.pm.PackageManager.NameNotFoundException unavailable) {
            return;
        }
        long last = preferences.getLong("checkedAt", 0);
        long now = System.currentTimeMillis();
        if (preferences.getLong("checkedBuild", 0) == code && last <= now && now - last < CHECK_INTERVAL_MS) return;
        checking = true;
        new Thread(() -> {
            OriginalBridgeUpdates.Release release;
            try {
                release = OriginalBridgeUpdates.latest();
            } catch (Exception unavailable) {
                Log.d("BridgeModding", "Original update check unavailable", unavailable);
                activity.runOnUiThread(() -> {
                    checking = false;
                    nextAttempt = SystemClock.elapsedRealtime() + RETRY_INTERVAL_MS;
                });
                return;
            }
            activity.runOnUiThread(() -> {
                checking = false;
                preferences.edit().putLong("checkedAt", System.currentTimeMillis()).putLong("checkedBuild", code).apply();
                pending = release != null && release.code > code ? release : null;
                showPending();
            });
        }, "bridge-modding-update").start();
    }

    void pause(Activity activity) {
        if (foreground != activity) return;
        foreground = null;
        if (dialog != null) dialog.dismiss();
    }

    private void showPending() {
        Activity activity = foreground;
        OriginalBridgeUpdates.Release release = pending;
        if (activity == null || release == null || dialog != null || activity.isFinishing()
                || activity.isDestroyed() || demoActive(activity)) {
            return;
        }
        dialog = new AlertDialog.Builder(activity)
            .setTitle("Update für GT Mod Bridge")
            .setMessage("Version " + release.name + " ist verfügbar. Bridge Modding übernimmt deine bisherigen Mods.")
            .setPositiveButton("Mit Mods aktualisieren", (notice, button) -> {
                pending = null;
                try {
                    activity.startActivity(new Intent(OriginalBridgeUpdates.OPEN_UPDATE)
                        .setClassName(OriginalBridgeUpdates.PATCHER_PACKAGE,
                            OriginalBridgeUpdates.PATCHER_PACKAGE + ".PatcherActivity")
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                } catch (ActivityNotFoundException unavailable) {
                    Toast.makeText(activity, "Installiere die aktuelle Version von Bridge Modding.", Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("Später", (notice, button) -> pending = null)
            .setOnCancelListener(notice -> pending = null)
            .create();
        dialog.setOnDismissListener(notice -> {
            dialog = null;
            showPending();
        });
        dialog.show();
    }

    private static boolean demoActive(Activity activity) {
        try {
            Class<?> isolation = activity.getClassLoader().loadClass("de.smartrepair.smartbridge.GtVescDemoIsolation");
            Method active = isolation.getDeclaredMethod("active");
            active.setAccessible(true);
            return Boolean.TRUE.equals(active.invoke(null));
        } catch (ReflectiveOperationException unavailable) {
            return false;
        }
    }
}
