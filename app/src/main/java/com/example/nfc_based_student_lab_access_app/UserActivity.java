package com.example.nfc_based_student_lab_access_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class UserActivity extends AppCompatActivity {

    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_user);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Fetch user stats for the Account Overview card
        if (mAuth.getCurrentUser() != null) {
            calculateUserStats(mAuth.getCurrentUser().getUid());
        }

        // Setup the Toolbar (This enables the top right menu)
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(""); // Clear default title
        }

        // Setup click listener for the specific room
        TextView roomLab101 = findViewById(R.id.roomLab101);
        roomLab101.setOnClickListener(v -> checkRoomOccupancy("lab-101"));

        // Setup click listener for the Account Overview card
        androidx.cardview.widget.CardView cvAccountOverview = findViewById(R.id.cvAccountOverview);
        cvAccountOverview.setOnClickListener(v -> {
            startActivity(new Intent(UserActivity.this, AccountOverviewActivity.class));
        });
    }

    // --- LOGOUT MENU LOGIC ---
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.user_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            logoutUser();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void logoutUser() {
        // Sign out of Firebase
        mAuth.signOut();

        // Clear local session preferences
        SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
        prefs.edit().clear().apply();

        // Send user back to Login screen
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // --- CALCULATE USER STATS ---
    private void calculateUserStats(String currentUid) {
        DatabaseReference logsRef = mDatabase.child("access_logs");

        logsRef.orderByChild("uid").equalTo(currentUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int totalVisitsThisMonth = 0;
                HashMap<String, Integer> roomCounts = new HashMap<>();

                // Setup date parsing for current month/year
                Calendar currentCal = Calendar.getInstance();
                int currentMonth = currentCal.get(Calendar.MONTH);
                int currentYear = currentCal.get(Calendar.YEAR);

                // Matches your Firebase format: "2026-03-01T10:00:00"
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());

                for (DataSnapshot log : snapshot.getChildren()) {
                    String decision = log.child("decision").getValue(String.class);
                    String timestampStr = log.child("timestamp").getValue(String.class);

                    if ("allow".equals(decision) && timestampStr != null) {
                        try {
                            Date logDate = sdf.parse(timestampStr);
                            if (logDate != null) {
                                Calendar logCal = Calendar.getInstance();
                                logCal.setTime(logDate);

                                // Check if the log is from the current month and year
                                if (logCal.get(Calendar.MONTH) == currentMonth && logCal.get(Calendar.YEAR) == currentYear) {
                                    totalVisitsThisMonth++;

                                    String room = log.child("lab_room").getValue(String.class);
                                    if (room != null) {
                                        roomCounts.put(room, roomCounts.getOrDefault(room, 0) + 1);
                                    }
                                }
                            }
                        } catch (ParseException e) {
                            e.printStackTrace(); // Skip logs with invalid date formats
                        }
                    }
                }

                updateStatsUI(totalVisitsThisMonth, roomCounts);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(UserActivity.this, "Failed to load stats", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- UPDATE UI WITH STATS ---
    private void updateStatsUI(int totalVisits, HashMap<String, Integer> roomCounts) {
        TextView tvTotalVisits = findViewById(R.id.tvTotalVisits);
        TextView tvMostVisited = findViewById(R.id.tvMostVisited);

        tvTotalVisits.setText("📅 Total visits this month: " + totalVisits);

        if (roomCounts.isEmpty()) {
            tvMostVisited.setText("📍 Most visited lab: None yet");
            return;
        }

        String mostVisitedRoom = "";
        int maxVisits = 0;

        for (Map.Entry<String, Integer> entry : roomCounts.entrySet()) {
            if (entry.getValue() > maxVisits) {
                maxVisits = entry.getValue();
                mostVisitedRoom = entry.getKey();
            }
        }

        tvMostVisited.setText("📍 Most visited lab: " + mostVisitedRoom);
    }

    // --- FIREBASE DATA FETCHING ---
    private void checkRoomOccupancy(String roomId) {
        DatabaseReference roomRef = mDatabase.child("occupancy").child(roomId);

        roomRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Long currentCount = snapshot.child("current_count").getValue(Long.class);
                    showRoomDialog(roomId, currentCount);
                } else {
                    Toast.makeText(UserActivity.this, "Room data not found.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(UserActivity.this, "Error fetching data.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- DISPLAY RESULT ---
    private void showRoomDialog(String roomId, Long count) {
        new AlertDialog.Builder(this)
                .setTitle("Lab Room: " + roomId)
                .setMessage("Current Occupancy: " + count + " students")
                .setPositiveButton("Close", null)
                .show();
    }
}