package com.example.nfc_based_student_lab_access_app;

public class RoomOccupancy {

    private int current_count;
    private String display_name;
    private int max_capacity;

    // Empty constructor needed for Firebase
    public RoomOccupancy() {

    }

    public RoomOccupancy(int current_count, String display_name, int max_capacity) {
        this.current_count = current_count;
        this.display_name = display_name;
        this.max_capacity = max_capacity;
    }

    // Getter for current_count
    public int getCurrent_count() {
        return current_count;
    }

    // Setter for current_count
    public void setCurrent_count(int current_count) {
        this.current_count = current_count;
    }

    // Getter for display_name
    public String getDisplay_name() {
        return display_name;
    }

    // Setter for display_name
    public void setDisplay_name(String display_name) {
        this.display_name = display_name;
    }

    // Getter for max_capacity
    public int getMax_capacity() {
        return max_capacity;
    }

    // Setter for max_capacity
    public void setMax_capacity(int max_capacity) {
        this.max_capacity = max_capacity;
    }

    // Optional: helper method (VERY useful later)
    public double getOccupancyPercentage() {
        if (max_capacity == 0) return 0;
        return (double) current_count / max_capacity;
    }
}