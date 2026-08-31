package com.example.myfirstapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Environment;
import android.util.Size;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

    public static final String EXTRA_PHOTO_PATH = "photo_path";
    public static final String EXTRA_OCR_TEXT = "ocr_text";

    private PreviewView previewView;
    private ImageView ivPhotoPreview;
    private TextView btnTorch, btnCancel, tvOcrStatus;
    private ImageButton btnCapture;
    private View layoutCapture;
    private View viewFinder;
    private OcrOverlayView ocrOverlay;

    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private androidx.camera.core.Camera camera;
    private boolean torchOn = false;

    private TextRecognizer textRecognizer;
    private MeterDetector meterDetector;
    private volatile String lastRecognizedDigits = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // НЕ ДАЁМ ЭКРАНУ ГАСНУТЬ во время использования камеры
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        setContentView(R.layout.activity_camera);

        previewView = findViewById(R.id.previewView);
        ivPhotoPreview = findViewById(R.id.ivPhotoPreview);
        btnTorch = findViewById(R.id.btnTorch);
        btnCapture = findViewById(R.id.btnCapture);
        btnCancel = findViewById(R.id.btnCancel);
        layoutCapture = findViewById(R.id.layoutCapture);
        tvOcrStatus = findViewById(R.id.tvOcrStatus);
        ocrOverlay = findViewById(R.id.ocrOverlay);
        viewFinder = findViewById(R.id.viewFinder);

        btnTorch.setOnClickListener(v -> toggleTorch());
        btnCapture.setOnClickListener(v -> takePhotoAndReturn());
        btnCancel.setOnClickListener(v -> { setResult(RESULT_CANCELED); finish(); });

        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        meterDetector = new MeterDetector(this);

        cameraExecutor = Executors.newSingleThreadExecutor();
        startCamera();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> f = ProcessCameraProvider.getInstance(this);
        f.addListener(() -> {
            try { bindPreview(f.get()); }
            catch (Exception e) { Toast.makeText(this, "Ошибка камеры", Toast.LENGTH_SHORT).show(); }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cp) {
        Preview preview = new Preview.Builder().build();
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build();

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        try {
            cp.unbindAll();
            camera = cp.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis);
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * ПАЙПЛАЙН: YOLO → Crop → ML Kit
     * 1. YOLO находит табло счётчика (или центральную область как fallback)
     * 2. Кропаем изображение по найденному боксу
     * 3. ML Kit распознаёт цифры ТОЛЬКО внутри кропа
     */
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        Bitmap tempBmp = imageProxy.toBitmap();
        int rot = imageProxy.getImageInfo().getRotationDegrees();
        
        // ИСПРАВЛЕНО: создаём final переменную для использования в лямбде
        final Bitmap originalBmp;
        if (rot != 0) {
            Matrix m = new Matrix();
            m.postRotate(rot);
            originalBmp = Bitmap.createBitmap(tempBmp, 0, 0,
                    tempBmp.getWidth(), tempBmp.getHeight(), m, true);
            tempBmp.recycle();
        } else {
            originalBmp = tempBmp;
        }

        // ШАГ 1: YOLO детекция
        RectF detectedBox = meterDetector.detect(originalBmp);

        // Fallback: если YOLO ничего не нашёл, используем центральную область кадра
        final Bitmap cropBmp;
        final boolean usedYolo = detectedBox != null && detectedBox.width() > 50 && detectedBox.height() > 50;
        
        if (usedYolo) {
            int left = Math.max(0, (int) detectedBox.left);
            int top = Math.max(0, (int) detectedBox.top);
            int w = Math.min((int) detectedBox.width(), originalBmp.getWidth() - left);
            int h = Math.min((int) detectedBox.height(), originalBmp.getHeight() - top);
            cropBmp = Bitmap.createBitmap(originalBmp, left, top, w, h);
        } else {
            // Центральная область 50% ширины, 30% высоты
            int cw = (int)(originalBmp.getWidth() * 0.5f);
            int ch = (int)(originalBmp.getHeight() * 0.3f);
            int cx = (originalBmp.getWidth() - cw) / 2;
            int cy = (originalBmp.getHeight() - ch) / 2;
            cropBmp = Bitmap.createBitmap(originalBmp, cx, cy, cw, ch);
        }

        // ШАГ 2: ML Kit распознавание на кропе
        InputImage image = InputImage.fromBitmap(cropBmp, 0);

        textRecognizer.process(image)
                .addOnSuccessListener(text -> {
                    String bestDigits = "";
                    List<Rect> bestBoxes = new ArrayList<>();
                    List<String> bestTexts = new ArrayList<>();

                    for (Text.TextBlock block : text.getTextBlocks()) {
                        String digitsOnly = block.getText().replaceAll("[^0-9]", "");
                        if (digitsOnly.length() >= 5 && digitsOnly.length() <= 8) {
                            if (digitsOnly.length() > bestDigits.length()) {
                                bestDigits = digitsOnly;
                                bestBoxes.clear();
                                bestTexts.clear();
                                if (block.getBoundingBox() != null) {
                                    bestBoxes.add(block.getBoundingBox());
                                    bestTexts.add(digitsOnly);
                                }
                            }
                        }
                    }

                    lastRecognizedDigits = bestDigits;

                    final String uiDigits = bestDigits;
                    final List<Rect> uiBoxes = new ArrayList<>(bestBoxes);
                    final List<String> uiTexts = new ArrayList<>(bestTexts);

                    runOnUiThread(() -> {
                        if (!uiDigits.isEmpty()) {
                            String prefix = usedYolo ? "🎯 YOLO+ML: " : "📐 Центр+ML: ";
                            tvOcrStatus.setText(prefix + uiDigits);
                            tvOcrStatus.setTextColor(0xFF00FF00);
                            float sx = (float) ocrOverlay.getWidth() / cropBmp.getWidth();
                            float sy = (float) ocrOverlay.getHeight() / cropBmp.getHeight();
                            ocrOverlay.setScale(sx, sy);
                            ocrOverlay.setData(uiBoxes, uiTexts);
                        } else {
                            String mode = usedYolo ? "YOLO" : "Центр";
                            tvOcrStatus.setText(mode + ": цифры не найдены");
                            tvOcrStatus.setTextColor(0xFFFFFFFF);
                            ocrOverlay.clear();
                        }
                    });
                    cropBmp.recycle();
                })
                .addOnFailureListener(e -> {
                    cropBmp.recycle();
                })
                .addOnCompleteListener(t -> {
                    originalBmp.recycle();
                    imageProxy.close();
                });
    }

    private void takePhotoAndReturn() {
        if (imageCapture == null) return;
        imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                runOnUiThread(() -> {
                    Bitmap bmp = image.toBitmap();
                    int rot = image.getImageInfo().getRotationDegrees();
                    if (rot != 0) {
                        Matrix m = new Matrix();
                        m.postRotate(rot);
                        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                    }
                    File file = saveBitmapToFile(bmp);
                    bmp.recycle();
                    image.close();
                    if (file != null) {
                        Intent r = new Intent();
                        r.putExtra(EXTRA_PHOTO_PATH, file.getAbsolutePath());
                        r.putExtra(EXTRA_OCR_TEXT, lastRecognizedDigits);
                        setResult(RESULT_OK, r);
                        finish();
                    }
                });
            }
            @Override
            public void onError(@NonNull ImageCaptureException e) {
                runOnUiThread(() -> Toast.makeText(CameraActivity.this, "Ошибка снимка", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void toggleTorch() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            torchOn = !torchOn;
            camera.getCameraControl().enableTorch(torchOn);
            btnTorch.setTextColor(torchOn ? 0xFFFFD700 : 0xFFFFFFFF);
        }
    }

    private File saveBitmapToFile(Bitmap bmp) {
        try {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File f = new File(dir, "METER_" + ts + ".jpg");
            FileOutputStream fos = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush(); fos.close();
            return f;
        } catch (Exception e) { return null; }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (textRecognizer != null) textRecognizer.close();
        if (meterDetector != null) meterDetector.close();
    }
}