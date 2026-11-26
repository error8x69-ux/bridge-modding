package com.gtbridge;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.app.CoreComponentFactory;

import java.io.IOException;
import java.io.DataInputStream;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class CloneStatusFactory extends CoreComponentFactory {
    private static final int HIDE_STATUS_BAR = 1;
    private static final int HOLD_SETTINGS = 2;
    private static final int CUSTOM_GRID = 4;
    private static final int RESTRICTED_TRIPLE_TAP = 8;

    @Override
    public Application instantiateApplication(ClassLoader loader, String className)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        Application application = super.instantiateApplication(loader, className);
        application.registerActivityLifecycleCallbacks(new CockpitCallbacks());
        return application;
    }

    private static final class CockpitCallbacks implements Application.ActivityLifecycleCallbacks {
        private final Set<View> watched = Collections.newSetFromMap(new WeakHashMap<>());
        private final Set<View> instrumentViews = Collections.newSetFromMap(new WeakHashMap<>());
        private final Set<View> gridAttempts = Collections.newSetFromMap(new WeakHashMap<>());
        private int options;
        private boolean optionsLoaded;
        private ReorderedCockpit.Config grid;

        @Override
        public void onActivityResumed(Activity activity) {
            if (!optionsLoaded) {
                optionsLoaded = true;
                try (DataInputStream stream = new DataInputStream(activity.getAssets().open("bridge-modding/options.bin"))) {
                    int flags = stream.read();
                    if (flags >= 0) {
                        options = flags;
                        if ((options & CUSTOM_GRID) != 0) grid = ReorderedCockpit.Config.read(stream);
                    }
                } catch (IOException unavailable) {
                    android.util.Log.w("BridgeModding", "Cockpit options unavailable", unavailable);
                }
            }
            if (options == 0) return;
            Window window = activity.getWindow();
            View decor = window.getDecorView();
            boolean hideStatus = (options & HIDE_STATUS_BAR) != 0;
            if (watched.add(decor)) {
                if (hideStatus) {
                    decor.getViewTreeObserver().addOnWindowFocusChangeListener(focused -> {
                        if (focused) hideStatusBar(window);
                    });
                }
                if (grid != null || (options & (HOLD_SETTINGS | RESTRICTED_TRIPLE_TAP)) != 0) {
                    decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> installCockpitOptions(activity, decor));
                }
            }
            if (hideStatus) {
                decor.post(() -> {
                    if (decor.hasWindowFocus()) hideStatusBar(window);
                });
            }
            installCockpitOptions(activity, decor);
        }

        private void installCockpitOptions(Activity activity, View root) {
            View cockpit = root.findViewWithTag("fixedCockpit030");
            if (cockpit == null) return;
            View instrument = cockpit.findViewWithTag("cockpitInstrument030");
            if (instrument == null || !(instrument.getParent() instanceof FrameLayout)) return;
            FrameLayout frame = (FrameLayout) instrument.getParent();
            if (grid != null && gridAttempts.add(instrument)) {
                View trip = cockpit.findViewWithTag("cockpitTripReset");
                try {
                    ReorderedCockpit display = new ReorderedCockpit(instrument, trip, grid);
                    frame.addView(display, 1, new FrameLayout.LayoutParams(-1, -1));
                    if (trip != null) trip.setVisibility(View.INVISIBLE);
                } catch (ReflectiveOperationException unavailable) {
                    android.util.Log.w("BridgeModding", "Cockpit readings unavailable", unavailable);
                }
            }
            if ((options & (HOLD_SETTINGS | RESTRICTED_TRIPLE_TAP)) != 0 && instrumentViews.add(instrument)) {
                installGestures(activity, instrument, frame);
            }
        }

        private void installGestures(Activity activity, View instrument, FrameLayout frame) {
            View search = (options & HOLD_SETTINGS) == 0 ? null : findSearch(frame);
            View.OnClickListener restricted = null;
            if ((options & RESTRICTED_TRIPLE_TAP) != 0) {
                try {
                    restricted = new RestrictedProfileTap(activity);
                } catch (ReflectiveOperationException unavailable) {
                    android.util.Log.w("BridgeModding", "Restricted profile action unavailable", unavailable);
                }
            }
            if (restricted == null && search == null) return;
            View.OnClickListener tap = restricted;
            GestureDetector gestures = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent event) {
                    return true;
                }
                @Override public boolean onSingleTapUp(MotionEvent event) {
                    if (tap != null) tap.onClick(instrument);
                    return true;
                }
                @Override public void onLongPress(MotionEvent event) {
                    if (search != null) search.performClick();
                }
            });
            gestures.setOnDoubleTapListener(null);
            gestures.setIsLongpressEnabled(search != null);
            instrument.setOnTouchListener((view, event) -> {
                gestures.onTouchEvent(event);
                return true;
            });
        }

        private View findSearch(FrameLayout frame) {
            int headerIndex = frame.getChildAt(1) instanceof ReorderedCockpit ? 2 : 1;
            if (!(frame.getChildAt(headerIndex) instanceof LinearLayout)) return null;
            LinearLayout actions = (LinearLayout) frame.getChildAt(headerIndex);
            View search = actions.getChildAt(0);
            return search != null && search.hasOnClickListeners() ? search : null;
        }

        @Override public void onActivityCreated(Activity activity, Bundle state) {}
        @Override public void onActivityStarted(Activity activity) {}
        @Override public void onActivityPaused(Activity activity) {}
        @Override public void onActivityStopped(Activity activity) {}
        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
        @Override public void onActivityDestroyed(Activity activity) {}
    }

    @SuppressWarnings("deprecation")
    private static void hideStatusBar(Window window) {
        if (Build.VERSION.SDK_INT >= 30) {
            InsetsApi30.hide(window);
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    private static final class InsetsApi30 {
        static void hide(Window window) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.statusBars());
            }
        }
    }
}
