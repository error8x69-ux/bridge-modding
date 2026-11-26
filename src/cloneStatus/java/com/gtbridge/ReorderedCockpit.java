package com.gtbridge;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.gtbridge.neonpatcher.CockpitReading;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;

final class ReorderedCockpit extends FrameLayout {
    private final View instrument;
    private final Method shownNumber;
    private final Method decimal;
    private final Method phaseScale;
    private final Field dual;
    private final Config config;
    private final LinearLayout grid;
    private final Tile[] tiles;
    private final Runnable tick = this::updateReadings;
    private boolean failed;
    private float appliedScale = -1;

    static final class Config {
        final int background;
        final int foreground;
        final int accent;
        final int textPercent;
        final boolean hideTileBackground;
        final CockpitReading[] order;

        private Config(int background, int foreground, int accent, int textPercent, CockpitReading[] order,
                       boolean hideTileBackground) {
            this.background = background;
            this.foreground = foreground;
            this.accent = accent;
            this.textPercent = textPercent;
            this.order = order;
            this.hideTileBackground = hideTileBackground;
        }

        static Config read(DataInputStream stream, boolean hideTileBackground) throws IOException {
            int background = stream.readInt();
            int foreground = stream.readInt();
            int accent = stream.readInt();
            int textPercent = stream.readUnsignedByte();
            int count = stream.readUnsignedByte();
            if (textPercent < 90 || textPercent > 110 || count > 8) throw new IOException("Invalid cockpit grid");
            CockpitReading[] order = new CockpitReading[count];
            EnumSet<CockpitReading> used = EnumSet.noneOf(CockpitReading.class);
            try {
                for (int i = 0; i < count; i++) {
                    CockpitReading reading = CockpitReading.valueOf(stream.readUTF());
                    if (reading == CockpitReading.DRIVE || !used.add(reading)) throw new IOException("Invalid cockpit tile");
                    order[i] = reading;
                }
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Unknown cockpit tile", invalid);
            }
            return new Config(background, foreground, accent, textPercent, order, hideTileBackground);
        }
    }

