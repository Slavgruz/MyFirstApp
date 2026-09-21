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

    private EditText etStreet;
    private EditText etHouse;
    private EditText etBuilding;
    private EditText etApartment;

    private RadioGroup rgResource;

    private MaterialButton btnCamera;
    private MaterialButton btnSave;
    private MaterialButton btnJournal;

    private TextView tvOcrResult;

    private CardView cardOcrResult;

    private final List<WheelView> wheels =
            new ArrayList<>();

    private MeterDatabase db;

    private String currentPhotoPath;

    private ActivityResultLauncher<Intent> cameraLauncher;

    private ActivityResultLauncher<String> permissionLauncher;


    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(
                savedInstanceState
        );


        setContentView(
                R.layout.activity_main
        );


        // =====================================================
        // DATABASE
        // =====================================================

        db =
                new MeterDatabase(
                        this
                );


        // =====================================================
        // VIEWS
        // =====================================================

        etStreet =
                findViewById(
                        R.id.etStreet
                );

        etHouse =
                findViewById(
                        R.id.etHouse
                );

        etBuilding =
                findViewById(
                        R.id.etBuilding
                );

        etApartment =
                findViewById(
                        R.id.etApartment
                );


        rgResource =
                findViewById(
                        R.id.rgResource
                );


        btnCamera =
                findViewById(
                        R.id.btnCamera
                );


        btnSave =
                findViewById(
                        R.id.btnSave
                );


        btnJournal =
                findViewById(
                        R.id.btnJournal
                );


        tvOcrResult =
                findViewById(
                        R.id.tvOcrResult
                );


        cardOcrResult =
                findViewById(
                        R.id.cardOcrResult
                );


        // =====================================================
        // WHEELS
        // =====================================================

        wheels.add(
                findViewById(
                        R.id.wheel1
                )
        );

        wheels.add(
                findViewById(
                        R.id.wheel2
                )
        );

        wheels.add(
                findViewById(
                        R.id.wheel3
                )
        );

        wheels.add(
                findViewById(
                        R.id.wheel4
                )
        );

        wheels.add(
                findViewById(
                        R.id.wheel5
                )
        );

        wheels.add(
                findViewById(
                        R.id.wheel6
                )
        );

        wheels.add(
                findViewById(
                        R.id.wheel7
                )
        );


        for (
                WheelView wheel :
                wheels
        ) {

            if (wheel != null) {

                wheel.setValue(
                        0,
                        false
                );
            }
        }


        // =====================================================
        // RESOURCE CHANGE
        // =====================================================

        rgResource.setOnCheckedChangeListener(
                (group, checkedId) ->
                        resetReadingInputs()
        );


        // =====================================================
        // CAMERA RESULT
        // =====================================================

        cameraLauncher =
                registerForActivityResult(
                        new ActivityResultContracts
                                .StartActivityForResult(),

                        result -> {

                            if (
                                    result.getResultCode()
                                            != RESULT_OK
                            ) {
                                return;
                            }


                            Intent data =
                                    result.getData();


                            if (data == null) {
                                return;
                            }


                            // -------------------------------------------------
                            // PHOTO PATH
                            // -------------------------------------------------

                            currentPhotoPath =
                                    data.getStringExtra(
                                            CameraActivity
                                                    .EXTRA_PHOTO_PATH
                                    );


                            // -------------------------------------------------
                            // OCR RESULT
                            // -------------------------------------------------

                            String ocr =
                                    data.getStringExtra(
                                            CameraActivity
                                                    .EXTRA_OCR_TEXT
                                    );


                            /*
                             * Если пользователь нажал
                             * "Применить", OCR-значение есть.
                             *
                             * Оно становится начальным значением
                             * барабанов.
                             *
                             * После этого пользователь МОЖЕТ
                             * вручную изменить любой барабан.
                             */

                            if (
                                    ocr != null &&
                                            !ocr.trim().isEmpty()
                            ) {

                                cardOcrResult.setVisibility(
                                        View.VISIBLE
                                );


                                tvOcrResult.setText(
                                        ocr
                                );


                                applyRecognizedValue(
                                        ocr
                                );

                            } else {

                                /*
                                 * "Ввести вручную":
                                 *
                                 * ничего не подставляем;
                                 * никакого сообщения об ошибке
                                 * не показываем.
                                 *
                                 * Пользователь просто остаётся
                                 * на главном экране и выставляет
                                 * показание барабанами вручную.
                                 */

                                cardOcrResult.setVisibility(
                                        View.GONE
                                );
                            }
                        }
                );


        // =====================================================
        // CAMERA PERMISSION
        // =====================================================

        permissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts
                                .RequestPermission(),

                        granted -> {

                            if (granted) {

                                launchCamera();

                            } else {

                                showInfoDialog(
                                        "Ошибка",
                                        "Нужен доступ к камере"
                                );
                            }
                        }
                );


        // =====================================================
        // CAMERA BUTTON
        // =====================================================

        btnCamera.setOnClickListener(
                view -> {

                    if (
                            ContextCompat.checkSelfPermission(
                                    this,
                                    Manifest.permission.CAMERA
                            )
                                    ==
                                    PackageManager.PERMISSION_GRANTED
                    ) {

                        launchCamera();

                    } else {

                        permissionLauncher.launch(
                                Manifest.permission.CAMERA
                        );
                    }
                }
        );


        // =====================================================
        // SAVE
        // =====================================================

        btnSave.setOnClickListener(
                view ->
                        saveRecord()
        );


        // =====================================================
        // JOURNAL
        // =====================================================

        btnJournal.setOnClickListener(
                view ->
                        startActivity(
                                new Intent(
                                        this,
                                        JournalActivity.class
                                )
                        )
        );
    }


    // =========================================================
    // INFO DIALOG
    // =========================================================

    private void showInfoDialog(
            String title,
            String message
    ) {

        new AlertDialog.Builder(
                this
        )
                .setTitle(
                        title
                )
                .setMessage(
                        message
                )
                .setPositiveButton(
                        "OK",
                        null
                )
                .show();
    }


    // =========================================================
    // APPLY OCR VALUE
    // =========================================================

    private void applyRecognizedValue(
            String value
    ) {

        if (
                value == null ||
                        value.trim().isEmpty()
        ) {

            return;
        }


        String intPart =
                value.replaceAll(
                        "[^0-9]",
                        ""
                );


        if (intPart.isEmpty()) {
            return;
        }


        /*
         * Основная часть счётчика —
         * 6 цифр.
         *
         * Если OCR дал меньше —
         * дополняем слева нулями.
         *
         * Если больше —
         * берём последние 6.
         *
         * Это только начальная подстановка.
         * Пользователь после неё может
         * прокрутить любой барабан.
         */

        while (
                intPart.length() < 6
        ) {

            intPart =
                    "0" +
                            intPart;
        }


        if (
                intPart.length() > 6
        ) {

            intPart =
                    intPart.substring(
                            intPart.length() -
                                    6
                    );
        }


        // -----------------------------------------------------
        // ЦЕЛАЯ ЧАСТЬ
        // -----------------------------------------------------

        for (
                int i = 0;
                i < 6;
                i++
        ) {

            int digit =
                    Character.getNumericValue(
                            intPart.charAt(
                                    i
                            )
                    );


            if (
                    digit < 0 ||
                            digit > 9
            ) {

                digit = 0;
            }


            WheelView wheel =
                    wheels.get(i);


            if (wheel != null) {

                wheel.setValue(
                        digit,
                        true
                );
            }
        }


        /*
         * На этом всё.
         *
         * WheelView остаются полностью
         * интерактивными.
         *
         * Например:
         *
         * OCR: 123456
         *
         * можно вручную сделать:
         *
         * 123496
         * 123457
         * 129457
         * и т.д.
         */
    }


    // =========================================================
    // RESET INPUTS
    // =========================================================

    private void resetReadingInputs() {

        for (
                WheelView wheel :
                wheels
        ) {

            if (wheel != null) {

                wheel.setValue(
                        0,
                        false
                );
            }
        }


        currentPhotoPath =
                null;


        btnCamera.setText(
                "📷 Сделать фото"
        );


        cardOcrResult.setVisibility(
                View.GONE
        );
    }


    // =========================================================
    // LAUNCH CAMERA
    // =========================================================

    private void launchCamera() {

        resetReadingInputs();


        cameraLauncher.launch(
                new Intent(
                        this,
                        CameraActivity.class
                )
        );
    }


    // =========================================================
    // SAVE RECORD
    // =========================================================

    private void saveRecord() {

        String street =
                etStreet
                        .getText()
                        .toString()
                        .trim();


        String house =
                etHouse
                        .getText()
                        .toString()
                        .trim();


        String building =
                etBuilding
                        .getText()
                        .toString()
                        .trim();


        String apartment =
                etApartment
                        .getText()
                        .toString()
                        .trim();


        if (
                house.isEmpty() ||
                        apartment.isEmpty()
        ) {

            showInfoDialog(
                    "Ошибка",
                    "Заполните дом и квартиру!"
            );

            return;
        }


        // =====================================================
        // READING
        // =====================================================

        StringBuilder integerPart =
                new StringBuilder();


        for (
                int i = 0;
                i < 6;
                i++
        ) {

            WheelView wheel =
                    wheels.get(i);


            integerPart.append(
                    wheel.getValue()
            );
        }


        int fractionalPart =
                wheels.get(6).getValue();


        String reading =
                integerPart
                        .toString()
                        +
                        "."
                        +
                        fractionalPart;


        // =====================================================
        // RESOURCE
        // =====================================================

        int selectedResource =
                rgResource
                        .getCheckedRadioButtonId();


        String resource =
                "ХВС";


        if (
                selectedResource ==
                        R.id.rbGvs
        ) {

            resource =
                    "ГВС";

        } else if (
                selectedResource ==
                        R.id.rbHeat
        ) {

            resource =
                    "Тепло";
        }


        // =====================================================
        // ADDRESS
        // =====================================================

        Address address =
                new Address(
                        street,
                        house,
                        building,
                        apartment
                );


        long addressId =
                db.findOrCreateAddress(
                        address
                );


        long meterId =
                db.findOrCreateMeter(
                        addressId,
                        resource
                );


        // =====================================================
        // READING OBJECT
        // =====================================================

        Reading readingObject =
                new Reading(
                        meterId,
                        System.currentTimeMillis(),
                        reading,
                        reading,
                        currentPhotoPath != null
                                ? currentPhotoPath
                                : ""
                );


        // =====================================================
        // INSERT
        // =====================================================

        if (
                db.insertReading(
                        readingObject
                ) != -1
        ) {

            showInfoDialog(
                    "Успех",
                    "Сохранено: " +
                            resource +
                            " = " +
                            reading
            );


            etApartment.setText(
                    ""
            );


            resetReadingInputs();

        } else {

            showInfoDialog(
                    "Ошибка",
                    "Ошибка сохранения!"
            );
        }
    }
}