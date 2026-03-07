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
import com.google.firebase.auth.FirebaseUser;
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
    private String nfcUid = null; // NFC card UID resolved from auth_uid

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

        // Null-check current user
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            goToLogin();
            return;
        }

        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("");
        }

        // Fetch student profile (name + NFC UID) by matching auth_uid in /authorized_uids
        fetchStudentProfile(user.getUid());

        // Room click listener — uses LinearLayout id in updated XML
        findViewById(R.id.roomLab101).setOnClickListener(v -> checkRoomOccupancy("lab-101"));

        // Account Overview card click
        findViewById(R.id.cvAccountOverview).setOnClickListener(v ->
                startActivity(new Intent(UserActivity.this, AccountOverviewActivity.class)));
    }

    // ── Fetch student profile from /authorized_uids by auth_uid ──
    private void fetchStudentProfile(String authUid) {
        mDatabase.child("authorized_uids")
                .orderByChild("auth_uid")
                .equalTo(authUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            // There should be exactly one match
                            for (DataSnapshot entry : snapshot.getChildren()) {
                                nfcUid = entry.getKey(); // the NFC card UID is the key

                                String name = entry.child("student_name").getValue(String.class);
                                TextView tvUserName = findViewById(R.id.tvUserName);
                                if (name != null && tvUserName != null) {
                                    tvUserName.setText(name);
                                }

                                // Now calculate stats using the NFC UID (logged in access_logs)
                                calculateUserStats(nfcUid);
                                break;
                            }
                        } else {
                            // auth_uid not linked yet — show fallback
                            TextView tvUserName = findViewById(R.id.tvUserName);
                            if (tvUserName != null) tvUserName.setText("Student");
                            Toast.makeText(UserActivity.this,
                                    "Profile not linked. Contact admin.", Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(UserActivity.this,
                                "Failed to load profile", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ── Logout menu ───────────────────────────────────────────────
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
        mAuth.signOut();
        SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
        prefs.edit().clear().apply();
        goToLogin();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ── Calculate monthly visit stats using NFC UID ───────────────
    private void calculateUserStats(String nfcUid) {
        mDatabase.child("access_logs")
                .orderByChild("uid")
                .equalTo(nfcUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int totalVisitsThisMonth = 0;
                        HashMap<String, Integer> roomCounts = new HashMap<>();

                        Calendar currentCal = Calendar.getInstance();
                        int currentMonth = currentCal.get(Calendar.MONTH);
                        int currentYear  = currentCal.get(Calendar.YEAR);

                        SimpleDateFormat sdf = new SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());

                        for (DataSnapshot log : snapshot.getChildren()) {
                            String decision     = log.child("decision").getValue(String.class);
                            String timestampStr = log.child("timestamp").getValue(String.class);

                            if ("allow".equals(decision) && timestampStr != null) {
                                try {
                                    Date logDate = sdf.parse(timestampStr);
                                    if (logDate != null) {
                                        Calendar logCal = Calendar.getInstance();
                                        logCal.setTime(logDate);

                                        if (logCal.get(Calendar.MONTH) == currentMonth
                                                && logCal.get(Calendar.YEAR) == currentYear) {
                                            totalVisitsThisMonth++;
                                            String room = log.child("lab_room").getValue(String.class);
                                            if (room != null) {
                                                roomCounts.put(room,
                                                        roomCounts.getOrDefault(room, 0) + 1);
                                            }
                                        }
                                    }
                                } catch (ParseException e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        updateStatsUI(totalVisitsThisMonth, roomCounts);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(UserActivity.this,
                                "Failed to load stats", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ── Update stats UI ───────────────────────────────────────────
    private void updateStatsUI(int totalVisits, HashMap<String, Integer> roomCounts) {
        TextView tvTotalVisits = findViewById(R.id.tvTotalVisits);
        TextView tvMostVisited = findViewById(R.id.tvMostVisited);

        if (tvTotalVisits != null) tvTotalVisits.setText(String.valueOf(totalVisits));

        if (roomCounts.isEmpty()) {
            if (tvMostVisited != null) tvMostVisited.setText("None");
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

        if (tvMostVisited != null) tvMostVisited.setText(mostVisitedRoom);
    }

    // ── Check room occupancy ──────────────────────────────────────
    private void checkRoomOccupancy(String roomId) {
        mDatabase.child("occupancy").child(roomId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            Long currentCount = snapshot.child("current_count").getValue(Long.class);
                            showRoomDialog(roomId, currentCount);
                        } else {
                            Toast.makeText(UserActivity.this,
                                    "Room data not found.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(UserActivity.this,
                                "Error fetching data.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ── Occupancy dialog ──────────────────────────────────────────
    private void showRoomDialog(String roomId, Long count) {
        new AlertDialog.Builder(this)
                .setTitle("Lab Room: " + roomId)
                .setMessage("Current Occupancy: " + count + " students")
                .setPositiveButton("Close", null)
                .show();
    }
}
