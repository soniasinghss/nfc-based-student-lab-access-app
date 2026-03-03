package com.example.nfc_based_student_lab_access_app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AdminActivity extends AppCompatActivity {

    EditText etUID;
    Button btnAddUID, btnRemoveUID;
    TextView tvStatus;
    DatabaseReference db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        db = FirebaseDatabase.getInstance().getReference();

        etUID = findViewById(R.id.etUID);
        btnAddUID = findViewById(R.id.btnAddUID);
        btnRemoveUID = findViewById(R.id.btnRemoveUID);
        tvStatus = findViewById(R.id.tvStatus);

        // ADMIN-1.1.3 - Restrict to admins only
        String currentUID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db.child("admins").child(currentUID).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    tvStatus.setText("Access denied: Admins only");
                    btnAddUID.setEnabled(false);
                    btnRemoveUID.setEnabled(false);
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        });

        btnAddUID.setOnClickListener(v -> addUID());
        btnRemoveUID.setOnClickListener(v -> removeUID());
    }

    // ADMIN-1.1.4 - Add UID
    private void addUID() {
        String uid = etUID.getText().toString().trim();
        if (uid.isEmpty()) {
            tvStatus.setText("Please enter a UID");
            return;
        }
        db.child("authorized_uids").child(uid).setValue(true)
                .addOnSuccessListener(a -> tvStatus.setText("UID added successfully"))
                .addOnFailureListener(e -> tvStatus.setText("Error: " + e.getMessage()));
    }

    // ADMIN-1.1.2 - Remove UID
    private void removeUID() {
        String uid = etUID.getText().toString().trim();
        if (uid.isEmpty()) {
            tvStatus.setText("Please enter a UID");
            return;
        }
        db.child("authorized_uids").child(uid).removeValue()
                .addOnSuccessListener(a -> tvStatus.setText("UID removed successfully"))
                .addOnFailureListener(e -> tvStatus.setText("Error: " + e.getMessage()));
    }
}