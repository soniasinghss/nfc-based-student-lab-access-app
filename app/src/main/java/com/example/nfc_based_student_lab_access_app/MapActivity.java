package com.example.nfc_based_student_lab_access_app;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

public class MapActivity extends AppCompatActivity {

    private DatabaseReference occupancyRef;
    private HashMap<String, RoomOccupancy> roomMap = new HashMap<>();
    private TextView tvSelectedRoom;
    private TextView tvSelectedOccupancy;
    private TextView tvSelectedStatus;
    private String selectedRoomId = null;
    private WebView wvMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        tvSelectedRoom = findViewById(R.id.tvSelectedRoom);
        tvSelectedOccupancy = findViewById(R.id.tvSelectedOccupancy);
        tvSelectedStatus = findViewById(R.id.tvSelectedStatus);

        setupLegendGradient();
        setupWebView();

        occupancyRef = FirebaseDatabase.getInstance().getReference("occupancy");
        readRoomOccupancy();
    }

    private void setupLegendGradient() {
        View vGradientBar = findViewById(R.id.vGradientBar);
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { Color.parseColor("#006B3E"), Color.parseColor("#FBC02D"), Color.parseColor("#C62828") }
        );
        gd.setCornerRadius(12f);
        vGradientBar.setBackground(gd);
    }

    private void setupWebView() {
        wvMap = findViewById(R.id.wvMap);
        WebSettings settings = wvMap.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        wvMap.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onRoomClicked(String roomId) {
                runOnUiThread(() -> showRoomDetails(roomId));
            }
        }, "AndroidInterface");
    }

    private void readRoomOccupancy() {
        occupancyRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                roomMap.clear();

                for (DataSnapshot roomSnapshot : snapshot.getChildren()) {
                    String roomId = roomSnapshot.getKey();
                    RoomOccupancy room = roomSnapshot.getValue(RoomOccupancy.class);

                    if (room != null && roomId != null) {
                        roomMap.put(roomId, room);
                    }
                }
                loadSvg();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FIREBASE_ERROR", "Database error: " + error.getMessage());
            }
        });
    }

    private String getOccupancyStatus(RoomOccupancy room) {
        if (room.getMax_capacity() == 0) return "No Data";
        int percent = (int) Math.round(((double) room.getCurrent_count() / room.getMax_capacity()) * 100);
        return percent + "% Full";
    }

    private int getRoomColor(RoomOccupancy room) {
        if (room.getMax_capacity() == 0) return Color.parseColor("#E0E0E0");

        double percentage = (double) room.getCurrent_count() / room.getMax_capacity();
        if (percentage < 0) percentage = 0;
        if (percentage > 1) percentage = 1;

        int emptyColor = Color.parseColor("#006B3E");
        int halfColor  = Color.parseColor("#FBC02D");
        int fullColor  = Color.parseColor("#C62828");

        if (percentage <= 0.5) {
            float ratio = (float) (percentage / 0.5);
            return blendColors(emptyColor, halfColor, ratio);
        } else {
            float ratio = (float) ((percentage - 0.5) / 0.5);
            return blendColors(halfColor, fullColor, ratio);
        }
    }

    private int blendColors(int color1, int color2, float ratio) {
        float inverseRatio = 1f - ratio;
        float r = (Color.red(color1) * inverseRatio) + (Color.red(color2) * ratio);
        float g = (Color.green(color1) * inverseRatio) + (Color.green(color2) * ratio);
        float b = (Color.blue(color1) * inverseRatio) + (Color.blue(color2) * ratio);
        return Color.rgb((int) r, (int) g, (int) b);
    }

    private void loadSvg() {
        String svgText = readRawSvgFile(R.raw.h8_simple);

        for (String roomId : roomMap.keySet()) {
            RoomOccupancy room = roomMap.get(roomId);
            if (room != null) {
                int colorInt = getRoomColor(room);
                String hexColor = String.format("#%06X", (0xFFFFFF & colorInt));
                svgText = colorRoomInSvg(svgText, roomId, hexColor);
            }
        }

        String htmlContent = "<!DOCTYPE html><html>" +
                "<head>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>" +
                "<style>" +
                "  body { margin: 0; padding: 0; display: flex; justify-content: center; align-items: center; height: 100vh; width: 100vw; background-color: white; overflow: hidden; touch-action: none; }" +
                "  svg { width: 100%; height: 100%; object-fit: contain; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                svgText +
                "<script>" +
                "  document.addEventListener('DOMContentLoaded', function() {" +
                "    const svg = document.querySelector('svg');" +
                "    if (!svg) return;" +
                "    " +
                "    let origViewBox = svg.getAttribute('data-orig-viewbox');" +
                "    if (!origViewBox) {" +
                "        origViewBox = svg.getAttribute('viewBox');" +
                "        if (!origViewBox) {" +
                "            origViewBox = `0 0 ${svg.getAttribute('width') || 1000} ${svg.getAttribute('height') || 1000}`;" +
                "        }" +
                "        svg.setAttribute('data-orig-viewbox', origViewBox);" +
                "        svg.setAttribute('viewBox', origViewBox);" +
                "    }" +
                "    " +
                "    const origVals = origViewBox.split(/[ ,]+/).map(Number);" +
                "    let activeRoom = null;" +
                "    " +
                "    function animateViewBox(endBox, duration) {" +
                "        if (svg.animationId) cancelAnimationFrame(svg.animationId);" +
                "        const startBox = svg.getAttribute('viewBox').split(/[ ,]+/).map(Number);" +
                "        const start = performance.now();" +
                "        function step(time) {" +
                "            let progress = (time - start) / duration;" +
                "            if (progress > 1) progress = 1;" +
                "            let ease = progress < 0.5 ? 2 * progress * progress : -1 + (4 - 2 * progress) * progress;" +
                "            let currentBox = startBox.map((s, i) => s + (endBox[i] - s) * ease);" +
                "            svg.setAttribute('viewBox', currentBox.join(' '));" +
                "            if (progress < 1) svg.animationId = requestAnimationFrame(step);" +
                "        }" +
                "        svg.animationId = requestAnimationFrame(step);" +
                "    }" +
                "    " +
                "    document.querySelectorAll('path, rect, polygon').forEach(function(room) {" +
                "      room.addEventListener('click', function(e) {" +
                "        e.stopPropagation();" +
                "        try {" +
                "          if (!this.id) return;" +
                "          const lowerId = String(this.id).toLowerCase();" +
                "          if (lowerId.includes('background') || lowerId.includes('layer') || " +
                "              lowerId.startsWith('rect') || lowerId.startsWith('path') || lowerId.startsWith('polygon')) {" +
                "              return;" +
                "          }" +
                "          " +
                "          if (window.AndroidInterface) AndroidInterface.onRoomClicked(this.id);" +
                "          " +
                "          if (activeRoom === this.id) {" +
                "            animateViewBox(origVals, 400);" +
                "            activeRoom = null;" +
                "          } else {" +
                "            /* BULLETPROOF LETTERBOX MATH */" +
                "            const svgRect = svg.getBoundingClientRect();" +
                "            const roomRect = this.getBoundingClientRect();" +
                "            const currentBox = svg.getAttribute('viewBox').split(/[ ,]+/).map(Number);" +
                "            " +
                "            const ratioW = svgRect.width / currentBox[2];" +
                "            const ratioH = svgRect.height / currentBox[3];" +
                "            const scale = Math.min(ratioW, ratioH);" +
                "            " +
                "            const drawnW = currentBox[2] * scale;" +
                "            const drawnH = currentBox[3] * scale;" +
                "            const padX = (svgRect.width - drawnW) / 2;" +
                "            const padY = (svgRect.height - drawnH) / 2;" +
                "            " +
                "            const drawnLeft = svgRect.left + padX;" +
                "            const drawnTop = svgRect.top + padY;" +
                "            " +
                "            const roomCxScreen = roomRect.left + roomRect.width / 2;" +
                "            const roomCyScreen = roomRect.top + roomRect.height / 2;" +
                "            " +
                "            /* Map exact screen pixels to internal SVG coordinates */" +
                "            const roomCxSvg = currentBox[0] + (roomCxScreen - drawnLeft) / scale;" +
                "            const roomCySvg = currentBox[1] + (roomCyScreen - drawnTop) / scale;" +
                "            " +
                "            const zoomScale = 0.35;" + // Zooms to 35% size
                "            const targetW = origVals[2] * zoomScale;" +
                "            const targetH = origVals[3] * zoomScale;" +
                "            const targetX = roomCxSvg - targetW / 2;" +
                "            const targetY = roomCySvg - targetH / 2;" +
                "            " +
                "            animateViewBox([targetX, targetY, targetW, targetH], 400);" +
                "            activeRoom = this.id;" +
                "          }" +
                "        } catch (err) { console.error('Click Error', err); }" +
                "      });" +
                "    });" +
                "    " +
                "    svg.addEventListener('click', function() {" +
                "      if (activeRoom) {" +
                "        animateViewBox(origVals, 400);" +
                "        activeRoom = null;" +
                "      }" +
                "    });" +
                "  });" +
                "</script>" +
                "</body>" +
                "</html>";

        wvMap.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null);
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

    // Upgraded Regex to catch 3-digit hex codes, color names, or blank fills
    private String colorRoomInSvg(String svgText, String roomId, String newFillColor) {
        // Matches inline CSS (style="fill: ...;")
        String regexStyle = "(<[^>]*id=\"" + roomId + "\"[^>]*style=\"[^\"]*fill:)\\s*[^;\"\\s]+";
        String updated = svgText.replaceAll(regexStyle, "$1" + newFillColor);

        // Matches standard XML attribute (fill="...")
        String regexFill = "(<[^>]*id=\"" + roomId + "\"[^>]*fill=\")[^\"]+(\")";
        return updated.replaceAll(regexFill, "$1" + newFillColor + "$2");
    }

    private void showRoomDetails(String roomId) {
        RoomOccupancy room = roomMap.get(roomId);
        if (room != null) {
            selectedRoomId = roomId;
            tvSelectedRoom.setText("Room: " + room.getDisplay_name());
            tvSelectedOccupancy.setText("Occupancy: " +
                    room.getCurrent_count() + " / " + room.getMax_capacity());
            tvSelectedStatus.setText("Status: " + getOccupancyStatus(room));
        } else {
            tvSelectedRoom.setText("Room: " + roomId);
            tvSelectedOccupancy.setText("Occupancy: Data Unavailable");
            tvSelectedStatus.setText("Status: Unknown");
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}