package com.example.nfc_based_student_lab_access_app;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
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

public class AccountOverviewActivity extends AppCompatActivity {

    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_account_overview);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Setup toolbar with back button
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }

        mAuth     = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            finish();
            return;
        }

        fetchStudentProfile(user.getUid());
    }

    // ── Back button ───────────────────────────────────────────────
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── Fetch profile from /authorized_uids by auth_uid ──────────
    private void fetchStudentProfile(String authUid) {
        mDatabase.child("authorized_uids")
                .orderByChild("auth_uid")
                .equalTo(authUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot entry : snapshot.getChildren()) {
                                String nfcUid = entry.getKey();
                                String name   = entry.child("student_name").getValue(String.class);
                                Object sidRaw = entry.child("student_id").getValue();
                                String sid = sidRaw != null ? String.valueOf(sidRaw) : null;
                                String addedAt = entry.child("added_at").getValue(String.class);

                                // Avatar initial
                                TextView tvAvatar = findViewById(R.id.tvAvatarInitial);
                                if (name != null && !name.isEmpty() && tvAvatar != null) {
                                    tvAvatar.setText(String.valueOf(name.charAt(0)).toUpperCase());
                                }

                                // Name
                                TextView tvName = findViewById(R.id.tvAccountName);
                                if (tvName != null) tvName.setText(name != null ? name : "Unknown");

                                // Student ID
                                TextView tvSid = findViewById(R.id.tvAccountStudentId);
                                if (tvSid != null) tvSid.setText("ID: " + (sid != null ? sid : "—"));

                                // NFC UID
                                TextView tvNfc = findViewById(R.id.tvAccountNfcUid);
                                if (tvNfc != null) tvNfc.setText(nfcUid != null ? nfcUid : "—");

                                // Added at
                                TextView tvAdded = findViewById(R.id.tvAccountAddedAt);
                                if (tvAdded != null) tvAdded.setText(addedAt != null ? addedAt : "—");

                                // Now fetch stats using NFC UID
                                if (nfcUid != null) fetchStats(nfcUid);
                                break;
                            }
                        } else {
                            Toast.makeText(AccountOverviewActivity.this,
                                    "Profile not linked. Contact admin.", Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(AccountOverviewActivity.this,
                                "Failed to load profile", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ── Fetch monthly stats using NFC UID ─────────────────────────
    private void fetchStats(String nfcUid) {
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

                        // Update stats UI
                        TextView tvTotal = findViewById(R.id.tvTotalVisits);
                        if (tvTotal != null) tvTotal.setText(String.valueOf(total));

                        TextView tvMost = findViewById(R.id.tvMostVisited);
                        if (tvMost != null) {
                            if (roomCounts.isEmpty()) {
                                tvMost.setText("None");
                            } else {
                                String best = "";
                                int max = 0;
                                for (Map.Entry<String, Integer> e : roomCounts.entrySet()) {
                                    if (e.getValue() > max) {
                                        max  = e.getValue();
                                        best = e.getKey();
                                    }
                                }
                                tvMost.setText(best);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(AccountOverviewActivity.this,
                                "Failed to load stats", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}