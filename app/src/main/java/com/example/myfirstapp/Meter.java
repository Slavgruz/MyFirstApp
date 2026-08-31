package com.example.myfirstapp;

public class Meter {
    private long id;
    private long addressId;
    private String resource;
    private String serialNumber;

    public Meter() {}

    public Meter(long addressId, String resource, String serialNumber) {
        this.addressId = addressId;
        this.resource = resource;
        this.serialNumber = serialNumber;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getAddressId() { return addressId; }
    public void setAddressId(long addressId) { this.addressId = addressId; }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
}