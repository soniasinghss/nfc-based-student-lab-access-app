package com.example.nfc_based_student_lab_access_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UserActivity extends AppCompatActivity {

    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;
    private String nfcUid = null;
    private LinearLayout llLabRooms;
    private EditText etSearchBox;
    private List<String> allLabRooms = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user);

        mAuth     = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) { goToLogin(); return; }

        llLabRooms  = findViewById(R.id.llLabRooms);
        etSearchBox = findViewById(R.id.etSearchBox);

        fetchStudentProfile(user.getUid());

        // Sign out button
        findViewById(R.id.btnSignOut).setOnClickListener(v -> logoutUser());

        // Account overview click
        findViewById(R.id.cvAccountOverview).setOnClickListener(v ->
                startActivity(new Intent(UserActivity.this, AccountOverviewActivity.class)));

        // NFC Card click
        findViewById(R.id.cvNfcCard).setOnClickListener(v ->
                startActivity(new Intent(UserActivity.this, NfcCardActivity.class)));

        // Occupancy live listener
        mDatabase.child("occupancy").child("lab-101")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Long count = snapshot.child("current_count").getValue(Long.class);
                        Long max   = snapshot.child("max_capacity").getValue(Long.class);
                        TextView tvOccupancy = findViewById(R.id.tvOccupancy);
                        if (tvOccupancy != null && count != null && max != null) {
                            tvOccupancy.setText(count + " / " + max);
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });

        // Load lab rooms dynamically
        loadLabRooms();

        // Search filter
        etSearchBox.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterLabRooms(s.toString().toLowerCase().trim());
            }
        });
    }

    private void loadLabRooms() {
        mDatabase.child("occupancy").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allLabRooms.clear();
                for (DataSnapshot room : snapshot.getChildren()) {
                    allLabRooms.add(room.getKey());
                }
                filterLabRooms("");
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void filterLabRooms(String query) {
        llLabRooms.removeAllViews();
        for (String roomId : allLabRooms) {
            if (query.isEmpty() || roomId.toLowerCase().contains(query)) {
                addRoomCard(roomId);
            }
        }
    }

    private void addRoomCard(String roomId) {
        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 12);
        card.setLayoutParams(cardParams);
        card.setRadius(24f);
        card.setCardElevation(6f);
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(40, 40, 40, 40);

        // Green dot
        android.view.View dot = new android.view.View(this);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(24, 24);
        dotParams.setMargins(0, 0, 36, 0);
        dot.setLayoutParams(dotParams);
        dot.setBackgroundColor(Color.parseColor("#22C55E"));

        // Room info
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView tvName = new TextView(this);
        tvName.setText(roomId.toUpperCase());
        tvName.setTextSize(15);
        tvName.setTextColor(Color.parseColor("#1A1A1A"));
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvSub = new TextView(this);
        tvSub.setText("Tap to check occupancy");
        tvSub.setTextSize(12);
        tvSub.setTextColor(Color.parseColor("#888888"));

        info.addView(tvName);
        info.addView(tvSub);

        // Arrow
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(22);
        arrow.setTextColor(Color.parseColor("#CCCCCC"));

        row.addView(dot);
        row.addView(info);
        row.addView(arrow);
        card.addView(row);

        card.setOnClickListener(v -> checkRoomOccupancy(roomId));
        llLabRooms.addView(card);
    }

    // Fetch profile by matching auth_uid
    private void fetchStudentProfile(String authUid) {
        mDatabase.child("authorized_uids")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean found = false;
                        for (DataSnapshot entry : snapshot.getChildren()) {
                            String storedAuthUid = entry.child("auth_uid").getValue(String.class);
                            if (authUid.equals(storedAuthUid)) {
                                found  = true;
                                nfcUid = entry.getKey();

                                String name = entry.child("student_name").getValue(String.class);
                                TextView tvUserName = findViewById(R.id.tvUserName);
                                if (name != null && tvUserName != null)
                                    tvUserName.setText(name);

                                TextView tvNfcUid = findViewById(R.id.tvNfcUid);
                                if (tvNfcUid != null && nfcUid != null)
                                    tvNfcUid.setText("Card UID: " + nfcUid);

                                calculateUserStats(nfcUid);
                                break;
                            }
                        }
                        if (!found) {
                            Toast.makeText(UserActivity.this,
                                    "Profile not linked. Contact admin.",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(UserActivity.this,
                                "Failed to load profile", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Calculate monthly stats
    private void calculateUserStats(String nfcUid) {
        mDatabase.child("access_logs")
                .orderByChild("uid")
                .equalTo(nfcUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int total = 0;
                        HashMap<String, Integer> roomCounts = new HashMap<>();

                        Calendar now = Calendar.getInstance();
                        int currentMonth = now.get(Calendar.MONTH);
                        int currentYear  = now.get(Calendar.YEAR);
                        SimpleDateFormat sdf = new SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());

                        for (DataSnapshot log : snapshot.getChildren()) {
                            String decision = log.child("decision").getValue(String.class);
                            Object tsRaw    = log.child("timestamp").getValue();

                            if (!"allow".equals(decision) || tsRaw == null) continue;

                            try {
                                Calendar c = Calendar.getInstance();
                                if (tsRaw instanceof Long) {
                                    c.setTimeInMillis((Long) tsRaw);
                                } else {
                                    Date d = sdf.parse(String.valueOf(tsRaw));
                                    if (d == null) continue;
                                    c.setTime(d);
                                }
                                if (c.get(Calendar.MONTH) == currentMonth
                                        && c.get(Calendar.YEAR) == currentYear) {
                                    total++;
                                    String room = log.child("lab_room").getValue(String.class);
                                    if (room != null)
                                        roomCounts.put(room, roomCounts.getOrDefault(room, 0) + 1);
                                }
                            } catch (ParseException e) { e.printStackTrace(); }
                        }
                        updateStatsUI(total, roomCounts);
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(UserActivity.this,
                                "Failed to load stats", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateStatsUI(int totalVisits, HashMap<String, Integer> roomCounts) {
        TextView tvTotalVisits = findViewById(R.id.tvTotalVisits);
        TextView tvMostVisited = findViewById(R.id.tvMostVisited);

        if (tvTotalVisits != null) tvTotalVisits.setText(String.valueOf(totalVisits));

        if (roomCounts.isEmpty()) {
            if (tvMostVisited != null) tvMostVisited.setText("None");
            return;
        }

        String best = ""; int max = 0;
        for (Map.Entry<String, Integer> e : roomCounts.entrySet()) {
            if (e.getValue() > max) { max = e.getValue(); best = e.getKey(); }
        }
        if (tvMostVisited != null) tvMostVisited.setText(best);
    }

    private void checkRoomOccupancy(String roomId) {
        mDatabase.child("occupancy").child(roomId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            Long count = snapshot.child("current_count").getValue(Long.class);
                            Long max   = snapshot.child("max_capacity").getValue(Long.class);
                            new AlertDialog.Builder(UserActivity.this)
                                    .setTitle("Lab Room: " + roomId)
                                    .setMessage("Current Occupancy: " + count + " / " + max)
                                    .setPositiveButton("Close", null)
                                    .show();
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

    private void logoutUser() {
        mAuth.signOut();
        getSharedPreferences("session", MODE_PRIVATE).edit().clear().apply();
        goToLogin();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}