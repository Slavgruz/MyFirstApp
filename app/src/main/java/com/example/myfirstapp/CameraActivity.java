package com.example.myfirstapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG =
            "CameraActivity";

    public static final String EXTRA_PHOTO_PATH =
            "photo_path";

    public static final String EXTRA_OCR_TEXT =
            "ocr_text";

    /*
     * Минимальный интервал между Live OCR.
     * Это снижает нагрузку на ML Kit и ONNX.
     */
    private static final long LIVE_FRAME_INTERVAL_MS =
            300L;

    /*
     * Для текущего приложения ожидается 6 цифр целой части.
     * Оставляем небольшой диапазон, чтобы OCR мог предложить
     * альтернативный вариант.
     */
    private static final int MIN_DIGITS = 5;
    private static final int MAX_DIGITS = 8;

    private static final int MAX_LIVE_HISTORY = 10;

    private static final Pattern DIGIT_RUN_PATTERN =
            Pattern.compile(
                    "\\d{" +
                            MIN_DIGITS +
                            "," +
                            MAX_DIGITS +
                            "}"
            );


    private PreviewView previewView;
    private OcrOverlayView ocrOverlay;
    private ImageView ivPhotoPreview;

    private View layoutCapture;
    private View layoutPreview;

    private View btnCapture;
    private View btnCancel;
    private View btnRetake;
    private View btnRotate;
    private View btnConfirm;
    private View btnTorch;

    private ImageCapture imageCapture;
    private Camera camera;
    private ProcessCameraProvider cameraProvider;

    private ExecutorService cameraExecutor;

    private TextRecognizer liveTextRecognizer;
    private TextRecognizer photoTextRecognizer;

    private MeterDetector meterDetector;

    private volatile boolean shuttingDown =
            false;

    private final AtomicInteger pendingLiveOcr =
            new AtomicInteger(0);

    private final AtomicInteger pendingPhotoOcr =
            new AtomicInteger(0);

    private final AtomicBoolean liveOcrBusy =
            new AtomicBoolean(false);

    private final AtomicBoolean photoOcrBusy =
            new AtomicBoolean(false);

    private final AtomicBoolean liveRecognizerClosed =
            new AtomicBoolean(false);

    private final AtomicBoolean photoRecognizerClosed =
            new AtomicBoolean(false);

    private final AtomicLong lastLiveFrameTime =
            new AtomicLong(0L);

    private final Object liveHistoryLock =
            new Object();

    private final ArrayDeque<String> liveHistory =
            new ArrayDeque<>();

    private volatile String lastLiveRecognizedDigits =
            "";

    /*
     * Именно это значение передаётся в MainActivity.
     */
    private volatile String lastRecognizedDigits =
            "";

    private Bitmap lastCapturedBitmap;

    /*
     * Номер текущей фотографии.
     * Нужен, чтобы старый OCR не смог подменить
     * результат после пересъёмки или поворота.
     */
    private final AtomicInteger photoGeneration =
            new AtomicInteger(0);

    private boolean torchEnabled =
            false;


    // =========================================================
    // ON CREATE
    // =========================================================

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(
                savedInstanceState
        );


        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );


        setContentView(
                R.layout.activity_camera
        );


        shuttingDown = false;


        previewView =
                findViewById(
                        R.id.previewView
                );


        ocrOverlay =
                findViewById(
                        R.id.ocrOverlay
                );


        ivPhotoPreview =
                findViewById(
                        R.id.ivPhotoPreview
                );


        layoutCapture =
                findViewById(
                        R.id.layoutCapture
                );


        layoutPreview =
                findViewById(
                        R.id.layoutPreview
                );


        btnCapture =
                findViewById(
                        R.id.btnCapture
                );


        btnCancel =
                findViewById(
                        R.id.btnCancel
                );


        btnRetake =
                findViewById(
                        R.id.btnRetake
                );


        btnRotate =
                findViewById(
                        R.id.btnRotate
                );


        btnConfirm =
                findViewById(
                        R.id.btnConfirm
                );


        btnTorch =
                findViewById(
                        R.id.btnTorch
                );


        // =====================================================
        // ML KIT
        // =====================================================

        try {

            liveTextRecognizer =
                    TextRecognition.getClient(
                            TextRecognizerOptions.DEFAULT_OPTIONS
                    );

        } catch (Throwable e) {

            Log.e(
                    TAG,
                    "Ошибка создания Live TextRecognizer",
                    e
            );

            liveTextRecognizer = null;
        }


        try {

            photoTextRecognizer =
                    TextRecognition.getClient(
                            TextRecognizerOptions.DEFAULT_OPTIONS
                    );

        } catch (Throwable e) {

            Log.e(
                    TAG,
                    "Ошибка создания Photo TextRecognizer",
                    e
            );

            photoTextRecognizer = null;
        }


        // =====================================================
        // ONNX
        // =====================================================

        try {

            meterDetector =
                    new MeterDetector(
                            this
                    );

        } catch (Throwable e) {

            Log.e(
                    TAG,
                    "Ошибка создания MeterDetector",
                    e
            );

            meterDetector = null;
        }


        // =====================================================
        // EXECUTOR
        // =====================================================

        cameraExecutor =
                Executors.newSingleThreadExecutor();


        // =====================================================
        // CAPTURE
        // =====================================================

        if (btnCapture != null) {

            btnCapture.setOnClickListener(
                    v -> {

                        if (!shuttingDown) {

                            takePhotoAndReturn();
                        }
                    }
            );

        } else {

            Log.e(
                    TAG,
                    "btnCapture не найден"
            );
        }


        // =====================================================
        // CANCEL
        // =====================================================

        if (btnCancel != null) {

            btnCancel.setOnClickListener(
                    v -> {

                        if (shuttingDown) {
                            return;
                        }


                        setResult(
                                RESULT_CANCELED
                        );


                        finish();
                    }
            );
        }


        // =====================================================
        // RETAKE
        // =====================================================

        if (btnRetake != null) {

            btnRetake.setOnClickListener(
                    v -> {

                        if (shuttingDown) {
                            return;
                        }


                        if (photoOcrBusy.get()) {

                            Toast.makeText(
                                    CameraActivity.this,
                                    "Подождите завершения распознавания",
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }


                        prepareForRetake();
                    }
            );
        }


        // =====================================================
        // ROTATE
        // =====================================================

        if (btnRotate != null) {

            btnRotate.setOnClickListener(
                    v -> {

                        if (shuttingDown) {
                            return;
                        }


                        rotateCapturedPhoto();
                    }
            );
        }


        // =====================================================
        // CONFIRM
        // =====================================================

        if (btnConfirm != null) {

            btnConfirm.setOnClickListener(
                    v -> {

                        if (shuttingDown) {
                            return;
                        }


                        confirmPhoto();
                    }
            );
        }


        // =====================================================
        // TORCH
        // =====================================================

        if (btnTorch != null) {

            btnTorch.setOnClickListener(
                    v -> toggleTorch()
            );
        }


        // =====================================================
        // START CAMERA
        // =====================================================

        startCamera();
    }


    // =========================================================
    // START CAMERA
    // =========================================================

    private void startCamera() {

        if (shuttingDown) {
            return;
        }


        if (previewView == null) {

            Log.e(
                    TAG,
                    "previewView не найден"
            );

            return;
        }


        if (
                cameraExecutor == null ||
                        cameraExecutor.isShutdown()
        ) {

            Log.e(
                    TAG,
                    "cameraExecutor недоступен"
            );

            return;
        }


        ListenableFuture<ProcessCameraProvider>
                providerFuture =
                ProcessCameraProvider.getInstance(
                        this
                );


        providerFuture.addListener(
                () -> {

                    if (shuttingDown) {
                        return;
                    }


                    try {

                        cameraProvider =
                                providerFuture.get();


                        if (!shuttingDown) {

                            bindCamera(
                                    cameraProvider
                            );
                        }

                    } catch (Throwable e) {

                        Log.e(
                                TAG,
                                "Ошибка запуска камеры",
                                e
                        );


                        if (!shuttingDown) {

                            Toast.makeText(
                                    CameraActivity.this,
                                    "Не удалось запустить камеру",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }

                },
                ContextCompat.getMainExecutor(
                        this
                )
        );
    }


    // =========================================================
    // BIND CAMERA
    // =========================================================

    private void bindCamera(
            ProcessCameraProvider provider
    ) {

        if (shuttingDown) {
            return;
        }


        if (previewView == null) {
            return;
        }


        Preview preview =
                new Preview.Builder()
                        .build();


        preview.setSurfaceProvider(
                previewView.getSurfaceProvider()
        );


        imageCapture =
                new ImageCapture.Builder()
                        .setCaptureMode(
                                ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                        )
                        .build();


        ImageAnalysis analysis =
                new ImageAnalysis.Builder()
                        .setTargetResolution(
                                new Size(
                                        1280,
                                        720
                                )
                        )
                        .setBackpressureStrategy(
                                ImageAnalysis
                                        .STRATEGY_KEEP_ONLY_LATEST
                        )
                        .build();


        analysis.setAnalyzer(
                cameraExecutor,
                imageProxy -> {

                    if (shuttingDown) {

                        imageProxy.close();

                        return;
                    }


                    analyzeLiveFrame(
                            imageProxy
                    );
                }
        );


        try {

            provider.unbindAll();


            camera =
                    provider.bindToLifecycle(
                            this,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                            analysis
                    );


            if (
                    torchEnabled &&
                            camera != null
            ) {

                camera
                        .getCameraControl()
                        .enableTorch(
                                true
                        );
            }

        } catch (Throwable e) {

            Log.e(
                    TAG,
                    "Ошибка bind камеры",
                    e
            );


            if (!shuttingDown) {

                Toast.makeText(
                        this,
                        "Ошибка запуска камеры",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }


    // =========================================================
    // LIVE FRAME
    // =========================================================

    private void analyzeLiveFrame(
            ImageProxy imageProxy
    ) {

        if (shuttingDown) {

            imageProxy.close();

            return;
        }


        long now =
                SystemClock.elapsedRealtime();


        long previous =
                lastLiveFrameTime.get();


        if (
                now - previous <
                        LIVE_FRAME_INTERVAL_MS
        ) {

            imageProxy.close();

            return;
        }


        if (
                !lastLiveFrameTime.compareAndSet(
                        previous,
                        now
                )
        ) {

            imageProxy.close();

            return;
        }


        Bitmap originalBitmap =
                null;

        Bitmap cropBitmap =
                null;


        try {

            Bitmap tempBitmap =
                    imageProxy.toBitmap();


            if (tempBitmap == null) {

                imageProxy.close();

                return;
            }


            int rotation =
                    imageProxy
                            .getImageInfo()
                            .getRotationDegrees();


            originalBitmap =
                    rotateBitmap(
                            tempBitmap,
                            rotation
                    );


            if (originalBitmap == null) {

                recycleBitmap(
                        tempBitmap
                );

                imageProxy.close();

                return;
            }


            if (
                    originalBitmap !=
                            tempBitmap
            ) {

                recycleBitmap(
                        tempBitmap
                );
            }


            if (shuttingDown) {

                recycleBitmap(
                        originalBitmap
                );

                imageProxy.close();

                return;
            }


            RectF detectedBox =
                    null;


            if (
                    meterDetector != null &&
                            !shuttingDown
            ) {

                try {

                    detectedBox =
                            meterDetector.detect(
                                    originalBitmap
                            );

                } catch (Throwable e) {

                    Log.e(
                            TAG,
                            "Ошибка Live ONNX",
                            e
                    );
                }
            }


            cropBitmap =
                    buildOcrCrop(
                            originalBitmap,
                            detectedBox
                    );


            try {

                imageProxy.close();

            } catch (Exception ignored) {
            }


            imageProxy = null;


            if (cropBitmap == null) {

                recycleBitmap(
                        originalBitmap
                );

                return;
            }


            if (shuttingDown) {

                recycleBitmap(
                        cropBitmap
                );

                recycleBitmap(
                        originalBitmap
                );

                return;
            }


            if (
                    !liveOcrBusy.compareAndSet(
                            false,
                            true
                    )
            ) {

                recycleBitmap(
                        cropBitmap
                );

                recycleBitmap(
                        originalBitmap
                );

                return;
            }


            if (
                    liveTextRecognizer == null ||
                            liveRecognizerClosed.get()
            ) {

                liveOcrBusy.set(
                        false
                );


                recycleBitmap(
                        cropBitmap
                );


                recycleBitmap(
                        originalBitmap
                );


                return;
            }


            pendingLiveOcr.incrementAndGet();


            InputImage inputImage =
                    InputImage.fromBitmap(
                            cropBitmap,
                            0
                    );


            final Bitmap finalCrop =
                    cropBitmap;

            final Bitmap finalOriginal =
                    originalBitmap;


            try {

                liveTextRecognizer
                        .process(
                                inputImage
                        )

                        .addOnSuccessListener(
                                visionText -> {

                                    if (shuttingDown) {
                                        return;
                                    }


                                    List<String>
                                            candidates =
                                            extractCandidates(
                                                    visionText
                                            );


                                    if (
                                            !candidates.isEmpty()
                                    ) {

                                        addLiveCandidates(
                                                candidates
                                        );


                                        String best =
                                                chooseBestCandidate(
                                                        candidates
                                                );


                                        if (
                                                !best.isEmpty()
                                        ) {

                                            lastLiveRecognizedDigits =
                                                    best;


                                            setOcrStatus(
                                                    "LIVE: " +
                                                            best
                                            );
                                        }
                                    }
                                }
                        )

                        .addOnFailureListener(
                                error -> {

                                    if (
                                            !shuttingDown
                                    ) {

                                        Log.w(
                                                TAG,
                                                "Live OCR ошибка",
                                                error
                                        );
                                    }
                                }
                        )

                        .addOnCompleteListener(
                                task -> {

                                    pendingLiveOcr
                                            .decrementAndGet();


                                    liveOcrBusy.set(
                                            false
                                    );


                                    recycleBitmap(
                                            finalCrop
                                    );


                                    recycleBitmap(
                                            finalOriginal
                                    );


                                    closeRecognizersIfPossible();
                                }
                        );

            } catch (Throwable e) {

                pendingLiveOcr
                        .decrementAndGet();


                liveOcrBusy.set(
                        false
                );


                recycleBitmap(
                        finalCrop
                );


                recycleBitmap(
                        finalOriginal
                );


                Log.e(
                        TAG,
                        "Не удалось запустить Live OCR",
                        e
                );


                closeRecognizersIfPossible();
            }


        } catch (Throwable e) {

            Log.e(
                    TAG,
                    "Ошибка анализа Live кадра",
                    e
            );


            if (imageProxy != null) {

                try {

                    imageProxy.close();

                } catch (Exception ignored) {
                }
            }


            recycleBitmap(
                    cropBitmap
            );


            recycleBitmap(
                    originalBitmap
            );
        }
    }


    // =========================================================
    // TAKE PHOTO
    // =========================================================

    private void takePhotoAndReturn() {

        if (shuttingDown) {
            return;
        }


        if (imageCapture == null) {

            Toast.makeText(
                    this,
                    "Камера ещё не готова",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        if (
                cameraExecutor == null ||
                        cameraExecutor.isShutdown()
        ) {

            return;
        }


        imageCapture.takePicture(
                cameraExecutor,
                new ImageCapture.OnImageCapturedCallback() {

                    @Override
                    public void onCaptureSuccess(
                            @NonNull ImageProxy image
                    ) {

                        if (shuttingDown) {

                            image.close();

                            return;
                        }


                        Bitmap bitmap =
                                null;


                        try {

                            bitmap =
                                    image.toBitmap();


                            if (bitmap == null) {

                                image.close();

                                return;
                            }


                            int rotation =
                                    image
                                            .getImageInfo()
                                            .getRotationDegrees();


                            Bitmap rotated =
                                    rotateBitmap(
                                            bitmap,
                                            rotation
                                    );


                            if (rotated == null) {

                                recycleBitmap(
                                        bitmap
                                );

                                image.close();

                                return;
                            }


                            if (
                                    rotated != bitmap
                            ) {

                                recycleBitmap(
                                        bitmap
                                );
                            }


                            final Bitmap
                                    capturedBitmap =
                                    rotated;


                            final int generation =
                                    photoGeneration
                                            .incrementAndGet();


                            runOnUiThread(
                                    () -> {

                                        if (
                                                shuttingDown
                                        ) {

                                            recycleBitmap(
                                                    capturedBitmap
                                            );

                                            return;
                                        }


                                        showCapturedPhoto(
                                                capturedBitmap
                                        );
                                    }
                            );


                            /*
                             * Очень важно:
                             * распознаём именно настоящий снимок.
                             */
                            analyzeCapturedPhoto(
                                    capturedBitmap,
                                    generation
                            );


                        } catch (Throwable e) {

                            Log.e(
                                    TAG,
                                    "Ошибка обработки фотографии",
                                    e
                            );


                            if (bitmap != null) {

                                recycleBitmap(
                                        bitmap
                                );
                            }


                            if (!shuttingDown) {

                                runOnUiThread(
                                        () ->
                                                Toast.makeText(
                                                        CameraActivity.this,
                                                        "Ошибка обработки фотографии",
                                                        Toast.LENGTH_SHORT
                                                ).show()
                                );
                            }

                        } finally {

                            try {

                                image.close();

                            } catch (Exception ignored) {
                            }
                        }
                    }


                    @Override
                    public void onError(
                            @NonNull ImageCaptureException exception
                    ) {

                        Log.e(
                                TAG,
                                "Ошибка съёмки",
                                exception
                        );


                        if (!shuttingDown) {

                            runOnUiThread(
                                    () ->
                                            Toast.makeText(
                                                    CameraActivity.this,
                                                    "Не удалось сделать фото",
                                                    Toast.LENGTH_SHORT
                                            ).show()
                            );
                        }
                    }
                }
        );
    }


    // =========================================================
    // SHOW PHOTO
    // =========================================================

    private void showCapturedPhoto(
            Bitmap bitmap
    ) {

        if (shuttingDown) {
            return;
        }


        if (
                lastCapturedBitmap != null &&
                        lastCapturedBitmap != bitmap
        ) {

            recycleBitmap(
                    lastCapturedBitmap
            );
        }


        lastCapturedBitmap =
                bitmap;


        lastRecognizedDigits =
                "";


        if (ivPhotoPreview != null) {

            ivPhotoPreview.setImageBitmap(
                    bitmap
            );


            ivPhotoPreview.setVisibility(
                    View.VISIBLE
            );
        }


        if (layoutCapture != null) {

            layoutCapture.setVisibility(
                    View.GONE
            );
        }


        if (layoutPreview != null) {

            layoutPreview.setVisibility(
                    View.VISIBLE
            );
        }


        if (ocrOverlay != null) {

            ocrOverlay.clear();
        }


        setOcrStatus(
                "Анализ фотографии..."
        );


        if (cameraProvider != null) {

            try {

                cameraProvider.unbindAll();

            } catch (Throwable e) {

                Log.w(
                        TAG,
                        "Ошибка остановки камеры",
                        e
                );
            }
        }


        if (camera != null) {

            try {

                camera
                        .getCameraControl()
                        .enableTorch(
                                false
                        );

            } catch (Exception ignored) {
            }
        }


        torchEnabled =
                false;
    }


    // =========================================================
    // ANALYZE CAPTURED PHOTO
    // =========================================================

    private void analyzeCapturedPhoto(
            Bitmap capturedBitmap,
            int generation
    ) {

        if (
                capturedBitmap == null ||
                        capturedBitmap.isRecycled()
        ) {

            return;
        }


        if (shuttingDown) {
            return;
        }


        if (
                photoTextRecognizer == null ||
                        photoRecognizerClosed.get()
        ) {

            return;
        }


        if (
                !photoOcrBusy.compareAndSet(
                        false,
                        true
                )
        ) {

            return;
        }


        pendingPhotoOcr.incrementAndGet();


        /*
         * Снимок Live-истории делаем именно сейчас.
         */
        List<String> liveSnapshot =
                getLiveHistorySnapshot();


        Bitmap cropBitmap =
                null;


        try {

            RectF detectedBox =
                    null;


            if (
                    meterDetector != null &&
                            !shuttingDown
            ) {

                try {

                    detectedBox =
                            meterDetector.detect(
                                    capturedBitmap
                            );

                } catch (Throwable e) {

                    Log.e(
                            TAG,
                            "Ошибка YOLO на фотографии",
                            e
                    );
                }
            }


            cropBitmap =
                    buildOcrCrop(
                            capturedBitmap,
                            detectedBox
                    );


            if (cropBitmap == null) {

                finishPhotoOcr();

                return;
            }


            if (
                    shuttingDown ||
                            generation !=
                                    photoGeneration.get()
            ) {

                recycleBitmap(
                        cropBitmap
                );


                finishPhotoOcr();

                return;
            }


            final Bitmap finalCrop =
                    cropBitmap;


            InputImage inputImage =
                    InputImage.fromBitmap(
                            finalCrop,
                            0
                    );


            photoTextRecognizer
                    .process(
                            inputImage
                    )

                    .addOnSuccessListener(
                            visionText -> {

                                if (shuttingDown) {
                                    return;
                                }


                                if (
                                        generation !=
                                                photoGeneration.get()
                                ) {

                                    return;
                                }


                                List<String>
                                        photoCandidates =
                                        extractCandidates(
                                                visionText
                                        );


                                showComparedCandidates(
                                        liveSnapshot,
                                        photoCandidates
                                );
                            }
                    )

                    .addOnFailureListener(
                            error -> {

                                if (
                                        !shuttingDown &&
                                                generation ==
                                                        photoGeneration.get()
                                ) {

                                    Log.w(
                                            TAG,
                                            "Photo OCR ошибка",
                                            error
                                    );


                                    showComparedCandidates(
                                            liveSnapshot,
                                            Collections.emptyList()
                                    );
                                }
                            }
                    )

                    .addOnCompleteListener(
                            task -> {

                                recycleBitmap(
                                        finalCrop
                                );


                                finishPhotoOcr();
                            }
                    );


        } catch (Throwable e) {

            Log.e(
                    TAG,
                    "Ошибка анализа фотографии",
                    e
            );


            recycleBitmap(
                    cropBitmap
            );


            finishPhotoOcr();
        }
    }


    // =========================================================
    // BUILD OCR CROP
    // =========================================================

    private Bitmap buildOcrCrop(
            Bitmap source,
            RectF detectedBox
    ) {

        if (
                source == null ||
                        source.isRecycled()
        ) {

            return null;
        }


        int sourceWidth =
                source.getWidth();

        int sourceHeight =
                source.getHeight();


        if (
                sourceWidth <= 1 ||
                        sourceHeight <= 1
        ) {

            return null;
        }


        try {

            /*
             * YOLO box.
             */
            if (
                    detectedBox != null &&
                            detectedBox.width() > 50 &&
                            detectedBox.height() > 40
            ) {

                float boxWidth =
                        detectedBox.width();

                float boxHeight =
                        detectedBox.height();


                /*
                 * Запас вокруг найденной области.
                 */
                float marginX =
                        boxWidth * 0.12f;

                float marginY =
                        boxHeight * 0.12f;


                int left =
                        Math.round(
                                detectedBox.left -
                                        marginX
                        );


                int top =
                        Math.round(
                                detectedBox.top -
                                        marginY
                        );


                int right =
                        Math.round(
                                detectedBox.right +
                                        marginX
                        );


                int bottom =
                        Math.round(
                                detectedBox.bottom +
                                        marginY
                        );


                left =
                        Math.max(
                                0,
                                left
                        );


                top =
                        Math.max(
                                0,
                                top
                        );


                right =
                        Math.min(
                                sourceWidth,
                                right
                        );


                bottom =
                        Math.min(
                                sourceHeight,
                                bottom
                        );


                int width =
                        right -
                                left;

                int height =
                        bottom -
                                top;


                if (
                        width > 30 &&
                                height > 20
                ) {

                    return Bitmap.createBitmap(
                            source,
                            left,
                            top,
                            width,
                            height
                    );
                }
            }


            /*
             * Fallback:
             * центральная часть кадра.
             */
            int cropWidth =
                    Math.max(
                            1,
                            Math.round(
                                    sourceWidth *
                                            0.60f
                            )
                    );


            int cropHeight =
                    Math.max(
                            1,
                            Math.round(
                                    sourceHeight *
                                            0.35f
                            )
                    );


            int left =
                    Math.max(
                            0,
                            (
                                    sourceWidth -
                                            cropWidth
                            ) / 2
                    );


            int top =
                    Math.max(
                            0,
                            (
                                    sourceHeight -
                                            cropHeight
                            ) / 2
                    );


            cropWidth =
                    Math.min(
                            cropWidth,
                            sourceWidth -
                                    left
                    );


            cropHeight =
                    Math.min(
                            cropHeight,
                            sourceHeight -
                                    top
                    );


            if (
                    cropWidth <= 1 ||
                            cropHeight <= 1
            ) {

                return null;
            }


            return Bitmap.createBitmap(
                    source,
                    left,
                    top,
                    cropWidth,
                    cropHeight
            );


        } catch (Throwable e) {

            Log.e(
                    TAG,
                    "Ошибка создания OCR crop",
                    e
            );


            return null;
        }
    }


    // =========================================================
    // EXTRACT OCR CANDIDATES
    // =========================================================

    private List<String> extractCandidates(
            Text visionText
    ) {

        if (visionText == null) {

            return Collections.emptyList();
        }


        LinkedHashSet<String> candidates =
                new LinkedHashSet<>();


        try {

            for (
                    Text.TextBlock block :
                    visionText.getTextBlocks()
            ) {

                if (block == null) {
                    continue;
                }


                addCandidatesFromText(
                        block.getText(),
                        candidates
                );


                for (
                        Text.Line line :
                        block.getLines()
                ) {

                    if (line == null) {
                        continue;
                    }


                    addCandidatesFromText(
                            line.getText(),
                            candidates
                    );
                }
            }

        } catch (Throwable e) {

            Log.w(
                    TAG,
                    "Ошибка извлечения OCR кандидатов",
                    e
            );
        }


        ArrayList<String> result =
                new ArrayList<>(
                        candidates
                );


        if (result.size() > 8) {

            return new ArrayList<>(
                    result.subList(
                            0,
                            8
                    )
            );
        }


        return result;
    }


    private void addCandidatesFromText(
            String text,
            Set<String> result
    ) {

        if (
                text == null ||
                        text.trim().isEmpty()
        ) {

            return;
        }


        /*
         * Непрерывная последовательность цифр.
         */
        Matcher matcher =
                DIGIT_RUN_PATTERN.matcher(
                        text
                );


        while (matcher.find()) {

            String candidate =
                    matcher.group();


            if (
                    isValidCandidate(
                            candidate
                    )
            ) {

                result.add(
                        candidate
                );
            }
        }


        /*
         * OCR иногда выдаёт:
         *
         * 123 456
         *
         * или:
         *
         * 12O456
         */
        String compact =
                text.replaceAll(
                        "\\s+",
                        ""
                );


        if (!compact.isEmpty()) {

            String corrected =
                    convertConfusableCharacters(
                            compact
                    );


            if (
                    isValidCandidate(
                            corrected
                    )
            ) {

                result.add(
                        corrected
                );
            }
        }
    }


    private String convertConfusableCharacters(
            String value
    ) {

        if (
                value == null ||
                        value.isEmpty()
        ) {

            return "";
        }


        StringBuilder result =
                new StringBuilder();


        for (
                int i = 0;
                i < value.length();
                i++
        ) {

            char c =
                    value.charAt(i);


            switch (c) {

                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':

                    result.append(
                            c
                    );

                    break;


                case 'O':
                case 'o':
                case 'Q':
                case 'q':

                    result.append(
                            '0'
                    );

                    break;


                case 'I':
                case 'i':
                case 'L':
                case 'l':

                    result.append(
                            '1'
                    );

                    break;


                case 'Z':
                case 'z':

                    result.append(
                            '2'
                    );

                    break;


                case 'S':
                case 's':

                    result.append(
                            '5'
                    );

                    break;


                case 'B':
                case 'b':

                    result.append(
                            '8'
                    );

                    break;


                case 'G':
                case 'g':

                    result.append(
                            '6'
                    );

                    break;


                default:

                    break;
            }
        }


        return result.toString();
    }


    private boolean isValidCandidate(
            String value
    ) {

        if (value == null) {
            return false;
        }


        int length =
                value.length();


        if (
                length < MIN_DIGITS ||
                        length > MAX_DIGITS
        ) {

            return false;
        }


        for (
                int i = 0;
                i < length;
                i++
        ) {

            if (
                    !Character.isDigit(
                            value.charAt(i)
                    )
            ) {

                return false;
            }
        }


        return true;
    }


    // =========================================================
    // LIVE HISTORY
    // =========================================================

    private void addLiveCandidates(
            List<String> candidates
    ) {

        if (
                candidates == null ||
                        candidates.isEmpty()
        ) {

            return;
        }


        synchronized (
                liveHistoryLock
        ) {

            for (
                    String candidate :
                    candidates
            ) {

                if (
                        !isValidCandidate(
                                candidate
                        )
                ) {

                    continue;
                }


                liveHistory.addLast(
                        candidate
                );


                while (
                        liveHistory.size() >
                                MAX_LIVE_HISTORY
                ) {

                    liveHistory.removeFirst();
                }
            }
        }
    }


    private List<String>
    getLiveHistorySnapshot() {

        synchronized (
                liveHistoryLock
        ) {

            return new ArrayList<>(
                    liveHistory
            );
        }
    }


    // =========================================================
    // BEST LIVE CANDIDATE
    // =========================================================

    private String chooseBestCandidate(
            List<String> candidates
    ) {

        if (
                candidates == null ||
                        candidates.isEmpty()
        ) {

            return "";
        }


        String best =
                "";


        int bestScore =
                Integer.MIN_VALUE;


        for (
                String candidate :
                candidates
        ) {

            if (
                    !isValidCandidate(
                            candidate
                    )
            ) {

                continue;
            }


            int score =
                    candidate.length() == 6
                            ? 100
                            : 50;


            synchronized (
                    liveHistoryLock
            ) {

                for (
                        String live :
                        liveHistory
                ) {

                    if (
                            candidate.equals(
                                    live
                            )
                    ) {

                        score += 10;
                    }
                }
            }


            if (
                    score > bestScore
            ) {

                bestScore =
                        score;

                best =
                        candidate;
            }
        }


        return best;
    }


    // =========================================================
    // COMPARE LIVE + PHOTO
    // =========================================================

    private void showComparedCandidates(
            List<String> liveSnapshot,
            List<String> photoCandidates
    ) {

        if (shuttingDown) {
            return;
        }


        if (liveSnapshot == null) {

            liveSnapshot =
                    Collections.emptyList();
        }


        if (photoCandidates == null) {

            photoCandidates =
                    Collections.emptyList();
        }


        List<CandidateInfo> merged =
                mergeCandidates(
                        liveSnapshot,
                        photoCandidates
                );


        if (merged.isEmpty()) {

            setOcrStatus(
                    "Цифры не найдены"
            );


            AlertDialog noResultDialog =
                    new AlertDialog.Builder(
                            this
                    )
                            .setTitle(
                                    "Результат распознавания"
                            )
                            .setMessage(
                                    "Цифры не удалось распознать.\n\n" +
                                            "Можно сделать пересъёмку " +
                                            "или ввести значение вручную."
                            )
                            .setNegativeButton(
                                    "Ввести вручную",
                                    (manualDialog, which) -> {

                                        lastRecognizedDigits =
                                                "";

                                        finishWithResult();
                                    }
                            )
                            .setPositiveButton(
                                    "OK",
                                    null
                            )
                            .create();


            noResultDialog.show();

            return;
        }


        boolean hasAgreement =
                false;


        for (
                CandidateInfo info :
                merged
        ) {

            if (
                    info.fromLive &&
                            info.fromPhoto
            ) {

                hasAgreement =
                        true;

                break;
            }
        }


        if (hasAgreement) {

            String agreement =
                    "";


            for (
                    CandidateInfo info :
                    merged
            ) {

                if (
                        info.fromLive &&
                                info.fromPhoto
                ) {

                    agreement =
                            info.value;

                    break;
                }
            }


            setOcrStatus(
                    "Совпадение LIVE + фото: " +
                            agreement
            );

        } else {

            setOcrStatus(
                    "Выберите найденное значение"
            );
        }


        showCandidateDialog(
                merged,
                hasAgreement
        );
    }


    private List<CandidateInfo> mergeCandidates(
            List<String> liveSnapshot,
            List<String> photoCandidates
    ) {

        LinkedHashMap<String, CandidateInfo>
                map =
                new LinkedHashMap<>();


        if (liveSnapshot != null) {

            for (
                    String value :
                    liveSnapshot
            ) {

                if (
                        !isValidCandidate(
                                value
                        )
                ) {

                    continue;
                }


                CandidateInfo info =
                        map.get(
                                value
                        );


                if (info == null) {

                    info =
                            new CandidateInfo(
                                    value
                            );


                    map.put(
                            value,
                            info
                    );
                }


                info.fromLive =
                        true;


                info.liveCount++;
            }
        }


        if (photoCandidates != null) {

            for (
                    String value :
                    photoCandidates
            ) {

                if (
                        !isValidCandidate(
                                value
                        )
                ) {

                    continue;
                }


                CandidateInfo info =
                        map.get(
                                value
                        );


                if (info == null) {

                    info =
                            new CandidateInfo(
                                    value
                            );


                    map.put(
                            value,
                            info
                    );
                }


                info.fromPhoto =
                        true;


                info.photoCount++;
            }
        }


        ArrayList<CandidateInfo>
                result =
                new ArrayList<>(
                        map.values()
                );


        Collections.sort(
                result,
                new Comparator<CandidateInfo>() {

                    @Override
                    public int compare(
                            CandidateInfo first,
                            CandidateInfo second
                    ) {

                        boolean firstBoth =
                                first.fromLive &&
                                        first.fromPhoto;


                        boolean secondBoth =
                                second.fromLive &&
                                        second.fromPhoto;


                        if (
                                firstBoth !=
                                        secondBoth
                        ) {

                            return firstBoth
                                    ? -1
                                    : 1;
                        }


                        boolean firstSix =
                                first.value.length()
                                        == 6;


                        boolean secondSix =
                                second.value.length()
                                        == 6;


                        if (
                                firstSix !=
                                        secondSix
                        ) {

                            return firstSix
                                    ? -1
                                    : 1;
                        }


                        int firstCount =
                                first.liveCount +
                                        first.photoCount;


                        int secondCount =
                                second.liveCount +
                                        second.photoCount;


                        if (
                                firstCount !=
                                        secondCount
                        ) {

                            return Integer.compare(
                                    secondCount,
                                    firstCount
                            );
                        }


                        return first.value.compareTo(
                                second.value
                        );
                    }
                }
        );


        if (
                result.size() > 6
        ) {

            return new ArrayList<>(
                    result.subList(
                            0,
                            6
                    )
            );
        }


        return result;
    }


    // =========================================================
    // CANDIDATE DIALOG
    // =========================================================

    private void showCandidateDialog(
            List<CandidateInfo> candidates,
            boolean hasAgreement
    ) {

        if (shuttingDown) {
            return;
        }


        if (
                candidates == null ||
                        candidates.isEmpty()
        ) {

            return;
        }


        String[] labels =
                new String[
                        candidates.size()
                ];


        int defaultIndex =
                0;


        for (
                int i = 0;
                i < candidates.size();
                i++
        ) {

            CandidateInfo info =
                    candidates.get(i);


            if (
                    info.fromLive &&
                            info.fromPhoto
            ) {

                labels[i] =
                        info.value +
                                "  — LIVE + ФОТО";

            } else if (
                    info.fromPhoto
            ) {

                labels[i] =
                        info.value +
                                "  — ФОТО";

            } else {

                labels[i] =
                        info.value +
                                "  — LIVE";
            }
        }


        int agreementIndex =
                -1;


        for (
                int i = 0;
                i < candidates.size();
                i++
        ) {

            CandidateInfo info =
                    candidates.get(i);


            if (
                    info.fromLive &&
                            info.fromPhoto
            ) {

                agreementIndex =
                        i;

                break;
            }
        }


        if (
                agreementIndex >= 0
        ) {

            defaultIndex =
                    agreementIndex;
        }


        final int[] selectedIndex =
                {
                        defaultIndex
                };


        String title =
                hasAgreement
                        ? "LIVE и фото дали совпадение"
                        : "Выберите показание";


        /*
         * Называем сам объект именно dialogWindow.
         * Нигде ниже нет lambda с параметром "dialog".
         *
         * Это полностью устраняет прежнюю ошибку:
         * variable dialog is already defined.
         */
        AlertDialog dialogWindow =
                new AlertDialog.Builder(
                        this
                )
                        .setTitle(
                                title
                        )

                        .setSingleChoiceItems(
                                labels,
                                defaultIndex,
                                (choiceDialog, which) -> {

                                    selectedIndex[0] =
                                            which;
                                }
                        )

                        .setNegativeButton(
                                "Ввести вручную",
                                (manualDialog, which) -> {

                                    /*
                                     * Пользователь сознательно
                                     * отказывается от OCR.
                                     *
                                     * Возвращаемся в MainActivity
                                     * без OCR-значения.
                                     */
                                    lastRecognizedDigits =
                                            "";

                                    finishWithResult();
                                }
                        )

                        .setPositiveButton(
                                "Применить",
                                null
                        )

                        .create();


        dialogWindow.setOnShowListener(
                showEvent -> {

                    android.widget.Button
                            applyButton =
                            dialogWindow.getButton(
                                    AlertDialog.BUTTON_POSITIVE
                            );


                    if (applyButton == null) {
                        return;
                    }


                    applyButton.setOnClickListener(
                            applyView -> {

                                int index =
                                        selectedIndex[0];


                                if (
                                        index < 0 ||
                                                index >=
                                                        candidates.size()
                                ) {

                                    return;
                                }


                                CandidateInfo selected =
                                        candidates.get(
                                                index
                                        );


                                /*
                                 * Сохраняем выбранное значение.
                                 */
                                lastRecognizedDigits =
                                        selected.value;


                                if (
                                        selected.fromLive &&
                                                selected.fromPhoto
                                ) {

                                    setOcrStatus(
                                            "Выбрано совпадение: " +
                                                    selected.value
                                    );

                                } else {

                                    setOcrStatus(
                                            "Выбрано: " +
                                                    selected.value
                                    );
                                }


                                /*
                                 * Важно:
                                 *
                                 * OCR не фиксирует значение.
                                 *
                                 * Мы только возвращаем выбранное
                                 * число в MainActivity.
                                 *
                                 * MainActivity подставит его
                                 * в барабаны, после чего оператор
                                 * сможет вручную изменить ЛЮБОЙ
                                 * барабан.
                                 */
                                dialogWindow.dismiss();


                                finishWithResult();
                            }
                    );
                }
        );


        dialogWindow.show();
    }


    // =========================================================
    // FINISH WITH RESULT
    // =========================================================

    private void finishWithResult() {

        if (shuttingDown) {
            return;
        }


        if (
                lastRecognizedDigits == null
        ) {

            lastRecognizedDigits =
                    "";
        }


        /*
         * Если фото уже существует —
         * сохраняем и возвращаем его.
         */
        confirmPhoto();
    }


    // =========================================================
    // STATUS
    // =========================================================

    private void setOcrStatus(
            String message
    ) {

        if (
                message == null ||
                        shuttingDown
        ) {

            return;
        }


        runOnUiThread(
                () -> {

                    if (shuttingDown) {
                        return;
                    }


                    View statusView =
                            findViewById(
                                    R.id.tvOcrStatus
                            );


                    if (
                            statusView instanceof TextView
                    ) {

                        TextView statusText =
                                (TextView)
                                        statusView;


                        statusText.setText(
                                message
                        );


                        if (
                                message.startsWith(
                                        "LIVE:"
                                )
                        ) {

                            statusText.setTextColor(
                                    0xFF00FF00
                            );

                        } else if (
                                message.startsWith(
                                        "Совпадение"
                                ) ||
                                        message.startsWith(
                                                "Выбрано"
                                        )
                        ) {

                            statusText.setTextColor(
                                    0xFF00FF00
                            );

                        } else {

                            statusText.setTextColor(
                                    0xFFFFFFFF
                            );
                        }
                    }
                }
        );
    }


    // =========================================================
    // PREPARE RETAKE
    // =========================================================

    private void prepareForRetake() {

        photoGeneration.incrementAndGet();


        lastRecognizedDigits =
                "";


        lastLiveRecognizedDigits =
                "";


        synchronized (
                liveHistoryLock
        ) {

            liveHistory.clear();
        }


        if (ivPhotoPreview != null) {

            ivPhotoPreview.setImageDrawable(
                    null
            );


            ivPhotoPreview.setVisibility(
                    View.GONE
            );
        }


        if (lastCapturedBitmap != null) {

            recycleBitmap(
                    lastCapturedBitmap
            );


            lastCapturedBitmap =
                    null;
        }


        if (layoutPreview != null) {

            layoutPreview.setVisibility(
                    View.GONE
            );
        }


        if (layoutCapture != null) {

            layoutCapture.setVisibility(
                    View.VISIBLE
            );
        }


        if (ocrOverlay != null) {

            ocrOverlay.clear();
        }


        setOcrStatus(
                "Наведите на цифры счётчика"
        );


        torchEnabled =
                false;


        lastLiveFrameTime.set(
                0L
        );


        startCamera();
    }


    // =========================================================
    // ROTATE PHOTO
    // =========================================================

    private void rotateCapturedPhoto() {

        if (
                lastCapturedBitmap == null ||
                        lastCapturedBitmap.isRecycled()
        ) {

            return;
        }


        if (photoOcrBusy.get()) {

            Toast.makeText(
                    this,
                    "Подождите завершения распознавания",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        Bitmap rotated =
                rotateBitmap(
                        lastCapturedBitmap,
                        90
                );


        if (rotated == null) {
            return;
        }


        if (
                rotated !=
                        lastCapturedBitmap
        ) {

            recycleBitmap(
                    lastCapturedBitmap
            );
        }


        lastCapturedBitmap =
                rotated;


        if (ivPhotoPreview != null) {

            ivPhotoPreview.setImageBitmap(
                    lastCapturedBitmap
            );


            ivPhotoPreview.setVisibility(
                    View.VISIBLE
            );
        }


        lastRecognizedDigits =
                "";


        if (ocrOverlay != null) {

            ocrOverlay.clear();
        }


        int generation =
                photoGeneration.incrementAndGet();


        setOcrStatus(
                "Повторное распознавание..."
        );


        analyzeCapturedPhoto(
                lastCapturedBitmap,
                generation
        );
    }


    // =========================================================
    // ROTATE BITMAP
    // =========================================================

    private Bitmap rotateBitmap(
            Bitmap bitmap,
            int degrees
    ) {

        if (bitmap == null) {
            return null;
        }


        if (degrees == 0) {
            return bitmap;
        }


        try {

            Matrix matrix =
                    new Matrix();


            matrix.postRotate(
                    degrees
            );


            return Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    matrix,
                    true
            );

        } catch (Throwable e) {

            Log.e(
                    TAG,
                    "Ошибка поворота Bitmap",
                    e
            );


            return bitmap;
        }
    }


    // =========================================================
    // TORCH
    // =========================================================

    private void toggleTorch() {

        if (shuttingDown) {
            return;
        }


        if (camera == null) {
            return;
        }


        try {

            if (
                    !camera
                            .getCameraInfo()
                            .hasFlashUnit()
            ) {

                Toast.makeText(
                        this,
                        "Вспышка отсутствует",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }


            torchEnabled =
                    !torchEnabled;


            camera
                    .getCameraControl()
                    .enableTorch(
                            torchEnabled
                    );


            if (btnTorch != null) {

                btnTorch.setAlpha(
                        torchEnabled
                                ? 1.0f
                                : 0.55f
                );
            }

        } catch (Throwable e) {

            Log.w(
                    TAG,
                    "Ошибка управления вспышкой",
                    e
            );
        }
    }


    // =========================================================
    // CONFIRM PHOTO
    // =========================================================

    private void confirmPhoto() {

        if (shuttingDown) {
            return;
        }


        if (
                lastCapturedBitmap == null ||
                        lastCapturedBitmap.isRecycled()
        ) {

            Toast.makeText(
                    this,
                    "Фотография отсутствует",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        File directory =
                new File(
                        getExternalFilesDir(null),
                        "photos"
                );


        try {

            if (!directory.exists()) {

                if (
                        !directory.mkdirs() &&
                                !directory.exists()
                ) {

                    throw new Exception(
                            "Не удалось создать папку"
                    );
                }
            }


            String filename =
                    "meter_" +
                            System.currentTimeMillis() +
                            ".jpg";


            File file =
                    new File(
                            directory,
                            filename
                    );


            try (
                    FileOutputStream output =
                            new FileOutputStream(
                                    file
                            )
            ) {

                lastCapturedBitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        92,
                        output
                );


                output.flush();
            }


            Intent result =
                    new Intent();


            result.putExtra(
                    EXTRA_PHOTO_PATH,
                    file.getAbsolutePath()
            );


            result.putExtra(
                    EXTRA_OCR_TEXT,
                    lastRecognizedDigits == null
                            ? ""
                            : lastRecognizedDigits
            );


            setResult(
                    RESULT_OK,
                    result
            );


            finish();


        } catch (Throwable e) {

            Log.e(
                    TAG,
                    "Ошибка сохранения фотографии",
                    e
            );


            Toast.makeText(
                    this,
                    "Не удалось сохранить фото",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }


    // =========================================================
    // COMPLETE PHOTO OCR
    // =========================================================

    private void finishPhotoOcr() {

        pendingPhotoOcr.decrementAndGet();

        photoOcrBusy.set(
                false
        );


        closeRecognizersIfPossible();
    }


    // =========================================================
    // CLOSE RECOGNIZERS
    // =========================================================

    private void closeRecognizersIfPossible() {

        if (!shuttingDown) {
            return;
        }


        if (
                pendingLiveOcr.get() != 0
        ) {

            return;
        }


        if (
                pendingPhotoOcr.get() != 0
        ) {

            return;
        }


        if (
                liveTextRecognizer != null &&
                        liveRecognizerClosed.compareAndSet(
                                false,
                                true
                        )
        ) {

            try {

                liveTextRecognizer.close();

            } catch (Throwable e) {

                Log.w(
                        TAG,
                        "Ошибка закрытия Live recognizer",
                        e
                );
            }
        }


        if (
                photoTextRecognizer != null &&
                        photoRecognizerClosed.compareAndSet(
                                false,
                                true
                        )
        ) {

            try {

                photoTextRecognizer.close();

            } catch (Throwable e) {

                Log.w(
                        TAG,
                        "Ошибка закрытия Photo recognizer",
                        e
                );
            }
        }
    }


    // =========================================================
    // RECYCLE BITMAP
    // =========================================================

    private void recycleBitmap(
            Bitmap bitmap
    ) {

        if (bitmap == null) {
            return;
        }


        try {

            if (!bitmap.isRecycled()) {

                bitmap.recycle();
            }

        } catch (Throwable ignored) {
        }
    }


    // =========================================================
    // ON DESTROY
    // =========================================================

    @Override
    protected void onDestroy() {

        shuttingDown =
                true;


        photoGeneration.incrementAndGet();


        if (cameraProvider != null) {

            try {

                cameraProvider.unbindAll();

            } catch (Throwable e) {

                Log.w(
                        TAG,
                        "Ошибка unbindAll",
                        e
                );
            }
        }


        if (camera != null) {

            try {

                camera
                        .getCameraControl()
                        .enableTorch(
                                false
                        );

            } catch (Throwable ignored) {
            }
        }


        /*
         * ONNX закрывается через тот же executor,
         * на котором выполняется detect().
         */
        if (
                cameraExecutor != null &&
                        !cameraExecutor.isShutdown()
        ) {

            try {

                cameraExecutor.execute(
                        () -> {

                            if (
                                    meterDetector != null
                            ) {

                                try {

                                    meterDetector.close();

                                } catch (Throwable e) {

                                    Log.w(
                                            TAG,
                                            "Ошибка закрытия MeterDetector",
                                            e
                                    );
                                }
                            }
                        }
                );

            } catch (Throwable e) {

                Log.w(
                        TAG,
                        "Не удалось поставить ONNX cleanup",
                        e
                );
            }


            cameraExecutor.shutdown();

        } else {

            if (
                    meterDetector != null
            ) {

                try {

                    meterDetector.close();

                } catch (Throwable e) {

                    Log.w(
                            TAG,
                            "Ошибка закрытия MeterDetector",
                            e
                    );
                }
            }
        }


        closeRecognizersIfPossible();


        if (lastCapturedBitmap != null) {

            recycleBitmap(
                    lastCapturedBitmap
            );


            lastCapturedBitmap =
                    null;
        }


        super.onDestroy();
    }


    // =========================================================
    // CANDIDATE INFO
    // =========================================================

    private static class CandidateInfo {

        final String value;

        boolean fromLive =
                false;

        boolean fromPhoto =
                false;

        int liveCount =
                0;

        int photoCount =
                0;


        CandidateInfo(
                String value
        ) {

            this.value =
                    value;
        }
    }
}