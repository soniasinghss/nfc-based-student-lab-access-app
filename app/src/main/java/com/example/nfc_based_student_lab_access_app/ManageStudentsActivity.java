package com.example.nfc_based_student_lab_access_app;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
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
        btnAddUID = findViewById(R.id.btnAddUID);
        btnRemoveUID = findViewById(R.id.btnRemoveUID);
        tvStatus = findViewById(R.id.tvStatus);
        tvLastScanned = findViewById(R.id.tvLastScanned);
        tvNfcReaderLabel = findViewById(R.id.tvNfcReaderLabel);
        toggleGroup = findViewById(R.id.toggleGroup);

        // Handle Mode Switching
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
            etUID.setEnabled(false); // Stop manual typing
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
                    String scannedUID = snapshot.getValue(String.class);
                    tvLastScanned.setText("Scanned UID: " + scannedUID);
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

        if (uid.isEmpty() || name.isEmpty() || sid.isEmpty()) {
            tvStatus.setText("❌ Please fill in all fields");
            return;
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(new Date());

        Map<String, Object> student = new HashMap<>();
        student.put("student_name", name);
        student.put("student_id", sid);
        student.put("added_at", timestamp);

        db.child("authorized_uids").child(uid).setValue(student)
                .addOnSuccessListener(a -> {
                    tvStatus.setText("✅ " + name + " enrolled successfully");
                    clearFields();
                })
                .addOnFailureListener(e -> tvStatus.setText("Error: " + e.getMessage()));
    }

    private void removeUID() {
        String uid = etUID.getText().toString().trim();
        if (uid.isEmpty()) {
            tvStatus.setText("⚠️ Select a student/UID to remove");
            return;
        }

        db.child("authorized_uids").child(uid).removeValue()
                .addOnSuccessListener(a -> {
                    tvStatus.setText("✅ UID " + uid + " removed from system");
                    clearFields();
                })
                .addOnFailureListener(e -> tvStatus.setText("Error: " + e.getMessage()));
    }

    private void clearFields() {
        etUID.setText("");
        etStudentName.setText("");
        etStudentId.setText("");
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