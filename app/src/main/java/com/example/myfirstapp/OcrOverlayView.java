package com.example.myfirstapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.List;

public class OcrOverlayView extends View {

    private Paint borderPaint;
    private Paint textPaint;
    private List<Rect> boxes;
    private List<String> texts;
    private float scaleX = 1f;
    private float scaleY = 1f;

    public OcrOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        borderPaint = new Paint();
        borderPaint.setColor(Color.GREEN);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f);
        borderPaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(Color.GREEN);
        textPaint.setTextSize(24f);
        textPaint.setAntiAlias(true);
    }

    public void setScale(float scaleX, float scaleY) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    public void setData(List<Rect> boxes, List<String> texts) {
        this.boxes = boxes;
        this.texts = texts;
        invalidate();
    }

    public void clear() {
        this.boxes = null;
        this.texts = null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (boxes == null || texts == null) return;

        for (int i = 0; i < boxes.size(); i++) {
            Rect box = boxes.get(i);
            // Масштабируем координаты из пространства изображения в пространство View
            float left = box.left * scaleX;
            float top = box.top * scaleY;
            float right = box.right * scaleX;
            float bottom = box.bottom * scaleY;

            canvas.drawRect(left, top, right, bottom, borderPaint);

            if (i < texts.size()) {
                canvas.drawText(texts.get(i), left, top - 8, textPaint);
            }
        }
    }
}