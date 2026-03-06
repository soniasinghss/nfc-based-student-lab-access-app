package com.example.nfc_based_student_lab_access_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

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
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        db = FirebaseDatabase.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();

        // Setup the Toolbar for the logout menu
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(""); // Clear default title

        etUID = findViewById(R.id.etUID);
        btnAddUID = findViewById(R.id.btnAddUID);
        btnRemoveUID = findViewById(R.id.btnRemoveUID);
        tvStatus = findViewById(R.id.tvStatus);

        // ADMIN-1.1.3 - Restrict to admins only
        if (mAuth.getCurrentUser() != null) {
            String currentUID = mAuth.getCurrentUser().getUid();
            db.child("admins").child(currentUID).addListenerForSingleValueEvent(new ValueEventListener() {
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
        }

        btnAddUID.setOnClickListener(v -> addUID());
        btnRemoveUID.setOnClickListener(v -> removeUID());
    }

    // --- LOGOUT MENU LOGIC ---
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.user_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            logoutAdmin();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void logoutAdmin() {
        // Sign out of Firebase
        mAuth.signOut();

        // Clear local session preferences
        SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
        prefs.edit().clear().apply();

        // Send user back to Login screen
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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