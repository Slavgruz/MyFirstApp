package com.example.myfirstapp;

public class Reading {
    private long id;
    private long meterId;
    private long datetime;
    private String ocrReading;
    private String confirmedReading;
    private String photoUri;

    public Reading() {}

    public Reading(long meterId, long datetime, String ocrReading, String confirmedReading, String photoUri) {
        this.meterId = meterId;
        this.datetime = datetime;
        this.ocrReading = ocrReading;
        this.confirmedReading = confirmedReading;
        this.photoUri = photoUri;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getMeterId() { return meterId; }
    public void setMeterId(long meterId) { this.meterId = meterId; }

    public long getDatetime() { return datetime; }
    public void setDatetime(long datetime) { this.datetime = datetime; }

    public String getOcrReading() { return ocrReading; }
    public void setOcrReading(String ocrReading) { this.ocrReading = ocrReading; }

    public String getConfirmedReading() { return confirmedReading; }
    public void setConfirmedReading(String confirmedReading) { this.confirmedReading = confirmedReading; }

    public String getPhotoUri() { return photoUri; }
    public void setPhotoUri(String photoUri) { this.photoUri = photoUri; }
}