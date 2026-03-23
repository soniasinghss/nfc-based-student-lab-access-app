package com.example.nfc_based_student_lab_access_app;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;

import android.graphics.Picture;
import android.graphics.drawable.PictureDrawable;
import android.widget.ImageView;

import androidx.appcompat.widget.Toolbar;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MapActivity extends AppCompatActivity {

    private DatabaseReference occupancyRef;
    private HashMap<String, RoomOccupancy> roomMap = new HashMap<>();
    private TextView tvSelectedRoom;
    private TextView tvSelectedOccupancy;
    private TextView tvSelectedStatus;
    private String selectedRoomId = null;
    private ImageView ivMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false); // because you're already showing "Floor Map" in the header
        }

        occupancyRef = FirebaseDatabase.getInstance().getReference("occupancy");

        readRoomOccupancy();
        ivMap = findViewById(R.id.ivMap);

        tvSelectedRoom = findViewById(R.id.tvSelectedRoom);
        tvSelectedOccupancy = findViewById(R.id.tvSelectedOccupancy);
        tvSelectedStatus = findViewById(R.id.tvSelectedStatus);

        findViewById(R.id.H843).setOnClickListener(v -> {
            showRoomDetails("H843");
        });
    }

    private void readRoomOccupancy() {
        occupancyRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d("FIREBASE_ROOM", "onDataChange triggered");

                roomMap.clear();

                for (DataSnapshot roomSnapshot : snapshot.getChildren()) {
                    String roomId = roomSnapshot.getKey();
                    RoomOccupancy room = roomSnapshot.getValue(RoomOccupancy.class);

                    if (room != null && roomId != null) {
                        roomMap.put(roomId, room);

                        Log.d("FIREBASE_ROOM", "Room ID: " + roomId);
                        Log.d("FIREBASE_ROOM", "Display Name: " + room.getDisplay_name());
                        Log.d("FIREBASE_ROOM", "Current Count: " + room.getCurrent_count());
                        Log.d("FIREBASE_ROOM", "Max Capacity: " + room.getMax_capacity());
                        Log.d("ROOM_STATUS", roomId + " -> " + getOccupancyStatus(room));
                        Log.d("ROOM_COLOR_HEX", roomId + " -> " + Integer.toHexString(getRoomColor(room)));
                    }
                }

                loadSvg();
                Log.d("FIREBASE_ROOM", "Total rooms loaded: " + roomMap.size());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FIREBASE_ERROR", "Database error: " + error.getMessage());
            }
        });
    }

    private String getOccupancyStatus(RoomOccupancy room) {
        if (room.getMax_capacity() == 0) {
            return "NO_DATA";
        }

        double percentage = (double) room.getCurrent_count() / room.getMax_capacity();

        if (percentage <= 0.25) {
            return "GREEN";
        } else if (percentage <= 0.50) {
            return "YELLOW";
        } else if (percentage < 1.0) {
            return "ORANGE";
        } else {
            return "RED";
        }
    }
    private int getRoomColor(RoomOccupancy room) {
        if (room.getMax_capacity() == 0) {
            return android.graphics.Color.LTGRAY;
        }

        double percentage = (double) room.getCurrent_count() / room.getMax_capacity();

        if (percentage <= 0.25) {
            return android.graphics.Color.parseColor("#006B3E"); // green
        } else if (percentage <= 0.50) {
            return android.graphics.Color.parseColor("#FBC02D"); // yellow
        } else if (percentage < 1.0) {
            return android.graphics.Color.parseColor("#FFAA1C"); // orange
        } else {
            return android.graphics.Color.parseColor("#C62828"); // red
        }
    }
    private void loadSvg() {
        try {
            String svgText = readRawSvgFile(R.raw.h8_simple);

            for (String roomId : roomMap.keySet()) {
                RoomOccupancy room = roomMap.get(roomId);

                if (room != null) {
                    int colorInt = getRoomColor(room);
                    String hexColor = colorIntToHex(colorInt);

                    svgText = colorRoomInSvg(svgText, roomId, hexColor);
                }
            }

            SVG svg = SVG.getFromString(svgText);
            Picture picture = svg.renderToPicture();
            PictureDrawable drawable = new PictureDrawable(picture);
            ivMap.setImageDrawable(drawable);

        } catch (SVGParseException e) {
            e.printStackTrace();
        }
    }
    private String readRawSvgFile(int rawResId) {
        StringBuilder builder = new StringBuilder();

        try {
            InputStream inputStream = getResources().openRawResource(rawResId);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }

            reader.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return builder.toString();
    }
    private String colorRoomInSvg(String svgText, String roomId, String newFillColor) {
        String regex = "(<[^>]*id=\"" + roomId + "\"[^>]*style=\"[^\"]*fill:)(#[0-9A-Fa-f]{6})(;[^\"]*\"[^>]*>)";
        return svgText.replaceAll(regex, "$1" + newFillColor + "$3");
    }
    private String colorIntToHex(int color) {
        return String.format("#%06X", (0xFFFFFF & color));
    }

    private void showRoomDetails(String roomId) {
        RoomOccupancy room = roomMap.get(roomId);

        if (room != null) {
            selectedRoomId = roomId;

            tvSelectedRoom.setText("Room: " + room.getDisplay_name());
            tvSelectedOccupancy.setText("Occupancy: " +
                    room.getCurrent_count() + " / " + room.getMax_capacity());
            tvSelectedStatus.setText("Status: " + getOccupancyStatus(room));
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

}