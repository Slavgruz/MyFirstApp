package com.example.myfirstapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

public class MeterDetector {

    private static final String TAG = "MeterDetector";
    private static final String MODEL_PATH = "models/water_meter_v1.onnx";
    private static final int INPUT_SIZE = 640;
    private static final float CONFIDENCE_THRESHOLD = 0.25f;

    private OrtEnvironment env;
    private OrtSession session;
    private boolean initialized = false;

    public MeterDetector(Context context) {
        try {
            env = OrtEnvironment.getEnvironment();

            InputStream is = context.getAssets().open(MODEL_PATH);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            is.close();
            byte[] modelBytes = baos.toByteArray();

            session = env.createSession(modelBytes);
            initialized = true;
            Log.d(TAG, "ONNX model loaded. Size: " + modelBytes.length + " bytes");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load ONNX model", e);
        }
    }

    public RectF detect(Bitmap bitmap) {
        if (!initialized || bitmap == null) return null;

        try {
            Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
            float[][][][] inputArray = preprocess(resized);
            resized.recycle();

            OnnxTensor inputTensor = OnnxTensor.createTensor(env, inputArray);

            // ИСПРАВЛЕНО: var заменён на явный тип
            OrtSession.Result results = session.run(Collections.singletonMap("images", inputTensor));
            float[][][] output = (float[][][]) results.get(0).getValue();

            inputTensor.close();
            results.close();

            return postprocess(output, bitmap.getWidth(), bitmap.getHeight());

        } catch (Exception e) {
            Log.e(TAG, "Inference error", e);
            return null;
        }
    }

    private float[][][][] preprocess(Bitmap bmp) {
        float[][][][] result = new float[1][3][INPUT_SIZE][INPUT_SIZE];
        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        bmp.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                int pixel = pixels[y * INPUT_SIZE + x];
                float r = ((pixel >> 16) & 0xFF) / 255.0f;
                float g = ((pixel >> 8) & 0xFF) / 255.0f;
                float b = (pixel & 0xFF) / 255.0f;
                result[0][0][y][x] = r;
                result[0][1][y][x] = g;
                result[0][2][y][x] = b;
            }
        }
        return result;
    }

    /**
     * ИСПРАВЛЕНО: YOLOv8 выводит [1][84][8400] где:
     * - 84 = 4 (bbox) + 80 (классы COCO)
     * - 8400 = количество anchor boxes
     * Структура: output[0][channel][anchor_index]
     */
    private RectF postprocess(float[][][] output, int origW, int origH) {
        float[][] data = output[0]; // [84][8400]
        int channels = data.length; // 84 для COCO
        int numBoxes = data[0].length; // 8400
        int numClasses = channels - 4; // 80 для COCO

        float bestScore = CONFIDENCE_THRESHOLD;
        int bestIdx = -1;
        int bestClass = -1;

        for (int i = 0; i < numBoxes; i++) {
            float maxClassScore = 0;
            int maxClassIdx = 0;
            
            // Ищем класс с максимальным score для этого anchor
            for (int c = 4; c < channels; c++) {
                float score = data[c][i]; // ИСПРАВЛЕНО: [channel][anchor]
                if (score > maxClassScore) {
                    maxClassScore = score;
                    maxClassIdx = c - 4;
                }
            }

            if (maxClassScore > bestScore) {
                bestScore = maxClassScore;
                bestIdx = i;
                bestClass = maxClassIdx;
            }
        }

        if (bestIdx == -1) {
            Log.d(TAG, "No detection above threshold " + CONFIDENCE_THRESHOLD);
            return null;
        }

        // ИСПРАВЛЕНО: извлекаем координаты в правильном порядке
        float cx = data[0][bestIdx]; // x_center
        float cy = data[1][bestIdx]; // y_center
        float w = data[2][bestIdx];  // width
        float h = data[3][bestIdx];  // height

        float scaleX = (float) origW / INPUT_SIZE;
        float scaleY = (float) origH / INPUT_SIZE;

        float left = (cx - w / 2f) * scaleX;
        float top = (cy - h / 2f) * scaleY;
        float right = (cx + w / 2f) * scaleX;
        float bottom = (cy + h / 2f) * scaleY;

        left = Math.max(0, Math.min(left, origW));
        top = Math.max(0, Math.min(top, origH));
        right = Math.max(0, Math.min(right, origW));
        bottom = Math.max(0, Math.min(bottom, origH));

        Log.d(TAG, "Detected: class=" + bestClass + " conf=" + bestScore
                + " box=[" + left + "," + top + "," + right + "," + bottom + "]");

        return new RectF(left, top, right, bottom);
    }

    public void close() {
        try {
            if (session != null) session.close();
            if (env != null) env.close();
        } catch (Exception e) {
            Log.e(TAG, "Error closing ONNX session", e);
        }
    }
}