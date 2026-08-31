package com.example.myfirstapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CropActivity extends AppCompatActivity {

    public static final String EXTRA_INPUT_PATH = "input_path";
    public static final String EXTRA_OUTPUT_PATH = "output_path";

    private CropView cropView;
    private MaterialButton btnRetake, btnAccept;
    
    private Bitmap originalBitmap;
    private String inputPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        cropView = findViewById(R.id.cropView);
        btnRetake = findViewById(R.id.btnRetake);
        btnAccept = findViewById(R.id.btnAccept);

        inputPath = getIntent().getStringExtra(EXTRA_INPUT_PATH);
        if (inputPath == null) {
            Toast.makeText(this, "Ошибка: нет фото", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        originalBitmap = BitmapFactory.decodeFile(inputPath);
        if (originalBitmap == null) {
            Toast.makeText(this, "Ошибка загрузки фото", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        cropView.setBitmap(originalBitmap);

        btnRetake.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        btnAccept.setOnClickListener(v -> {
            cropAndSave();
        });
    }

    private void cropAndSave() {
        RectF cropRect = cropView.getCropRect();
        
        int x = (int) cropRect.left;
        int y = (int) cropRect.top;
        int width = (int) cropRect.width();
        int height = (int) cropRect.height();
        
        // Защита от выхода за границы
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x + width > originalBitmap.getWidth()) width = originalBitmap.getWidth() - x;
        if (y + height > originalBitmap.getHeight()) height = originalBitmap.getHeight() - y;
        
        if (width <= 0 || height <= 0) {
            Toast.makeText(this, "Ошибка: рамка слишком мала", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            Bitmap croppedBitmap = Bitmap.createBitmap(originalBitmap, x, y, width, height);
            
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File outputFile = new File(dir, "METER_CROPPED_" + timeStamp + ".jpg");
            
            FileOutputStream fos = new FileOutputStream(outputFile);
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();
            
            croppedBitmap.recycle();
            
            Intent result = new Intent();
            result.putExtra(EXTRA_OUTPUT_PATH, outputFile.getAbsolutePath());
            setResult(RESULT_OK, result);
            finish();
            
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка сохранения: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (originalBitmap != null && !originalBitmap.isRecycled()) {
            originalBitmap.recycle();
        }
    }
}