    ReorderedCockpit(View instrument, View reset, Config config) throws ReflectiveOperationException {
        super(instrument.getContext());
        this.instrument = instrument;
        this.config = config;
        Class<?> panel = instrument.getClass();
        shownNumber = panel.getDeclaredMethod("shownNumber", String.class);
        decimal = panel.getDeclaredMethod("decimal", Double.class, int.class);
        phaseScale = panel.getDeclaredMethod("phaseScale", int.class);
        dual = panel.getDeclaredField("dual");
        shownNumber.setAccessible(true);
        decimal.setAccessible(true);
        phaseScale.setAccessible(true);
        dual.setAccessible(true);
        grid = new LinearLayout(getContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        tiles = new Tile[config.order.length];
        for (int i = 0; i < tiles.length; i += 2) {
            LinearLayout row = new LinearLayout(getContext());
            grid.addView(row, new LinearLayout.LayoutParams(-1, 0));
            for (int j = i; j < Math.min(i + 2, tiles.length); j++) {
                Tile tile = new Tile(getContext(), config.order[j], config);
                tiles[j] = tile;
                if (reset != null && reset.hasOnClickListeners()
                        && (tile.reading == CockpitReading.TRIP || tile.reading == CockpitReading.CONSUMPTION)) {
                    tile.setOnClickListener(view -> reset.performClick());
                    tile.setFocusable(true);
                }
                row.addView(tile, new LinearLayout.LayoutParams(0, -1, 1f));
            }
        }
        addView(grid, new FrameLayout.LayoutParams(0, -2));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        // The native instrument uses a centered 418 × 633 content area.
        float scale = Math.min(width / 418f, height / 633f);
        FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) grid.getLayoutParams();
        layout.topMargin = Math.round((height - 633 * scale) / 2 + 410 * scale);
        layout.width = Math.round(394 * scale);
        layout.leftMargin = (width - layout.width) / 2;
        if (scale != appliedScale) {
            appliedScale = scale;
            int rowHeight = Math.round(51 * scale);
            for (int i = 0; i < grid.getChildCount(); i++) {
                grid.getChildAt(i).getLayoutParams().height = rowHeight;
            }
            for (Tile tile : tiles) tile.applyScale(scale * config.textPercent / 100f, scale);
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (tiles.length > 0) post(tick);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private void updateReadings() {
        if (!isAttachedToWindow() || failed) return;
        if (isShown() && getWindowVisibility() == VISIBLE) {
            try {
                boolean both = dual.getBoolean(instrument);
                for (Tile tile : tiles) updateTile(tile, both);
            } catch (ReflectiveOperationException error) {
                failed = true;
                android.util.Log.w("BridgeModding", "Cockpit readings unavailable", error);
                for (Tile tile : tiles) tile.setValue(tile.reading.tileLabel, "—", 0);
                return;
            }
        }
        postDelayed(tick, 300);
    }

    private void updateTile(Tile tile, boolean dualDrive) throws ReflectiveOperationException {
        CockpitReading reading = tile.reading;
        if (reading.isDriveReading()) {
            Double rear = readNumber(reading.key + "0");
            Double front = dualDrive ? readNumber(reading.key + "1") : null;
            String value = dualDrive ? format(front, reading.precision) + " / " + format(rear, reading.precision)
                : format(rear, reading.precision);
            int progress = reading == CockpitReading.PHASE ? phaseProgress(rear, 0) : 0;
            if (dualDrive && reading == CockpitReading.PHASE) progress = Math.max(progress, phaseProgress(front, 1));
            tile.setValue(reading.tileLabel + (dualDrive ? " · V/H" : ""), value + " " + reading.unit, progress);
            return;
        }
        Double number = readNumber(reading.key);
        if (reading == CockpitReading.POWER && number != null) number /= 1_000;
        tile.setValue(reading.tileLabel, format(number, reading.precision) + " " + reading.unit, 0);
    }

    private Double readNumber(String key) throws ReflectiveOperationException {
        return (Double) shownNumber.invoke(instrument, key);
    }

    private String format(Double number, int precision) throws ReflectiveOperationException {
        return number == null ? "—" : (String) decimal.invoke(null, number, precision);
    }

    private int phaseProgress(Double number, int motor) throws ReflectiveOperationException {
        if (number == null) return 0;
        double limit = (Double) phaseScale.invoke(instrument, motor);
        return limit > 0 ? (int) Math.min(100, Math.round(100 * Math.abs(number) / limit)) : 0;
    }

    private static final class Tile extends LinearLayout {
        final CockpitReading reading;
        private final TextView label;
        private final TextView value;
        private final ProgressBar bar;
        private final GradientDrawable background;

        Tile(Context context, CockpitReading reading, Config config) {
            super(context);
            this.reading = reading;
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_VERTICAL);
            background = new GradientDrawable();
            background.setColor(config.hideTileBackground ? Color.TRANSPARENT
                : Color.argb(70, Color.red(config.accent), Color.green(config.accent), Color.blue(config.accent)));
            setBackground(background);
            label = newText(config.foreground);
            value = newText(config.foreground);
            value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            label.setText(reading.tileLabel);
            value.setText("—");
            addView(label);
            addView(value);
            bar = reading == CockpitReading.PHASE ? new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal) : null;
            if (bar != null) {
                bar.setMax(100);
                bar.setProgressTintList(ColorStateList.valueOf(config.accent));
                bar.setProgressBackgroundTintList(ColorStateList.valueOf(config.background));
                addView(bar, new LinearLayout.LayoutParams(-1, 1));
            }
        }

        private TextView newText(int color) {
            TextView text = new TextView(getContext());
            text.setTextColor(color);
            text.setIncludeFontPadding(false);
            text.setSingleLine(true);
            text.setEllipsize(TextUtils.TruncateAt.END);
            return text;
        }

        void applyScale(float font, float scale) {
            setPadding(Math.round(10 * scale), 0, Math.round(8 * scale), 0);
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(1, 12 * font));
            value.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(1, 19 * font));
            background.setCornerRadius(6 * scale);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) getLayoutParams();
            params.setMargins(Math.round(3 * scale), Math.round(3 * scale),
                Math.round(3 * scale), Math.round(3 * scale));
            if (bar != null) bar.getLayoutParams().height = Math.max(1, Math.round(3 * scale));
        }

        void setValue(String name, String text, int progress) {
            if (!TextUtils.equals(label.getText(), name)) label.setText(name);
            if (!TextUtils.equals(value.getText(), text)) value.setText(text);
            if (bar != null && bar.getProgress() != progress) bar.setProgress(progress);
        }
    }
}
