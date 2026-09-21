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

public class MeterDetector {

    private static final String TAG = "MeterDetector";

    private static final String MODEL_PATH =
            "models/water_meter_v1.onnx";

    private static final int INPUT_SIZE = 640;

    private static final float CONFIDENCE_THRESHOLD = 0.25f;

    private final OrtEnvironment env;
    private OrtSession session;

    private volatile boolean initialized = false;
    private volatile boolean closing = false;

    public MeterDetector(Context context) {

        OrtEnvironment tempEnv = null;

        try {

            Log.d(TAG, "========== ONNX INITIALIZATION ==========");
            Log.d(TAG, "Loading model: " + MODEL_PATH);

            tempEnv = OrtEnvironment.getEnvironment();
            env = tempEnv;

            InputStream is =
                    context.getAssets().open(MODEL_PATH);

            ByteArrayOutputStream baos =
                    new ByteArrayOutputStream();

            byte[] buffer = new byte[8192];
            int len;

            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }

            is.close();

            byte[] modelBytes =
                    baos.toByteArray();

            Log.d(
                    TAG,
                    "Model size: "
                            + modelBytes.length
                            + " bytes"
            );

            session =
                    env.createSession(modelBytes);

            initialized = true;

            Log.d(
                    TAG,
                    "ONNX MODEL LOADED SUCCESSFULLY"
            );

            try {

                Log.d(
                        TAG,
                        "Input names: "
                                + session.getInputNames()
                );

                Log.d(
                        TAG,
                        "Output names: "
                                + session.getOutputNames()
                );

            } catch (Exception e) {

                Log.w(
                        TAG,
                        "Cannot read model IO names",
                        e
                );
            }

            Log.d(TAG, "========================================");

        } catch (Exception e) {

            initialized = false;

            if (tempEnv != null) {

                try {
                    tempEnv.close();
                } catch (Exception ignored) {
                }
            }

            Log.e(
                    TAG,
                    "FAILED TO LOAD ONNX MODEL",
                    e
            );

            throw new RuntimeException(
                    "Failed to load ONNX model",
                    e
            );
        }
    }


    public RectF detect(Bitmap bitmap) {

        if (!initialized) {

            Log.w(
                    TAG,
                    "detect(): detector is NOT initialized"
            );

            return null;
        }

        if (closing) {

            Log.w(
                    TAG,
                    "detect(): detector is closing"
            );

            return null;
        }

        if (bitmap == null) {

            Log.w(
                    TAG,
                    "detect(): bitmap is NULL"
            );

            return null;
        }

        OnnxTensor inputTensor = null;
        OrtSession.Result results = null;
        Bitmap resized = null;

        try {

            Log.d(
                    TAG,
                    "----------------------------------------"
            );

            Log.d(
                    TAG,
                    "detect(): image = "
                            + bitmap.getWidth()
                            + " x "
                            + bitmap.getHeight()
            );

            if (closing) {
                return null;
            }

            resized =
                    Bitmap.createScaledBitmap(
                            bitmap,
                            INPUT_SIZE,
                            INPUT_SIZE,
                            true
                    );

            float[][][][] inputArray =
                    preprocess(resized);

            if (closing) {
                return null;
            }

            inputTensor =
                    OnnxTensor.createTensor(
                            env,
                            inputArray
                    );

            if (closing) {
                return null;
            }

            Log.d(
                    TAG,
                    "Running ONNX inference..."
            );

            results =
                    session.run(
                            Collections.singletonMap(
                                    "images",
                                    inputTensor
                            )
                    );

            if (results == null ||
                    results.size() == 0) {

                Log.e(
                        TAG,
                        "ONNX returned NO RESULTS"
                );

                return null;
            }

            Log.d(
                    TAG,
                    "ONNX results count: "
                            + results.size()
            );

            Object value =
                    results.get(0).getValue();

            if (value == null) {

                Log.e(
                        TAG,
                        "ONNX output value is NULL"
                );

                return null;
            }

            Log.d(
                    TAG,
                    "ONNX output Java type: "
                            + value.getClass().getName()
            );

            if (!(value instanceof float[][][])) {

                Log.e(
                        TAG,
                        "Unexpected ONNX output type: "
                                + value.getClass().getName()
                );

                return null;
            }

            float[][][] output =
                    (float[][][]) value;

            if (output.length == 0 ||
                    output[0] == null) {

                Log.e(
                        TAG,
                        "ONNX output is EMPTY"
                );

                return null;
            }

            int channels =
                    output[0].length;

            int boxes =
                    channels > 0
                            ? output[0][0].length
                            : 0;

            Log.d(
                    TAG,
                    "ONNX OUTPUT SHAPE = ["
                            + output.length
                            + "]["
                            + channels
                            + "]["
                            + boxes
                            + "]"
            );

            if (channels < 5 ||
                    boxes <= 0) {

                Log.e(
                        TAG,
                        "INVALID OUTPUT SHAPE"
                );

                return null;
            }

            return postprocess(
                    output,
                    bitmap.getWidth(),
                    bitmap.getHeight()
            );

        } catch (Exception e) {

            if (!closing) {

                Log.e(
                        TAG,
                        "ONNX INFERENCE ERROR",
                        e
                );
            }

            return null;

        } finally {

            if (results != null) {

                try {
                    results.close();
                } catch (Exception e) {

                    Log.w(
                            TAG,
                            "Error closing results",
                            e
                    );
                }
            }

            if (inputTensor != null) {

                try {
                    inputTensor.close();
                } catch (Exception e) {

                    Log.w(
                            TAG,
                            "Error closing input tensor",
                            e
                    );
                }
            }

            if (resized != null &&
                    !resized.isRecycled()) {

                try {
                    resized.recycle();
                } catch (Exception ignored) {
                }
            }
        }
    }


    private float[][][][] preprocess(
            Bitmap bmp
    ) {

        float[][][][] result =
                new float[1][3][INPUT_SIZE][INPUT_SIZE];

        int[] pixels =
                new int[INPUT_SIZE * INPUT_SIZE];

        bmp.getPixels(
                pixels,
                0,
                INPUT_SIZE,
                0,
                0,
                INPUT_SIZE,
                INPUT_SIZE
        );

        for (int y = 0;
             y < INPUT_SIZE;
             y++) {

            for (int x = 0;
                 x < INPUT_SIZE;
                 x++) {

                int pixel =
                        pixels[
                                y * INPUT_SIZE + x
                        ];

                float r =
                        ((pixel >> 16) & 0xFF)
                                / 255.0f;

                float g =
                        ((pixel >> 8) & 0xFF)
                                / 255.0f;

                float b =
                        (pixel & 0xFF)
                                / 255.0f;

                result[0][0][y][x] = r;
                result[0][1][y][x] = g;
                result[0][2][y][x] = b;
            }
        }

        return result;
    }


    private RectF postprocess(
            float[][][] output,
            int origW,
            int origH
    ) {

        float[][] data =
                output[0];

        int channels =
                data.length;

        int numBoxes =
                data[0].length;

        Log.d(
                TAG,
                "postprocess(): channels="
                        + channels
                        + " boxes="
                        + numBoxes
        );

        float highestRawScore =
                -Float.MAX_VALUE;

        float highestProbability =
                -Float.MAX_VALUE;

        int highestIndex = -1;

        int validScores = 0;

        for (int i = 0;
             i < numBoxes;
             i++) {

            float rawScore =
                    data[4][i];

            if (Float.isNaN(rawScore) ||
                    Float.isInfinite(rawScore)) {

                continue;
            }

            validScores++;

            float probability =
                    rawScore;

            if (probability < 0.0f ||
                    probability > 1.0f) {

                probability =
                        sigmoid(probability);
            }

            if (probability >
                    highestProbability) {

                highestProbability =
                        probability;

                highestRawScore =
                        rawScore;

                highestIndex =
                        i;
            }
        }

        Log.d(
                TAG,
                "Scores: valid="
                        + validScores
                        + "/"
                        + numBoxes
                        + " highestRaw="
                        + highestRawScore
                        + " highestProbability="
                        + highestProbability
                        + " index="
                        + highestIndex
        );

        if (highestIndex < 0) {

            Log.w(
                    TAG,
                    "MODEL RETURNED NO VALID SCORES"
            );

            return null;
        }

        float cx =
                data[0][highestIndex];

        float cy =
                data[1][highestIndex];

        float w =
                data[2][highestIndex];

        float h =
                data[3][highestIndex];

        Log.d(
                TAG,
                "BEST RAW BOX: "
                        + "cx=" + cx
                        + " cy=" + cy
                        + " w=" + w
                        + " h=" + h
        );

        /*
         * Проверяем, нормализованы ли координаты.
         */
        float maxCoordinate =
                Math.max(
                        Math.max(
                                Math.abs(cx),
                                Math.abs(cy)
                        ),
                        Math.max(
                                Math.abs(w),
                                Math.abs(h)
                        )
                );

        boolean normalized =
                maxCoordinate <= 1.5f;

        Log.d(
                TAG,
                "Coordinates normalized: "
                        + normalized
        );

        if (normalized) {

            cx *= INPUT_SIZE;
            cy *= INPUT_SIZE;
            w *= INPUT_SIZE;
            h *= INPUT_SIZE;

            Log.d(
                    TAG,
                    "Converted to 640 coordinates: "
                            + "cx=" + cx
                            + " cy=" + cy
                            + " w=" + w
                            + " h=" + h
            );
        }

        if (w <= 0 ||
                h <= 0) {

            Log.e(
                    TAG,
                    "INVALID BOX SIZE: "
                            + w
                            + " x "
                            + h
            );

            return null;
        }

        float left =
                cx - w / 2.0f;

        float top =
                cy - h / 2.0f;

        float right =
                cx + w / 2.0f;

        float bottom =
                cy + h / 2.0f;

        Log.d(
                TAG,
                "BOX IN 640x640: ["
                        + left
                        + ", "
                        + top
                        + ", "
                        + right
                        + ", "
                        + bottom
                        + "]"
        );

        float scaleX =
                (float) origW /
                        INPUT_SIZE;

        float scaleY =
                (float) origH /
                        INPUT_SIZE;

        left *= scaleX;
        right *= scaleX;

        top *= scaleY;
        bottom *= scaleY;

        left =
                Math.max(
                        0,
                        Math.min(
                                left,
                                origW
                        )
                );

        top =
                Math.max(
                        0,
                        Math.min(
                                top,
                                origH
                        )
                );

        right =
                Math.max(
                        0,
                        Math.min(
                                right,
                                origW
                        )
                );

        bottom =
                Math.max(
                        0,
                        Math.min(
                                bottom,
                                origH
                        )
                );

        Log.d(
                TAG,
                "FINAL BOX: ["
                        + left
                        + ", "
                        + top
                        + ", "
                        + right
                        + ", "
                        + bottom
                        + "]"
        );

        float finalWidth =
                right - left;

        float finalHeight =
                bottom - top;

        Log.d(
                TAG,
                "FINAL BOX SIZE: "
                        + finalWidth
                        + " x "
                        + finalHeight
        );

        if (right <= left ||
                bottom <= top) {

            Log.e(
                    TAG,
                    "INVALID FINAL BOX"
            );

            return null;
        }

        /*
         * Пока специально НЕ применяем
         * CONFIDENCE_THRESHOLD.
         *
         * Нам важно увидеть, что модель
         * реально выдаёт.
         */

        Log.d(
                TAG,
                "========== DETECTION ACCEPTED =========="
        );

        return new RectF(
                left,
                top,
                right,
                bottom
        );
    }


    private float sigmoid(float x) {

        if (x >= 0) {

            double z =
                    Math.exp(-x);

            return (float)
                    (1.0 / (1.0 + z));

        } else {

            double z =
                    Math.exp(x);

            return (float)
                    (z / (1.0 + z));
        }
    }


    public void close() {

        closing = true;
        initialized = false;

        if (session != null) {

            try {

                session.close();

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "Error closing ONNX session",
                        e
                );

            } finally {

                session = null;
            }
        }
    }
}