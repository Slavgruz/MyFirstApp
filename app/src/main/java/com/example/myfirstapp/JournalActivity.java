package com.example.myfirstapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import java.io.File;
import java.util.List;

public class JournalActivity extends AppCompatActivity {

    private RecyclerView rvRecords;
    private MaterialButton btnExport;
    private MaterialToolbar toolbar;
    
    private MeterDatabase db;
    private RecordAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_journal);

        db = new MeterDatabase(this);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        rvRecords = findViewById(R.id.rvRecords);
        btnExport = findViewById(R.id.btnExport);

        setupRecyclerView();
        
        btnExport.setOnClickListener(v -> CsvExporter.exportToCsv(this, db));
    }
    
    private void setupRecyclerView() {
        List<Reading> readings = db.getAllReadings();
        
        adapter = new RecordAdapter(readings, db, reading -> {
            if (reading.getPhotoUri() != null && !reading.getPhotoUri().isEmpty()) {
                openPhoto(reading.getPhotoUri());
            } else {
                Toast.makeText(this, "Фото отсутствует", Toast.LENGTH_SHORT).show();
            }
        });
        
        rvRecords.setLayoutManager(new LinearLayoutManager(this));
        rvRecords.setAdapter(adapter);
        
        if (readings.isEmpty()) {
            Toast.makeText(this, "Журнал пуст", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void openPhoto(String photoPath) {
        File photoFile = new File(photoPath);
        if (photoFile.exists()) {
            Uri photoUri = FileProvider.getUriForFile(this,
                    "com.example.myfirstapp.fileprovider", photoFile);
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(photoUri, "image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось открыть фото", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Файл фото не найден", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        setupRecyclerView();
    }
}