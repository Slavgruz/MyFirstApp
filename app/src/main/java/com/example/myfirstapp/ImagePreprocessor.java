package com.example.myfirstapp;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Легковесная предобработка изображений — Java-аналог OpenCV.
 * Улучшает распознавание ML Kit без добавления тяжелых зависимостей.
 */
public class ImagePreprocessor {

    /**
     * Полный pipeline предобработки для счетчиков
     */
    public static Bitmap process(Bitmap input) {
        Bitmap gray = toGrayscale(input);
        Bitmap enhanced = applyCLAHE(gray);
        Bitmap sharpened = applySharpen(enhanced);
        
        if (gray != input) gray.recycle();
        if (enhanced != gray) enhanced.recycle();
        
        return sharpened;
    }

    /**
     * Перевод в grayscale
     */
    private static Bitmap toGrayscale(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        Bitmap dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = src.getPixel(x, y);
                int gray = (int)(0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p));
                dst.setPixel(x, y, Color.rgb(gray, gray, gray));
            }
        }
        return dst;
    }

    /**
     * Упрощенный CLAHE (Contrast Limited Adaptive Histogram Equalization)
     * Выравнивает гистограмму локально — спасает при неравномерном освещении
     */
    private static Bitmap applyCLAHE(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        Bitmap dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        
        int tileSize = 32;
        int tilesX = (w + tileSize - 1) / tileSize;
        int tilesY = (h + tileSize - 1) / tileSize;
        
        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                int startX = tx * tileSize;
                int startY = ty * tileSize;
                int endX = Math.min(startX + tileSize, w);
                int endY = Math.min(startY + tileSize, h);
                
                int[] hist = new int[256];
                int count = 0;
                
                for (int y = startY; y < endY; y++) {
                    for (int x = startX; x < endX; x++) {
                        hist[Color.red(src.getPixel(x, y))]++;
                        count++;
                    }
                }
                
                int[] cdf = new int[256];
                cdf[0] = hist[0];
                for (int i = 1; i < 256; i++) cdf[i] = cdf[i-1] + hist[i];
                
                int cdfMin = 0;
                for (int i = 0; i < 256; i++) {
                    if (cdf[i] > 0) { cdfMin = cdf[i]; break; }
                }
                
                for (int y = startY; y < endY; y++) {
                    for (int x = startX; x < endX; x++) {
                        int p = Color.red(src.getPixel(x, y));
                        int newVal = (int)(((cdf[p] - cdfMin) * 255.0f) / (count - cdfMin));
                        if (newVal < 0) newVal = 0;
                        if (newVal > 255) newVal = 255;
                        dst.setPixel(x, y, Color.rgb(newVal, newVal, newVal));
                    }
                }
            }
        }
        return dst;
    }

    /**
     * Sharpen фильтр — подчеркивает края цифр
     * Ядро:  0 -1  0
     *       -1  5 -1
     *        0 -1  0
     */
    private static Bitmap applySharpen(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        Bitmap dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int center = Color.red(src.getPixel(x, y));
                int top = Color.red(src.getPixel(x, y - 1));
                int bottom = Color.red(src.getPixel(x, y + 1));
                int left = Color.red(src.getPixel(x - 1, y));
                int right = Color.red(src.getPixel(x + 1, y));
                
                int val = 5 * center - top - bottom - left - right;
                if (val < 0) val = 0;
                if (val > 255) val = 255;
                
                dst.setPixel(x, y, Color.rgb(val, val, val));
            }
        }
        
        // Копируем края
        for (int x = 0; x < w; x++) {
            dst.setPixel(x, 0, src.getPixel(x, 0));
            dst.setPixel(x, h - 1, src.getPixel(x, h - 1));
        }
        for (int y = 0; y < h; y++) {
            dst.setPixel(0, y, src.getPixel(0, y));
            dst.setPixel(w - 1, y, src.getPixel(w - 1, y));
        }
        
        return dst;
    }
}