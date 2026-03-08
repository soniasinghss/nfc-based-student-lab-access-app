package com.example.nfc_based_student_lab_access_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
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
    private String nfcUid = null;

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

        mAuth     = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) { goToLogin(); return; }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("");

        fetchStudentProfile(user.getUid());

        findViewById(R.id.roomLab101).setOnClickListener(v -> checkRoomOccupancy("lab-101"));
        findViewById(R.id.cvAccountOverview).setOnClickListener(v ->
                startActivity(new Intent(UserActivity.this, AccountOverviewActivity.class)));
    }

    // ── Fetch profile by looping manually (no index needed) ───────
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
                                if (name != null && tvUserName != null) tvUserName.setText(name);

                                calculateUserStats(nfcUid);
                                break;
                            }
                        }
                        if (!found) {
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
        if (item.getItemId() == R.id.action_logout) { logoutUser(); return true; }
        return super.onOptionsItemSelected(item);
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

    // ── Calculate monthly stats ───────────────────────────────────
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
                            String decision  = log.child("decision").getValue(String.class);
                            String timestamp = log.child("timestamp").getValue(String.class);
                            if ("allow".equals(decision) && timestamp != null) {
                                try {
                                    Date d = sdf.parse(timestamp);
                                    if (d != null) {
                                        Calendar c = Calendar.getInstance();
                                        c.setTime(d);
                                        if (c.get(Calendar.MONTH) == currentMonth
                                                && c.get(Calendar.YEAR) == currentYear) {
                                            total++;
                                            String room = log.child("lab_room").getValue(String.class);
                                            if (room != null)
                                                roomCounts.put(room, roomCounts.getOrDefault(room, 0) + 1);
                                        }
                                    }
                                } catch (ParseException e) { e.printStackTrace(); }
                            }
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

    // ── Update UI ─────────────────────────────────────────────────
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

    // ── Room occupancy ────────────────────────────────────────────
    private void checkRoomOccupancy(String roomId) {
        mDatabase.child("occupancy").child(roomId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            Long count = snapshot.child("current_count").getValue(Long.class);
                            showRoomDialog(roomId, count);
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

    private void showRoomDialog(String roomId, Long count) {
        new AlertDialog.Builder(this)
                .setTitle("Lab Room: " + roomId)
                .setMessage("Current Occupancy: " + count + " students")
                .setPositiveButton("Close", null)
                .show();
    }
}