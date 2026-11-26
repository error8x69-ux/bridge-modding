package com.gtbridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

final class CloneUpdateNotice {
    private static final long CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L;
    private static final long RETRY_INTERVAL_MS = 5 * 60 * 1000L;
    private final Set<View> watchedWindows = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<View> versionLinks = Collections.newSetFromMap(new WeakHashMap<>());
    private Activity foreground;
    private AlertDialog dialog;
    private AlertDialog manualDialog;
    private OriginalBridgeUpdates.Release pending;
    private boolean checking;
    private long nextAttempt;

    void resume(Activity activity) {
        if (!"de.smartrepair.smartbridge.MainActivity".equals(activity.getClass().getName())) return;
        PackageInfo info;
        try {
            info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException unavailable) {
            return;
        }
        foreground = activity;
        watchVersionLink(activity, info);
        if (demoActive(activity)) return;
        showPending();
        if (checking || SystemClock.elapsedRealtime() < nextAttempt) return;
        SharedPreferences preferences = activity.getSharedPreferences("bridge-modding-updates", Context.MODE_PRIVATE);
        long code = info.getLongVersionCode();
        long last = preferences.getLong("checkedAt", 0);
        long now = System.currentTimeMillis();
        if (preferences.getLong("checkedBuild", 0) == code && last <= now && now - last < CHECK_INTERVAL_MS) return;
        check(activity, code, preferences);
    }

    private void watchVersionLink(Activity activity, PackageInfo info) {
        if (TextUtils.isEmpty(info.versionName)) return;
        View decor = activity.getWindow().getDecorView();
        String label = "Version " + info.versionName;
        Runnable find = () -> {
            ArrayList<View> matches = new ArrayList<>();
            decor.findViewsWithText(matches, label, View.FIND_VIEWS_WITH_TEXT);
            for (View view : matches) {
                if (view instanceof TextView && TextUtils.equals(((TextView) view).getText(), label)
                        && view.hasOnClickListeners() && versionLinks.add(view)) {
                    view.setOnClickListener(clicked -> checkManually(activity, info.getLongVersionCode()));
                }
            }
        };
        if (watchedWindows.add(decor)) decor.getViewTreeObserver().addOnGlobalLayoutListener(find::run);
        find.run();
    }

    private void checkManually(Activity activity, long code) {
        if (foreground != activity || manualDialog != null) return;
        if (demoActive(activity)) {
            showManualStatus(activity, "Die Update-Prüfung ist im Offline-Demo pausiert.");
            return;
        }
        manualDialog = new AlertDialog.Builder(activity)
            .setTitle("Update für GT Mod Bridge")
            .setMessage("Suche nach einem Original-Update …")
            .setNegativeButton("Schließen", null)
            .create();
        manualDialog.setOnDismissListener(notice -> {
            if (manualDialog == notice) manualDialog = null;
        });
        manualDialog.show();
        if (!checking) {
            check(activity, code, activity.getSharedPreferences("bridge-modding-updates", Context.MODE_PRIVATE));
        }
    }

    private void check(Activity activity, long code, SharedPreferences preferences) {
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
                    finishManualCheck("Die Update-Prüfung ist fehlgeschlagen. Versuche es erneut.");
                });
                return;
            }
            activity.runOnUiThread(() -> {
                checking = false;
                preferences.edit().putLong("checkedAt", System.currentTimeMillis()).putLong("checkedBuild", code).apply();
                pending = release != null && release.code > code ? release : null;
                finishManualCheck(pending == null ? "Du hast bereits die aktuelle Version." : null);
                showPending();
            });
        }, "bridge-modding-update").start();
    }

    void pause(Activity activity) {
        if (foreground != activity) return;
        foreground = null;
        if (dialog != null) dialog.dismiss();
        if (manualDialog != null) manualDialog.dismiss();
    }

    private void finishManualCheck(String message) {
        if (manualDialog == null) return;
        AlertDialog progress = manualDialog;
        manualDialog = null;
        progress.dismiss();
        if (message != null && foreground != null) showManualStatus(foreground, message);
    }

    private void showManualStatus(Activity activity, String message) {
        manualDialog = new AlertDialog.Builder(activity)
            .setTitle("Update für GT Mod Bridge")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .create();
        manualDialog.setOnDismissListener(notice -> {
            if (manualDialog == notice) manualDialog = null;
        });
        manualDialog.show();
    }

    private void showPending() {
        Activity activity = foreground;
        OriginalBridgeUpdates.Release release = pending;
        if (activity == null || release == null || dialog != null || manualDialog != null || activity.isFinishing()
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
