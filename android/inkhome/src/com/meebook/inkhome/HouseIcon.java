package com.meebook.inkhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * A plain black house outline (roof, walls, door) for the Home Control button, matching SyncIcon:
 * drawn on the same 4:3 grid so the two sit side by side at the same size.
 */
final class HouseIcon extends View {
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    HouseIcon(Context c) {
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

        Path house = new Path();
        house.moveTo(7, 15);        // roof eaves left
        house.lineTo(20, 3);        // ridge
        house.lineTo(33, 15);       // eaves right
        house.moveTo(11, 12);       // walls
        house.lineTo(11, 27);
        house.lineTo(29, 27);
        house.lineTo(29, 12);
        house.moveTo(17, 27);       // door
        house.lineTo(17, 19);
        house.lineTo(23, 19);
        house.lineTo(23, 27);
        canvas.drawPath(house, stroke);
        canvas.restore();
    }
}
