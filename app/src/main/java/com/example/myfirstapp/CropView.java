package com.example.myfirstapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

public class CropView extends View {

    private Bitmap bitmap;
    private RectF cropRect; // Область рамки в координатах bitmap
    private RectF displayRect; // Область отображения bitmap на экране
    
    private Paint bitmapPaint;
    private Paint overlayPaint;
    private Paint borderPaint;
    private Paint handlePaint;
    
    private static final float HANDLE_SIZE = 40f;
    private static final float BORDER_WIDTH = 4f;
    
    private int touchMode = TOUCH_NONE;
    private static final int TOUCH_NONE = 0;
    private static final int TOUCH_MOVE = 1;
    private static final int TOUCH_TOP_LEFT = 2;
    private static final int TOUCH_TOP_RIGHT = 3;
    private static final int TOUCH_BOTTOM_LEFT = 4;
    private static final int TOUCH_BOTTOM_RIGHT = 5;
    
    private float lastTouchX, lastTouchY;
    
    public CropView(Context context) {
        super(context);
        init();
    }
    
    public CropView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    private void init() {
        bitmapPaint = new Paint();
        bitmapPaint.setAntiAlias(true);
        
        overlayPaint = new Paint();
        overlayPaint.setColor(Color.BLACK);
        overlayPaint.setAlpha(150); // Полупрозрачный черный
        
        borderPaint = new Paint();
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(BORDER_WIDTH);
        borderPaint.setAntiAlias(true);
        
        handlePaint = new Paint();
        handlePaint.setColor(Color.WHITE);
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setAntiAlias(true);
        
        cropRect = new RectF();
        displayRect = new RectF();
    }
    
    public void setBitmap(Bitmap bmp) {
        this.bitmap = bmp;
        // Инициализируем рамку в центре bitmap (80% от размера)
        float w = bmp.getWidth();
        float h = bmp.getHeight();
        float cropW = w * 0.8f;
        float cropH = h * 0.5f;
        float left = (w - cropW) / 2;
        float top = (h - cropH) / 2;
        cropRect.set(left, top, left + cropW, top + cropH);
        
        invalidate();
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateDisplayRect();
    }
    
    private void updateDisplayRect() {
        if (bitmap == null) return;
        
        float viewW = getWidth();
        float viewH = getHeight();
        float bmpW = bitmap.getWidth();
        float bmpH = bitmap.getHeight();
        
        float scale = Math.min(viewW / bmpW, viewH / bmpH);
        float scaledW = bmpW * scale;
        float scaledH = bmpH * scale;
        
        float left = (viewW - scaledW) / 2;
        float top = (viewH - scaledH) / 2;
        
        displayRect.set(left, top, left + scaledW, top + scaledH);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (bitmap == null) return;
        
        // Рисуем bitmap
        canvas.drawBitmap(bitmap, null, displayRect, bitmapPaint);
        
        // Рисуем затемнение вне рамки
        // Верхняя область
        canvas.drawRect(0, 0, getWidth(), getCropRectOnScreen().top, overlayPaint);
        // Нижняя область
        canvas.drawRect(0, getCropRectOnScreen().bottom, getWidth(), getHeight(), overlayPaint);
        // Левая область
        canvas.drawRect(0, getCropRectOnScreen().top, getCropRectOnScreen().left, getCropRectOnScreen().bottom, overlayPaint);
        // Правая область
        canvas.drawRect(getCropRectOnScreen().right, getCropRectOnScreen().top, getWidth(), getCropRectOnScreen().bottom, overlayPaint);
        
        // Рисуем белую рамку
        canvas.drawRect(getCropRectOnScreen(), borderPaint);
        
        // Рисуем ручки по углам
        RectF screenRect = getCropRectOnScreen();
        canvas.drawCircle(screenRect.left, screenRect.top, HANDLE_SIZE / 2, handlePaint);
        canvas.drawCircle(screenRect.right, screenRect.top, HANDLE_SIZE / 2, handlePaint);
        canvas.drawCircle(screenRect.left, screenRect.bottom, HANDLE_SIZE / 2, handlePaint);
        canvas.drawCircle(screenRect.right, screenRect.bottom, HANDLE_SIZE / 2, handlePaint);
    }
    
