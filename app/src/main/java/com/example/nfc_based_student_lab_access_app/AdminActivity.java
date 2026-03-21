package com.example.nfc_based_student_lab_access_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AdminActivity extends AppCompatActivity {

    EditText etUID, etStudentName, etStudentId;
    Button btnAddUID, btnRemoveUID, btnLogout;
    TextView tvStatus;
    LinearLayout llOccupancyContainer, llLogsContainer;

    DatabaseReference db;
    FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseDatabase.getInstance().getReference();

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            goToLogin();
            return;
        }

        etUID                = findViewById(R.id.etUID);
        etStudentName        = findViewById(R.id.etStudentName);
        etStudentId          = findViewById(R.id.etStudentId);
        btnAddUID            = findViewById(R.id.btnAddUID);
        btnRemoveUID         = findViewById(R.id.btnRemoveUID);
        btnLogout            = findViewById(R.id.btnLogout);
        tvStatus             = findViewById(R.id.tvStatus);
        llOccupancyContainer = findViewById(R.id.llOccupancyContainer);
        llLogsContainer      = findViewById(R.id.llLogsContainer);

        // Admin Auth Check
        String currentUID = user.getUid();
        db.child("admins").child(currentUID)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            tvStatus.setText("Access denied: Admins only");
                            btnAddUID.setEnabled(false);
                            btnRemoveUID.setEnabled(false);
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });

        // Initialize Dynamic Content
        setupOccupancyListener();
        loadAccessLogs();

        // Click Listeners
        btnAddUID.setOnClickListener(v -> addUID());
        btnRemoveUID.setOnClickListener(v -> removeUID());
        btnLogout.setOnClickListener(v -> logoutUser());
    }

    // ==========================================
    // LAB OCCUPANCY LOGIC (DYNAMIC LABS)
    // ==========================================
    private void setupOccupancyListener() {
        db.child("occupancy").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                llOccupancyContainer.removeAllViews(); // Clear previous list

                for (DataSnapshot labSnap : snapshot.getChildren()) {
                    String labKey = labSnap.getKey(); // e.g. H843, lab-101

                    Long count = labSnap.child("current_count").getValue(Long.class);
                    Long max = labSnap.child("max_capacity").getValue(Long.class);
                    String displayName = labSnap.child("display_name").getValue(String.class);

                    // Fallbacks just in case data is missing
                    if (displayName == null) displayName = labKey;
                    if (count == null) count = 0L;
                    if (max == null) max = 30L;

                    addLabToView(labKey, displayName, count, max);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void addLabToView(String labKey, String displayName, Long count, Long max) {
        // Main row container
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 16, 0, 16);

        // Lab Name (Takes up remaining space)
        TextView tvName = new TextView(this);
        tvName.setText("Lab " + displayName);
        tvName.setTextSize(14f);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setTextColor(Color.BLACK);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Helper to convert to 'dp' so it scales properly on all screen densities
        int buttonSize = (int) (40 * getResources().getDisplayMetrics().density);

        // Decrement (-) Button
        Button btnDec = new Button(this);
        btnDec.setText("-");
        btnDec.setTextSize(18f);
        btnDec.setTypeface(null, android.graphics.Typeface.BOLD);
        btnDec.setTextColor(Color.parseColor("#7B1A2E")); // Dark Red Text
        btnDec.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FFE0E0"))); // Light Red BG

        // Strip default padding so the text isn't squished
        btnDec.setPadding(0, 0, 0, 0);
        btnDec.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams decParams = new LinearLayout.LayoutParams(buttonSize, buttonSize);
        decParams.setMargins(0, 0, 16, 0);
        btnDec.setLayoutParams(decParams);
        btnDec.setOnClickListener(v -> adjustOccupancy(labKey, -1));

        // Count / Max Display
        TextView tvOcc = new TextView(this);
        tvOcc.setText(count + " / " + max);
        tvOcc.setTextSize(14f);
        tvOcc.setTypeface(null, android.graphics.Typeface.BOLD);
        tvOcc.setTextColor(Color.parseColor("#7B1A2E"));
        tvOcc.setPadding(16, 16, 16, 16);

        // Increment (+) Button
        Button btnInc = new Button(this);
        btnInc.setText("+");
        btnInc.setTextSize(18f);
        btnInc.setTypeface(null, android.graphics.Typeface.BOLD);
        btnInc.setTextColor(Color.WHITE);
        btnInc.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#7B1A2E"))); // Dark Red BG

        // Strip default padding so the text isn't squished
        btnInc.setPadding(0, 0, 0, 0);
        btnInc.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams incParams = new LinearLayout.LayoutParams(buttonSize, buttonSize);
        incParams.setMargins(16, 0, 0, 0);
        btnInc.setLayoutParams(incParams);
        btnInc.setOnClickListener(v -> adjustOccupancy(labKey, 1));

        // Assemble the row
        row.addView(tvName);
        row.addView(btnDec);
        row.addView(tvOcc);
        row.addView(btnInc);

        // Add to main container
        llOccupancyContainer.addView(row);
    }

    private void adjustOccupancy(String labKey, int change) {
        DatabaseReference countRef = db.child("occupancy").child(labKey).child("current_count");
        countRef.get().addOnSuccessListener(snapshot -> {
            Long currentCount = snapshot.getValue(Long.class);
            if (currentCount != null) {
                long newCount = currentCount + change;
                if (newCount >= 0) { // Prevent negative counts
                    countRef.setValue(newCount);
                }
            }
        });
    }

    // ==========================================
    // ACCESS LOGS LOGIC
    // ==========================================
    private void loadAccessLogs() {
        // Listen to the last 20 logs so the UI stays responsive
        db.child("access_logs").orderByChild("timestamp").limitToLast(20)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        llLogsContainer.removeAllViews();

                        if (!snapshot.exists()) {
                            TextView tvEmpty = new TextView(AdminActivity.this);
                            tvEmpty.setText("No logs found.");
                            llLogsContainer.addView(tvEmpty);
                            return;
                        }

                        for (DataSnapshot logSnap : snapshot.getChildren()) {
                            String uid = logSnap.child("uid").getValue(String.class);
                            String time = logSnap.child("timestamp").getValue(String.class);
                            String decision = logSnap.child("decision").getValue(String.class);
                            String labRoom = logSnap.child("lab_room").getValue(String.class);

                            addLogEntryToView(uid, time, decision, labRoom);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void addLogEntryToView(String uid, String time, String decision, String labRoom) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 16, 0, 16);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textContainer = new LinearLayout(this);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvDetails = new TextView(this);
        String roomDisplay = (labRoom != null) ? " - " + labRoom : "";
        tvDetails.setText("UID: " + (uid != null ? uid : "Unknown") + roomDisplay);
        tvDetails.setTextSize(14f);
        tvDetails.setTextColor(Color.BLACK);
        tvDetails.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvTime = new TextView(this);
        String displayTime = (time != null) ? time.replace("T", " ") : "Unknown Time";
        tvTime.setText(displayTime);
        tvTime.setTextSize(12f);
        tvTime.setTextColor(Color.parseColor("#888888"));

        textContainer.addView(tvDetails);
        textContainer.addView(tvTime);

        TextView tvStatusBadge = new TextView(this);
        tvStatusBadge.setPadding(24, 8, 24, 8);
        tvStatusBadge.setTextSize(12f);
        tvStatusBadge.setTypeface(null, android.graphics.Typeface.BOLD);

        if ("allow".equalsIgnoreCase(decision)) {
            tvStatusBadge.setText("ALLOWED");
            tvStatusBadge.setTextColor(Color.parseColor("#2E7D32"));
            tvStatusBadge.setBackgroundColor(Color.parseColor("#E8F5E9"));
        } else {
            tvStatusBadge.setText("DENIED");
            tvStatusBadge.setTextColor(Color.parseColor("#C62828"));
            tvStatusBadge.setBackgroundColor(Color.parseColor("#FFEBEE"));
        }

        row.addView(textContainer);
        row.addView(tvStatusBadge);

        // Add to index 0 so newest logs appear at the top
        llLogsContainer.addView(row, 0);
    }

    // ==========================================
    // USER MANAGEMENT LOGIC
    // ==========================================
    private void addUID() {
        String uid  = etUID.getText().toString().trim();
        String name = etStudentName.getText().toString().trim();
        String sid  = etStudentId.getText().toString().trim();

        if (uid.isEmpty() || name.isEmpty() || sid.isEmpty()) {
            tvStatus.setText("Please fill in all fields");
            return;
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                .format(new Date());

        Map<String, Object> student = new HashMap<>();
        student.put("student_name", name);
        student.put("student_id",   sid);
        student.put("added_at",     timestamp);
        student.put("auth_uid", "");

        db.child("authorized_uids").child(uid).setValue(student)
                .addOnSuccessListener(a -> {
                    tvStatus.setText("✅ Student added successfully");
                    etUID.setText("");
                    etStudentName.setText("");
                    etStudentId.setText("");
                })
                .addOnFailureListener(e -> tvStatus.setText("Error: " + e.getMessage()));
    }

    private void removeUID() {
        String uid = etUID.getText().toString().trim();
        if (uid.isEmpty()) {
            tvStatus.setText("Please enter a UID");
            return;
        }
        db.child("authorized_uids").child(uid).removeValue()
                .addOnSuccessListener(a -> {
                    tvStatus.setText("✅ Student removed successfully");
                    etUID.setText("");
                })
                .addOnFailureListener(e -> tvStatus.setText("Error: " + e.getMessage()));
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
}