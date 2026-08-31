package com.example.myfirstapp;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CsvExporter {
    
    public static void exportToCsv(Context context, MeterDatabase db) {
        List<Reading> readings = db.getAllReadings();
        
        if (readings.isEmpty()) {
            Toast.makeText(context, "Нет данных для экспорта", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            String fileName = "meter_readings_" + 
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + 
                    ".csv";
            
            File docsDir = new File(context.getExternalFilesDir(null), "Documents");
            if (!docsDir.exists()) docsDir.mkdirs();
            File file = new File(docsDir, fileName);
            
            FileWriter writer = new FileWriter(file);
            
            // BOM для корректного отображения кириллицы в Excel
            writer.write("\uFEFF");
            writer.write("Дата;Ресурс;Улица;Дом;Корпус;Квартира;Показание;Фото\n");
            
            for (Reading reading : readings) {
                Meter meter = db.getMeter(reading.getMeterId());
                if (meter == null) continue;
                
                Address address = db.getAddress(meter.getAddressId());
                if (address == null) continue;
                
                String dateStr = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                        .format(new Date(reading.getDatetime()));
                
                StringBuilder line = new StringBuilder();
                line.append(dateStr).append(";");
                line.append(meter.getResource()).append(";");
                line.append(address.getStreet() != null ? address.getStreet() : "").append(";");
                line.append(address.getHouse() != null ? address.getHouse() : "").append(";");
                line.append(address.getBuilding() != null ? address.getBuilding() : "").append(";");
                line.append(address.getApartment() != null ? address.getApartment() : "").append(";");
                line.append(reading.getConfirmedReading()).append(";");
                line.append(reading.getPhotoUri() != null && !reading.getPhotoUri().isEmpty() ? "Да" : "Нет");
                line.append("\n");
                
                writer.write(line.toString());
            }
            
            writer.close();
            
            Uri fileUri = FileProvider.getUriForFile(context,
                    "com.example.myfirstapp.fileprovider", file);
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Журнал обходов");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            context.startActivity(Intent.createChooser(shareIntent, "Экспорт журнала"));
            
            Toast.makeText(context, "Файл готов к отправке", Toast.LENGTH_SHORT).show();
            
        } catch (IOException e) {
            Toast.makeText(context, "Ошибка экспорта: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}