package com.gtbridge;

import android.app.Activity;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class RestrictedProfileTap implements View.OnClickListener {
    private static final int RESTRICTED_PROFILE = 0;
    private static final int UNRESTRICTED_PROFILE = 1;
    private final Activity activity;
    private final Method connected;
    private final Method available;
    private final Method updateBlocked;
    private final Method requestStatus;
    private final Method applyProfile;
    private final Field authenticated;
    private final Field demoMode;
    private final Field limitsDirty;
    private final Field detected;
    private final Field state;
    private long firstTap;
    private long lastTap;
    private int taps;
    private boolean requesting;

    RestrictedProfileTap(Activity activity) throws ReflectiveOperationException {
        this.activity = activity;
        Class<?> owner = activity.getClass();
        connected = method(owner, "connected");
        available = method(owner, "vcuProfileTransportAvailable");
        updateBlocked = method(owner, "firmwareUpdateTrafficBlocked");
        requestStatus = method(owner, "requestRfState");
        authenticated = field(owner, "authenticated");
        demoMode = field(owner, "demoMode");
        limitsDirty = field(owner, "gtLimitsDirty");
        detected = field(owner, "vcuProfileDetected");
        state = field(owner, "lastRfState");
        Method action = null;
        for (Method candidate : owner.getDeclaredMethods()) {
            if (!candidate.getName().startsWith("lambda$renderVcuProfile$")) continue;
            Class<?>[] args = candidate.getParameterTypes();
            if (args.length == 3 && args[0] == Button.class && args[1] == int.class && args[2] == View.class) {
                if (action != null) throw new NoSuchMethodException("Ambiguous VCU action");
                action = candidate;
            }
        }
        if (action == null) throw new NoSuchMethodException("VCU action");
        action.setAccessible(true);
        applyProfile = action;
    }

    @Override public void onClick(View view) {
        if (requesting) return;
        long now = SystemClock.uptimeMillis();
        if (taps == 0 || now - lastTap > 450 || now - firstTap > 750) {
            taps = 0;
            firstTap = now;
        }
        lastTap = now;
        if (++taps != 3) return;
        taps = 0;
        try {
            if (!canRequest()) {
                message("Gedrosseltes Profil derzeit nicht verfügbar.");
                return;
            }
            JSONObject previous = (JSONObject) state.get(activity);
            if (previous != null && previous.optBoolean("vcuProfilePending")) {
                message("Ein Profilwechsel läuft bereits.");
                return;
            }
            requestStatus.invoke(activity);
            requesting = true;
            awaitStatus(view, previous, now + 2_000);
        } catch (ReflectiveOperationException error) {
            failure(error);
        }
    }

    private void awaitStatus(View view, JSONObject previous, long deadline) {
        if (!activity.hasWindowFocus() || !view.isShown() || !view.isAttachedToWindow()) {
            requesting = false;
            return;
        }
        try {
            JSONObject current = (JSONObject) state.get(activity);
            if (current == null || current == previous) {
                if (SystemClock.uptimeMillis() < deadline) {
                    view.postDelayed(() -> awaitStatus(view, previous, deadline), 100);
                } else {
                    requesting = false;
                    message("Kein aktueller Profilstatus von der Bridge.");
                }
                return;
            }
            requesting = false;
            if (!canRequest() || !detected.getBoolean(activity) || !current.optBoolean("vcuProfileKnown")
                    || !current.optBoolean("vcuProfileSwitchable") || current.optBoolean("vcuProfilePending")) {
                message("Gedrosseltes Profil derzeit nicht verfügbar.");
                return;
            }
            int profile = current.optInt("vcuProfile", -1);
            if (profile == RESTRICTED_PROFILE) {
                message("Gedrosseltes Profil ist bereits aktiv.");
            } else if (profile == UNRESTRICTED_PROFILE) {
                applyProfile.invoke(activity, new Button(activity), RESTRICTED_PROFILE, view);
                message("Gedrosseltes Profil angefordert.");
            } else {
                message("Die Bridge meldet ein unbekanntes Fahrprofil.");
            }
        } catch (ReflectiveOperationException error) {
            failure(error);
        }
    }

    private boolean canRequest() throws ReflectiveOperationException {
        return !demoMode.getBoolean(activity) && authenticated.getBoolean(activity) && !limitsDirty.getBoolean(activity)
            && (Boolean) connected.invoke(activity) && (Boolean) available.invoke(activity)
            && !(Boolean) updateBlocked.invoke(activity);
    }

    private void failure(ReflectiveOperationException error) {
        requesting = false;
        android.util.Log.w("BridgeModding", "Restricted profile could not be requested", error);
        message("Gedrosseltes Profil konnte nicht angefordert werden.");
    }

    private void message(String text) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }

    private static Method method(Class<?> owner, String name) throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name);
        method.setAccessible(true);
        return method;
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
