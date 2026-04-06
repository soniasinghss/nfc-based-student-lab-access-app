package com.example.nfc_based_student_lab_access_app;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
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

public class ManageStudentsActivity extends AppCompatActivity {

    private EditText etUID, etStudentName, etStudentId;
    private Button btnAddUID, btnRemoveUID;
    private CheckBox cbGrantAccess;
    private TextView tvStatus, tvLastScanned, tvNfcReaderLabel;
    private MaterialButtonToggleGroup toggleGroup;
    private DatabaseReference db;
    private ValueEventListener nfcListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_students);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        db = FirebaseDatabase.getInstance().getReference();

        etUID = findViewById(R.id.etUID);
        etStudentName = findViewById(R.id.etStudentName);
        etStudentId = findViewById(R.id.etStudentId);
        cbGrantAccess = findViewById(R.id.cbGrantAccess);
        btnAddUID = findViewById(R.id.btnAddUID);
        btnRemoveUID = findViewById(R.id.btnRemoveUID);
        tvStatus = findViewById(R.id.tvStatus);
        tvLastScanned = findViewById(R.id.tvLastScanned);
        tvNfcReaderLabel = findViewById(R.id.tvNfcReaderLabel);
        toggleGroup = findViewById(R.id.toggleGroup);

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                enableAutomaticMode(checkedId == R.id.btnAuto);
            }
        });

        btnAddUID.setOnClickListener(v -> addUID());
        btnRemoveUID.setOnClickListener(v -> removeUID());
    }

    private void enableAutomaticMode(boolean auto) {
        if (auto) {
            tvNfcReaderLabel.setVisibility(View.VISIBLE);
            tvLastScanned.setVisibility(View.VISIBLE);
            etUID.setEnabled(false);
            startNfcListener();
        } else {
            tvNfcReaderLabel.setVisibility(View.GONE);
            tvLastScanned.setVisibility(View.GONE);
            etUID.setEnabled(true);
            stopNfcListener();
        }
    }

    private void startNfcListener() {
        nfcListener = db.child("NFC_setter").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String scannedUID = String.valueOf(snapshot.getValue());
                    tvLastScanned.setText("Scanned: " + scannedUID);
                    etUID.setText(scannedUID);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void stopNfcListener() {
        if (nfcListener != null) {
            db.child("NFC_setter").removeEventListener(nfcListener);
        }
    }

    private void addUID() {
        String uid = etUID.getText().toString().trim();
        String name = etStudentName.getText().toString().trim();
        String sid = etStudentId.getText().toString().trim();
        boolean hasAccess = cbGrantAccess.isChecked();

        if (uid.isEmpty() || name.isEmpty() || sid.isEmpty()) {
            tvStatus.setText("❌ Please fill in all fields");
            return;
        }

        tvStatus.setText("Checking database...");

        // NEW: Check if user already exists before adding
        db.child("authorized_uids").child(uid).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                if (task.getResult().exists()) {
                    // User already in database
                    tvStatus.setText("⚠️ Error: Student already exists with this UID");
                } else {
                    // User does not exist, proceed with enrollment
                    proceedWithEnrollment(uid, name, sid, hasAccess);
                }
            } else {
                tvStatus.setText("Database error: " + task.getException().getMessage());
            }
        });
    }

    private void proceedWithEnrollment(String uid, String name, String sid, boolean hasAccess) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(new Date());

        Map<String, Object> student = new HashMap<>();
        student.put("student_name", name);
        student.put("student_id", sid);
        student.put("added_at", timestamp);
        student.put("Access", hasAccess);
        student.put("auth_uid", "");

        db.child("authorized_uids").child(uid).setValue(student)
                .addOnSuccessListener(a -> {
                    String accessMsg = hasAccess ? "with access" : "without access";
                    tvStatus.setText("✅ " + name + " enrolled " + accessMsg);
                    clearFields();
                })
                .addOnFailureListener(e -> tvStatus.setText("Error: " + e.getMessage()));
    }

    private void removeUID() {
        String uid = etUID.getText().toString().trim();
        if (uid.isEmpty()) {
            tvStatus.setText("⚠️ Enter a UID to remove");
            return;
        }

        db.child("authorized_uids").child(uid).removeValue()
                .addOnSuccessListener(a -> {
                    tvStatus.setText("✅ Student record removed from system");
                    clearFields();
                })
                .addOnFailureListener(e -> tvStatus.setText("Error: " + e.getMessage()));
    }

    private void clearFields() {
        etUID.setText("");
        etStudentName.setText("");
        etStudentId.setText("");
        cbGrantAccess.setChecked(true);
        tvLastScanned.setText("Waiting for scan...");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopNfcListener();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}