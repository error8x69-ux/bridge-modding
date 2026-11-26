package com.gtbridge;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import java.io.DataInputStream;
import java.io.IOException;

final class CockpitTitleView extends View {
    private static final float CONTENT_WIDTH = 418f;
    private static final float CONTENT_HEIGHT = 633f;
    private static final float TITLE_X = 48f;
    private static final float TITLE_BASELINE = 30f;
    private static final float TITLE_WIDTH = 215f;
    private final View instrument;
    private final String title;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    static final class Config {
        final String title;
        final int color;
        final int textPercent;

        private Config(String title, int color, int textPercent) {
            this.title = title;
            this.color = color;
            this.textPercent = textPercent;
        }

        static Config read(DataInputStream stream) throws IOException {
            String title = stream.readUTF();
            int color = stream.readInt();
            int textPercent = stream.readUnsignedByte();
            if (title.isEmpty() || title.length() > 32 || textPercent < 90 || textPercent > 110) {
                throw new IOException("Invalid cockpit title");
            }
            return new Config(title, color, textPercent);
        }
    }

    CockpitTitleView(View instrument, Config config) {
        super(instrument.getContext());
        this.instrument = instrument;
        title = config.title;
        paint.setColor(config.color);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        float size = 22f * config.textPercent / 100;
        paint.setTextSize(size);
        float width = paint.measureText(title);
        if (width > TITLE_WIDTH) paint.setTextSize(size * TITLE_WIDTH / width);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (instrument.getVisibility() != VISIBLE) return;
        int width = instrument.getWidth();
        int height = instrument.getHeight();
        float scale = Math.min(width / CONTENT_WIDTH, height / CONTENT_HEIGHT);
        if (scale <= 0) return;
        float x = instrument.getLeft() - getLeft() + (width - CONTENT_WIDTH * scale) / 2;
        float y = instrument.getTop() - getTop() + (height - CONTENT_HEIGHT * scale) / 2;
        int saved = canvas.save();
        canvas.translate(x, y);
        canvas.scale(scale, scale);
        canvas.drawText(title, TITLE_X, TITLE_BASELINE, paint);
        canvas.restoreToCount(saved);
    }
}