    private RectF getCropRectOnScreen() {
        if (bitmap == null) return new RectF();
        
        float scaleX = displayRect.width() / bitmap.getWidth();
        float scaleY = displayRect.height() / bitmap.getHeight();
        
        float left = displayRect.left + cropRect.left * scaleX;
        float top = displayRect.top + cropRect.top * scaleY;
        float right = displayRect.left + cropRect.right * scaleX;
        float bottom = displayRect.top + cropRect.bottom * scaleY;
        
        return new RectF(left, top, right, bottom);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = x;
                lastTouchY = y;
                touchMode = getTouchMode(x, y);
                return true;
                
            case MotionEvent.ACTION_MOVE:
                if (touchMode != TOUCH_NONE) {
                    float dx = x - lastTouchX;
                    float dy = y - lastTouchY;
                    handleMove(dx, dy);
                    lastTouchX = x;
                    lastTouchY = y;
                    invalidate();
                }
                return true;
                
            case MotionEvent.ACTION_UP:
                touchMode = TOUCH_NONE;
                return true;
        }
        return false;
    }
    
    private int getTouchMode(float x, float y) {
        RectF screenRect = getCropRectOnScreen();
        float touchRadius = HANDLE_SIZE;
        
        // Проверяем углы
        if (Math.abs(x - screenRect.left) < touchRadius && Math.abs(y - screenRect.top) < touchRadius) {
            return TOUCH_TOP_LEFT;
        }
        if (Math.abs(x - screenRect.right) < touchRadius && Math.abs(y - screenRect.top) < touchRadius) {
            return TOUCH_TOP_RIGHT;
        }
        if (Math.abs(x - screenRect.left) < touchRadius && Math.abs(y - screenRect.bottom) < touchRadius) {
            return TOUCH_BOTTOM_LEFT;
        }
        if (Math.abs(x - screenRect.right) < touchRadius && Math.abs(y - screenRect.bottom) < touchRadius) {
            return TOUCH_BOTTOM_RIGHT;
        }
        
        // Проверяем, внутри ли рамки
        if (screenRect.contains(x, y)) {
            return TOUCH_MOVE;
        }
        
        return TOUCH_NONE;
    }
    
    private void handleMove(float dx, float dy) {
        float scaleX = bitmap.getWidth() / displayRect.width();
        float scaleY = bitmap.getHeight() / displayRect.height();
        
        float bmpDx = dx * scaleX;
        float bmpDy = dy * scaleY;
        
        float minSize = 100f; // Минимальный размер рамки в пикселях bitmap
        
        switch (touchMode) {
            case TOUCH_MOVE:
                // Перемещаем всю рамку
                float newLeft = cropRect.left + bmpDx;
                float newTop = cropRect.top + bmpDy;
                float width = cropRect.width();
                float height = cropRect.height();
                
                // Ограничиваем границами bitmap
                if (newLeft < 0) newLeft = 0;
                if (newTop < 0) newTop = 0;
                if (newLeft + width > bitmap.getWidth()) newLeft = bitmap.getWidth() - width;
                if (newTop + height > bitmap.getHeight()) newTop = bitmap.getHeight() - height;
                
                cropRect.set(newLeft, newTop, newLeft + width, newTop + height);
                break;
                
            case TOUCH_TOP_LEFT:
                cropRect.left = Math.max(0, Math.min(cropRect.left + bmpDx, cropRect.right - minSize));
                cropRect.top = Math.max(0, Math.min(cropRect.top + bmpDy, cropRect.bottom - minSize));
                break;
                
            case TOUCH_TOP_RIGHT:
                cropRect.right = Math.min(bitmap.getWidth(), Math.max(cropRect.right + bmpDx, cropRect.left + minSize));
                cropRect.top = Math.max(0, Math.min(cropRect.top + bmpDy, cropRect.bottom - minSize));
                break;
                
            case TOUCH_BOTTOM_LEFT:
                cropRect.left = Math.max(0, Math.min(cropRect.left + bmpDx, cropRect.right - minSize));
                cropRect.bottom = Math.min(bitmap.getHeight(), Math.max(cropRect.bottom + bmpDy, cropRect.top + minSize));
                break;
                
            case TOUCH_BOTTOM_RIGHT:
                cropRect.right = Math.min(bitmap.getWidth(), Math.max(cropRect.right + bmpDx, cropRect.left + minSize));
                cropRect.bottom = Math.min(bitmap.getHeight(), Math.max(cropRect.bottom + bmpDy, cropRect.top + minSize));
                break;
        }
    }
    
    public RectF getCropRect() {
        return new RectF(cropRect);
    }
}