package com.example.myfirstapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class MeterDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "meters.db";
    private static final int DATABASE_VERSION = 1;

    // Таблицы
    private static final String TABLE_ADDRESSES = "addresses";
    private static final String TABLE_METERS = "meters";
    private static final String TABLE_READINGS = "readings";

    // Колонки addresses
    private static final String COL_ADDR_ID = "id";
    private static final String COL_ADDR_STREET = "street";
    private static final String COL_ADDR_HOUSE = "house";
    private static final String COL_ADDR_BUILDING = "building";
    private static final String COL_ADDR_APARTMENT = "apartment";

    // Колонки meters
    private static final String COL_METER_ID = "id";
    private static final String COL_METER_ADDRESS_ID = "address_id";
    private static final String COL_METER_RESOURCE = "resource";
    private static final String COL_METER_SERIAL = "serial_number";

    // Колонки readings
    private static final String COL_READ_ID = "id";
    private static final String COL_READ_METER_ID = "meter_id";
    private static final String COL_READ_DATETIME = "datetime";
    private static final String COL_READ_OCR = "ocr_reading";
    private static final String COL_READ_CONFIRMED = "confirmed_reading";
    private static final String COL_READ_PHOTO = "photo_uri";

    private static final String SQL_CREATE_ADDRESSES =
            "CREATE TABLE " + TABLE_ADDRESSES + " (" +
            COL_ADDR_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_ADDR_STREET + " TEXT, " +
            COL_ADDR_HOUSE + " TEXT, " +
            COL_ADDR_BUILDING + " TEXT, " +
            COL_ADDR_APARTMENT + " TEXT)";

    private static final String SQL_CREATE_METERS =
            "CREATE TABLE " + TABLE_METERS + " (" +
            COL_METER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_METER_ADDRESS_ID + " INTEGER, " +
            COL_METER_RESOURCE + " TEXT, " +
            COL_METER_SERIAL + " TEXT, " +
            "FOREIGN KEY(" + COL_METER_ADDRESS_ID + ") REFERENCES " + TABLE_ADDRESSES + "(" + COL_ADDR_ID + "))";

    private static final String SQL_CREATE_READINGS =
            "CREATE TABLE " + TABLE_READINGS + " (" +
            COL_READ_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_READ_METER_ID + " INTEGER, " +
            COL_READ_DATETIME + " INTEGER, " +
            COL_READ_OCR + " TEXT, " +
            COL_READ_CONFIRMED + " TEXT, " +
            COL_READ_PHOTO + " TEXT, " +
            "FOREIGN KEY(" + COL_READ_METER_ID + ") REFERENCES " + TABLE_METERS + "(" + COL_METER_ID + "))";

    public MeterDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ADDRESSES);
        db.execSQL(SQL_CREATE_METERS);
        db.execSQL(SQL_CREATE_READINGS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_READINGS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_METERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ADDRESSES);
        onCreate(db);
    }

    // ==================== ADDRESSES ====================

    public long insertAddress(Address address) {
        if (address == null) return -1;
        
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_ADDR_STREET, address.getStreet());
        values.put(COL_ADDR_HOUSE, address.getHouse());
        values.put(COL_ADDR_BUILDING, address.getBuilding());
        values.put(COL_ADDR_APARTMENT, address.getApartment());
        
        return db.insert(TABLE_ADDRESSES, null, values);
    }

    public long findOrCreateAddress(Address address) {
        if (address == null) return -1;
        
        Long existingId = findAddressId(address);
        if (existingId != null) {
            return existingId;
        }
        return insertAddress(address);
    }

    public Long findAddressId(Address address) {
        if (address == null) return null;
        
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COL_ADDR_STREET + "=? AND " + COL_ADDR_HOUSE + "=? AND " +
                          COL_ADDR_BUILDING + "=? AND " + COL_ADDR_APARTMENT + "=?";
        String[] selectionArgs = {
            address.getStreet() != null ? address.getStreet() : "",
            address.getHouse() != null ? address.getHouse() : "",
            address.getBuilding() != null ? address.getBuilding() : "",
            address.getApartment() != null ? address.getApartment() : ""
        };
        
        try (Cursor cursor = db.query(TABLE_ADDRESSES, new String[]{COL_ADDR_ID},
                selection, selectionArgs, null, null, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        }
        return null;
    }

    public Address getAddress(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_ADDRESSES, null,
                COL_ADDR_ID + "=?", new String[]{String.valueOf(id)},
                null, null, null)) {
            if (cursor.moveToFirst()) {
                return cursorToAddress(cursor);
            }
        }
        return null;
    }

    public List<Address> getAllAddresses() {
        List<Address> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_ADDRESSES, null, null, null, null, null,
                COL_ADDR_STREET + ", " + COL_ADDR_HOUSE + ", " + COL_ADDR_APARTMENT)) {
            while (cursor.moveToNext()) {
                list.add(cursorToAddress(cursor));
            }
        }
        return list;
    }

    public int deleteAddress(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_ADDRESSES, COL_ADDR_ID + "=?", new String[]{String.valueOf(id)});
    }

    // ==================== METERS ====================

    public long insertMeter(Meter meter) {
        if (meter == null) return -1;
        
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_METER_ADDRESS_ID, meter.getAddressId());
        values.put(COL_METER_RESOURCE, meter.getResource());
        values.put(COL_METER_SERIAL, meter.getSerialNumber());
        
        return db.insert(TABLE_METERS, null, values);
    }

    public long findOrCreateMeter(long addressId, String resource) {
        Long existingId = findMeterId(addressId, resource);
        if (existingId != null) {
            return existingId;
        }
        Meter meter = new Meter(addressId, resource, null);
        return insertMeter(meter);
    }

    public Long findMeterId(long addressId, String resource) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COL_METER_ADDRESS_ID + "=? AND " + COL_METER_RESOURCE + "=?";
        String[] selectionArgs = {String.valueOf(addressId), resource};
        
        try (Cursor cursor = db.query(TABLE_METERS, new String[]{COL_METER_ID},
                selection, selectionArgs, null, null, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        }
        return null;
    }

    public Meter getMeter(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_METERS, null,
                COL_METER_ID + "=?", new String[]{String.valueOf(id)},
                null, null, null)) {
            if (cursor.moveToFirst()) {
                return cursorToMeter(cursor);
            }
        }
        return null;
    }

    public List<Meter> getMetersForAddress(long addressId) {
        List<Meter> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_METERS, null,
                COL_METER_ADDRESS_ID + "=?", new String[]{String.valueOf(addressId)},
                null, null, COL_METER_RESOURCE)) {
            while (cursor.moveToNext()) {
                list.add(cursorToMeter(cursor));
            }
        }
        return list;
    }

    public int updateMeter(Meter meter) {
        if (meter == null) return 0;
        
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_METER_ADDRESS_ID, meter.getAddressId());
        values.put(COL_METER_RESOURCE, meter.getResource());
        values.put(COL_METER_SERIAL, meter.getSerialNumber());
        
        return db.update(TABLE_METERS, values, COL_METER_ID + "=?",
                new String[]{String.valueOf(meter.getId())});
    }

    public int deleteMeter(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_METERS, COL_METER_ID + "=?", new String[]{String.valueOf(id)});
    }

    // ==================== READINGS ====================

    public long insertReading(Reading reading) {
        if (reading == null) return -1;
        
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_READ_METER_ID, reading.getMeterId());
        values.put(COL_READ_DATETIME, reading.getDatetime());
        values.put(COL_READ_OCR, reading.getOcrReading());
        values.put(COL_READ_CONFIRMED, reading.getConfirmedReading());
        values.put(COL_READ_PHOTO, reading.getPhotoUri());
        
        return db.insert(TABLE_READINGS, null, values);
    }

    public Reading getReading(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_READINGS, null,
                COL_READ_ID + "=?", new String[]{String.valueOf(id)},
                null, null, null)) {
            if (cursor.moveToFirst()) {
                return cursorToReading(cursor);
            }
        }
        return null;
    }

    public List<Reading> getReadingsForMeter(long meterId) {
        List<Reading> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_READINGS, null,
                COL_READ_METER_ID + "=?", new String[]{String.valueOf(meterId)},
                null, null, COL_READ_DATETIME + " DESC")) {
            while (cursor.moveToNext()) {
                list.add(cursorToReading(cursor));
            }
        }
        return list;
    }

    public List<Reading> getAllReadings() {
        List<Reading> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_READINGS, null, null, null, null, null,
                COL_READ_DATETIME + " DESC")) {
            while (cursor.moveToNext()) {
                list.add(cursorToReading(cursor));
            }
        }
        return list;
    }

    public int updateReading(Reading reading) {
        if (reading == null) return 0;
        
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_READ_METER_ID, reading.getMeterId());
        values.put(COL_READ_DATETIME, reading.getDatetime());
        values.put(COL_READ_OCR, reading.getOcrReading());
        values.put(COL_READ_CONFIRMED, reading.getConfirmedReading());
        values.put(COL_READ_PHOTO, reading.getPhotoUri());
        
        return db.update(TABLE_READINGS, values, COL_READ_ID + "=?",
                new String[]{String.valueOf(reading.getId())});
    }

    public int deleteReading(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_READINGS, COL_READ_ID + "=?", new String[]{String.valueOf(id)});
    }

    // ==================== УТИЛИТЫ ====================

    public int getRecordCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_READINGS, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }
        return 0;
    }

    public boolean recordExists(long addressId, String resource, long datetime) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT COUNT(*) FROM " + TABLE_READINGS + " r " +
                      "INNER JOIN " + TABLE_METERS + " m ON r." + COL_READ_METER_ID + " = m." + COL_METER_ID + " " +
                      "WHERE m." + COL_METER_ADDRESS_ID + "=? AND m." + COL_METER_RESOURCE + "=? AND r." + COL_READ_DATETIME + "=?";
        
        try (Cursor cursor = db.rawQuery(query, new String[]{
                String.valueOf(addressId), resource, String.valueOf(datetime)})) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0) > 0;
            }
        }
        return false;
    }

    public void clearDatabase() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_READINGS, null, null);
            db.delete(TABLE_METERS, null, null);
            db.delete(TABLE_ADDRESSES, null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // ==================== CURSOR CONVERTERS ====================

    private Address cursorToAddress(Cursor cursor) {
        Address address = new Address();
        address.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_ADDR_ID)));
        address.setStreet(cursor.getString(cursor.getColumnIndexOrThrow(COL_ADDR_STREET)));
        address.setHouse(cursor.getString(cursor.getColumnIndexOrThrow(COL_ADDR_HOUSE)));
        address.setBuilding(cursor.getString(cursor.getColumnIndexOrThrow(COL_ADDR_BUILDING)));
        address.setApartment(cursor.getString(cursor.getColumnIndexOrThrow(COL_ADDR_APARTMENT)));
        return address;
    }

    private Meter cursorToMeter(Cursor cursor) {
        Meter meter = new Meter();
        meter.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_METER_ID)));
        meter.setAddressId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_METER_ADDRESS_ID)));
        meter.setResource(cursor.getString(cursor.getColumnIndexOrThrow(COL_METER_RESOURCE)));
        meter.setSerialNumber(cursor.getString(cursor.getColumnIndexOrThrow(COL_METER_SERIAL)));
        return meter;
    }

    private Reading cursorToReading(Cursor cursor) {
        Reading reading = new Reading();
        reading.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_READ_ID)));
        reading.setMeterId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_READ_METER_ID)));
        reading.setDatetime(cursor.getLong(cursor.getColumnIndexOrThrow(COL_READ_DATETIME)));
        reading.setOcrReading(cursor.getString(cursor.getColumnIndexOrThrow(COL_READ_OCR)));
        reading.setConfirmedReading(cursor.getString(cursor.getColumnIndexOrThrow(COL_READ_CONFIRMED)));
        reading.setPhotoUri(cursor.getString(cursor.getColumnIndexOrThrow(COL_READ_PHOTO)));
        return reading;
    }
}