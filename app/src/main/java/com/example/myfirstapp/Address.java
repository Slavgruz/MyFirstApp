package com.example.myfirstapp;

public class Address {
    private long id;
    private String street;
    private String house;
    private String building;
    private String apartment;

    public Address() {}

    public Address(String street, String house, String building, String apartment) {
        this.street = street;
        this.house = house;
        this.building = building;
        this.apartment = apartment;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getHouse() { return house; }
    public void setHouse(String house) { this.house = house; }

    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }

    public String getApartment() { return apartment; }
    public void setApartment(String apartment) { this.apartment = apartment; }

    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        if (street != null && !street.isEmpty()) sb.append(street);
        if (house != null && !house.isEmpty()) sb.append(", д. ").append(house);
        if (building != null && !building.isEmpty()) sb.append(", корп. ").append(building);
        if (apartment != null && !apartment.isEmpty()) sb.append(", кв. ").append(apartment);
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Address address = (Address) obj;
        return (street != null ? street.equals(address.street) : address.street == null) &&
               (house != null ? house.equals(address.house) : address.house == null) &&
               (building != null ? building.equals(address.building) : address.building == null) &&
               (apartment != null ? apartment.equals(address.apartment) : address.apartment == null);
    }

    @Override
    public int hashCode() {
        int result = street != null ? street.hashCode() : 0;
        result = 31 * result + (house != null ? house.hashCode() : 0);
        result = 31 * result + (building != null ? building.hashCode() : 0);
        result = 31 * result + (apartment != null ? apartment.hashCode() : 0);
        return result;
    }
}