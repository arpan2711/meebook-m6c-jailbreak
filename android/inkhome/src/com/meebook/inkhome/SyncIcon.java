package com.meebook.inkhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * A plain black cloud outline with an up/down arrow pair inside, drawn so it stays crisp on e-ink.
 * (Deliberately not circular arrows: the status bar's refresh button already uses that shape.)
 * Drawn on a 4:3 grid, so give the view a 4:3 size.
 */
final class SyncIcon extends View {
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    SyncIcon(Context c) {
        super(c);
        stroke.setColor(Color.BLACK);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float u = Math.min(getWidth() / 40f, getHeight() / 30f);  // one grid unit
        float ox = (getWidth() - 40 * u) / 2, oy = (getHeight() - 30 * u) / 2;
        canvas.save();
        canvas.translate(ox, oy);
        canvas.scale(u, u);
        stroke.setStrokeWidth(2.4f);

        // cloud: flat base, small bump on the left, big bump on top, medium bump on the right
        Path cloud = new Path();
        cloud.moveTo(10, 27);
        cloud.arcTo(new RectF(2, 13, 16, 27), 90, 160);
        cloud.arcTo(new RectF(9, 3, 29, 23), 195, 150);
        cloud.arcTo(new RectF(24, 11, 38, 27), 285, 165);
        cloud.close();
        canvas.drawPath(cloud, stroke);

        // up arrow (left) and down arrow (right)
        drawArrow(canvas, 16.5f, 24, 13, true);
        drawArrow(canvas, 23.5f, 13, 24, false);
        canvas.restore();
    }

    private void drawArrow(Canvas c, float x, float from, float to, boolean up) {
        c.drawLine(x, from, x, to, stroke);
        float d = up ? 3.2f : -3.2f;
        c.drawLine(x, to, x - 3.2f, to + d, stroke);
        c.drawLine(x, to, x + 3.2f, to + d, stroke);
    }
}
