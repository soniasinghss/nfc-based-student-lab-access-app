package com.example.nfc_based_student_lab_access_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
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
    DatabaseReference db;
    FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseDatabase.getInstance().getReference();

        // Null-check current user
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            goToLogin();
            return;
        }

        // Bind views
        etUID         = findViewById(R.id.etUID);
        etStudentName = findViewById(R.id.etStudentName);
        etStudentId   = findViewById(R.id.etStudentId);
        btnAddUID     = findViewById(R.id.btnAddUID);
        btnRemoveUID  = findViewById(R.id.btnRemoveUID);
        btnLogout     = findViewById(R.id.btnLogout);
        tvStatus      = findViewById(R.id.tvStatus);

        // ADMIN-1.1.3 — Restrict to admins only
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

        btnAddUID.setOnClickListener(v -> addUID());
        btnRemoveUID.setOnClickListener(v -> removeUID());
        btnLogout.setOnClickListener(v -> logoutUser());
    }

    // ── Add UID with full student object ─────────────────────────
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
        // auth_uid is empty until the student creates a Firebase Auth account
        // Admin can update this later or it gets linked on first login
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

    // ── Remove UID ────────────────────────────────────────────────
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

    // ── Logout ────────────────────────────────────────────────────
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