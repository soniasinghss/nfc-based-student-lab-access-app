package com.example.nfc_based_student_lab_access_app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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

    private static final int NOTIF_PERMISSION_CODE = 101;
    private static final String PREFS_SUBSCRIPTIONS = "lab_subscriptions";

    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;
    private String nfcUid = null;
    private LinearLayout llLabRooms;
    private EditText etSearchBox;
    private List<String> allLabRooms = new ArrayList<>();
    private Map<String, Boolean> labWasFull = new HashMap<>();
    private Map<String, ValueEventListener> labListeners = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            goToLogin();
            return;
        }

        NotificationHelper.createChannel(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIF_PERMISSION_CODE
                );
            }
        }

        llLabRooms = findViewById(R.id.llLabRooms);
        etSearchBox = findViewById(R.id.etSearchBox);

        fetchStudentProfile(user.getUid());

        findViewById(R.id.btnSignOut).setOnClickListener(v -> logoutUser());

        findViewById(R.id.cvAccountOverview).setOnClickListener(v ->
                startActivity(new Intent(UserActivity.this, AccountOverviewActivity.class)));

        findViewById(R.id.cvMap).setOnClickListener(v ->
                startActivity(new Intent(UserActivity.this, MapActivity.class)));

        loadLabRooms();

        etSearchBox.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterLabRooms(s.toString().toLowerCase().trim());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (Map.Entry<String, ValueEventListener> entry : labListeners.entrySet()) {
            mDatabase.child("occupancy").child(entry.getKey())
                    .removeEventListener(entry.getValue());
        }
    }

    private void loadLabRooms() {
        mDatabase.child("occupancy").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allLabRooms.clear();
                for (DataSnapshot room : snapshot.getChildren()) {
                    String roomId = room.getKey();
                    if (roomId != null) {
                        allLabRooms.add(roomId);
                    }
                }
                llLabRooms.removeAllViews();
                llLabRooms.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void filterLabRooms(String query) {
        for (Map.Entry<String, ValueEventListener> entry : labListeners.entrySet()) {
            mDatabase.child("occupancy").child(entry.getKey())
                    .removeEventListener(entry.getValue());
        }
        labListeners.clear();
        llLabRooms.removeAllViews();

        if (query.isEmpty()) {
            llLabRooms.setVisibility(View.GONE);
            return;
        }

        boolean foundMatch = false;

        for (String roomId : allLabRooms) {
            if (roomId != null && roomId.toLowerCase().contains(query)) {
                addRoomCard(roomId);
                foundMatch = true;
            }
        }

        llLabRooms.setVisibility(foundMatch ? View.VISIBLE : View.GONE);
    }

    private void addRoomCard(String roomId) {
        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 12);
        card.setLayoutParams(cardParams);
        card.setRadius(24f);
        card.setCardElevation(6f);
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(40, 40, 40, 40);

        View dot = new View(this);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(24, 24);
        dotParams.setMargins(0, 0, 36, 0);
        dot.setLayoutParams(dotParams);
        dot.setBackgroundColor(Color.parseColor("#22C55E"));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1
        ));

        TextView tvName = new TextView(this);
        tvName.setText(roomId.toUpperCase());
        tvName.setTextSize(15);
        tvName.setTextColor(Color.parseColor("#1A1A1A"));
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvOccupancy = new TextView(this);
        tvOccupancy.setText("Occupancy: loading...");
        tvOccupancy.setTextSize(12);
        tvOccupancy.setTextColor(Color.parseColor("#888888"));

        info.addView(tvName);
        info.addView(tvOccupancy);

        TextView tvBell = new TextView(this);
        tvBell.setText("🔔");
        tvBell.setTextSize(20);
        tvBell.setVisibility(View.VISIBLE);
        tvBell.setPadding(16, 0, 0, 0);
        updateBellState(tvBell, roomId);
        tvBell.setOnClickListener(v -> toggleSubscription(roomId, tvBell));

        row.addView(dot);
        row.addView(info);
        row.addView(tvBell);
        card.addView(row);

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Long count = snapshot.child("current_count").getValue(Long.class);
                Long max = snapshot.child("max_capacity").getValue(Long.class);

                if (count != null && max != null) {
                    tvOccupancy.setText("Occupancy: " + count + " / " + max);

                    boolean isFull = count >= max;
                    dot.setBackgroundColor(isFull
                            ? Color.parseColor("#EF4444")
                            : Color.parseColor("#22C55E"));

                    boolean wasFullBefore = Boolean.TRUE.equals(labWasFull.get(roomId));
                    boolean isSubscribed = isSubscribed(roomId);

                    if (wasFullBefore && !isFull && isSubscribed) {
                        NotificationHelper.sendLabAvailableNotification(
                                UserActivity.this, roomId
                        );
                        unsubscribe(roomId);
                        updateBellState(tvBell, roomId);
                        Toast.makeText(
                                UserActivity.this,
                                roomId.toUpperCase() + " has space — unsubscribed",
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                    labWasFull.put(roomId, isFull);
                } else {
                    tvOccupancy.setText("Occupancy: unavailable");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvOccupancy.setText("Occupancy: error");
            }
        };

        mDatabase.child("occupancy").child(roomId).addValueEventListener(listener);
        labListeners.put(roomId, listener);

        card.setOnClickListener(v -> checkRoomOccupancy(roomId));
        llLabRooms.addView(card);
    }

    private SharedPreferences getSubPrefs() {
        return getSharedPreferences(PREFS_SUBSCRIPTIONS, MODE_PRIVATE);
    }

    private boolean isSubscribed(String roomId) {
        return getSubPrefs().getBoolean(roomId, false);
    }

    private void subscribe(String roomId) {
        getSubPrefs().edit().putBoolean(roomId, true).apply();
    }

    private void unsubscribe(String roomId) {
        getSubPrefs().edit().putBoolean(roomId, false).apply();
    }

    private void updateBellState(TextView tvBell, String roomId) {
        tvBell.setAlpha(isSubscribed(roomId) ? 1.0f : 0.4f);
    }

    private void toggleSubscription(String roomId, TextView tvBell) {
        if (isSubscribed(roomId)) {
            unsubscribe(roomId);
            Toast.makeText(this,
                    "Unsubscribed from " + roomId.toUpperCase(),
                    Toast.LENGTH_SHORT).show();
        } else {
            subscribe(roomId);
            Toast.makeText(this,
                    "You'll be notified when " + roomId.toUpperCase() + " has space",
                    Toast.LENGTH_SHORT).show();
        }
        updateBellState(tvBell, roomId);
    }

    private void fetchStudentProfile(String authUid) {
        mDatabase.child("authorized_uids")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean found = false;

                        for (DataSnapshot entry : snapshot.getChildren()) {
                            String storedAuthUid = entry.child("auth_uid").getValue(String.class);

                            if (authUid.equals(storedAuthUid)) {
                                found = true;
                                nfcUid = entry.getKey();

                                String name = entry.child("student_name").getValue(String.class);
                                TextView tvUserName = findViewById(R.id.tvUserName);
                                if (name != null && tvUserName != null) {
                                    tvUserName.setText(name);
                                }

                                if (nfcUid != null && !nfcUid.trim().isEmpty()) {
                                    calculateUserStats(nfcUid);
                                } else {
                                    updateStatsUI(0, new HashMap<>());
                                }

                                break;
                            }
                        }

                        if (!found) {
                            Toast.makeText(UserActivity.this,
                                    "Profile not linked. Contact admin.",
                                    Toast.LENGTH_LONG).show();
                            updateStatsUI(0, new HashMap<>());
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(UserActivity.this,
                                "Failed to load profile",
                                Toast.LENGTH_SHORT).show();
                        updateStatsUI(0, new HashMap<>());
                    }
                });
    }

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
                        int currentYear = now.get(Calendar.YEAR);

                        SimpleDateFormat sdf = new SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss",
                                Locale.getDefault()
                        );

                        for (DataSnapshot log : snapshot.getChildren()) {
                            String decision = log.child("decision").getValue(String.class);
                            Object tsRaw = log.child("timestamp").getValue();
                            String room = log.child("lab_room").getValue(String.class);

                            if (!"allow".equalsIgnoreCase(decision) || tsRaw == null) {
                                continue;
                            }

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

                                    if (room != null && !room.trim().isEmpty()) {
                                        total++;
                                        roomCounts.put(room, roomCounts.getOrDefault(room, 0) + 1);
                                    }
                                }

                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                        }

                        updateStatsUI(total, roomCounts);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(UserActivity.this,
                                "Failed to load stats",
                                Toast.LENGTH_SHORT).show();
                        updateStatsUI(0, new HashMap<>());
                    }
                });
    }

    private void updateStatsUI(int totalVisits, HashMap<String, Integer> roomCounts) {
        TextView tvTotalVisits = findViewById(R.id.tvTotalVisits);
        TextView tvMostVisited = findViewById(R.id.tvMostVisited);

        if (tvTotalVisits != null) {
            tvTotalVisits.setText(String.valueOf(totalVisits));
        }

        if (tvMostVisited == null) return;

        if (roomCounts == null || roomCounts.isEmpty()) {
            tvMostVisited.setText("None");
            return;
        }

        String best = "";
        int max = 0;

        for (Map.Entry<String, Integer> e : roomCounts.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                best = e.getKey();
            }
        }

        tvMostVisited.setText(best.isEmpty() ? "None" : best);
    }

    private void checkRoomOccupancy(String roomId) {
        Intent intent = new Intent(this, LabDetailActivity.class);
        intent.putExtra("room_id", roomId);
        startActivity(intent);
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