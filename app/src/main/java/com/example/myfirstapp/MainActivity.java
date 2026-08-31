package com.example.myfirstapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private EditText etStreet, etHouse, etBuilding, etApartment;
    private RadioGroup rgResource;
    private MaterialButton btnCamera, btnSave, btnJournal;
    private TextView tvOcrResult;
    private CardView cardOcrResult;

    private List<WheelView> wheels = new ArrayList<>();
    private MeterDatabase db;
    private String currentPhotoPath;

    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = new MeterDatabase(this);
        etStreet = findViewById(R.id.etStreet);
        etHouse = findViewById(R.id.etHouse);
        etBuilding = findViewById(R.id.etBuilding);
        etApartment = findViewById(R.id.etApartment);
        rgResource = findViewById(R.id.rgResource);
        btnCamera = findViewById(R.id.btnCamera);
        btnSave = findViewById(R.id.btnSave);
        btnJournal = findViewById(R.id.btnJournal);
        tvOcrResult = findViewById(R.id.tvOcrResult);
        cardOcrResult = findViewById(R.id.cardOcrResult);

        wheels.add(findViewById(R.id.wheel1));
        wheels.add(findViewById(R.id.wheel2));
        wheels.add(findViewById(R.id.wheel3));
        wheels.add(findViewById(R.id.wheel4));
        wheels.add(findViewById(R.id.wheel5));
        wheels.add(findViewById(R.id.wheel6));
        wheels.add(findViewById(R.id.wheel7));

        for (WheelView w : wheels) {
            w.setValue(0, false);
        }

        rgResource.setOnCheckedChangeListener((g, id) -> resetReadingInputs());

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        currentPhotoPath = result.getData().getStringExtra(CameraActivity.EXTRA_PHOTO_PATH);
                        String ocr = result.getData().getStringExtra(CameraActivity.EXTRA_OCR_TEXT);
                        if (ocr != null && !ocr.isEmpty()) {
                            cardOcrResult.setVisibility(View.VISIBLE);
                            tvOcrResult.setText(ocr);
                            applyRecognizedValue(ocr);
                        } else {
                            showInfoDialog("OCR", "Цифры не найдены. Поместите табло в зелёную рамку.");
                        }
                    }
                });

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), ok -> {
                    if (ok) launchCamera();
                    else showInfoDialog("Ошибка", "Нужен доступ к камере");
                });

        btnCamera.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) launchCamera();
            else permissionLauncher.launch(Manifest.permission.CAMERA);
        });

        btnSave.setOnClickListener(v -> saveRecord());
        btnJournal.setOnClickListener(v -> startActivity(new Intent(this, JournalActivity.class)));
    }

    private void showInfoDialog(String t, String m) {
        new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("OK", null).show();
    }

    private void applyRecognizedValue(String value) {
        String intPart = value.replaceAll("[^0-9]", "");

        while (intPart.length() < 6) intPart = "0" + intPart;
        if (intPart.length() > 6) intPart = intPart.substring(intPart.length() - 6);

        for (int i = 0; i < 6; i++) {
            int d = Character.getNumericValue(intPart.charAt(i));
            if (d < 0 || d > 9) d = 0;
            wheels.get(i).setValue(d, true);
        }
    }

    private void resetReadingInputs() {
        for (WheelView w : wheels) w.setValue(0, false);
        currentPhotoPath = null;
        btnCamera.setText("📷 Сделать фото");
        cardOcrResult.setVisibility(View.GONE);
    }

    private void launchCamera() {
        resetReadingInputs();
        cameraLauncher.launch(new Intent(this, CameraActivity.class));
    }

    private void saveRecord() {
        String street = etStreet.getText().toString().trim();
        String house = etHouse.getText().toString().trim();
        String building = etBuilding.getText().toString().trim();
        String apartment = etApartment.getText().toString().trim();

        if (house.isEmpty() || apartment.isEmpty()) {
            showInfoDialog("Ошибка", "Заполните дом и квартиру!");
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(wheels.get(i).getValue());
        int frac = wheels.get(6).getValue();
        String reading = sb.toString() + "." + frac;

        int sel = rgResource.getCheckedRadioButtonId();
        String res = "ХВС";
        if (sel == R.id.rbGvs) res = "ГВС";
        else if (sel == R.id.rbHeat) res = "Тепло";

        Address addr = new Address(street, house, building, apartment);
        long aid = db.findOrCreateAddress(addr);
        long mid = db.findOrCreateMeter(aid, res);

        Reading r = new Reading(mid, System.currentTimeMillis(), reading, reading,
                currentPhotoPath != null ? currentPhotoPath : "");
        if (db.insertReading(r) != -1) {
            showInfoDialog("Успех", "Сохранено: " + res + " = " + reading);
            etApartment.setText("");
            resetReadingInputs();
        } else {
            showInfoDialog("Ошибка", "Ошибка сохранения!");
        }
    }
}