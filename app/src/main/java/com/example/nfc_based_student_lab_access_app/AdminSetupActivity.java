package com.example.nfc_based_student_lab_access_app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class AdminSetupActivity extends AppCompatActivity {

    private EditText etNewAdminEmail, etNewAdminPassword, etDeleteAdminEmail;
    private Button btnCreateAdmin, btnDeleteAdmin;
    private TextView tvAdminStatus;

    private FirebaseAuth auth;
    private DatabaseReference db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_admin_setup);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Setup Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseDatabase.getInstance().getReference();

        // UI Binding
        etNewAdminEmail = findViewById(R.id.etNewAdminEmail);
        etNewAdminPassword = findViewById(R.id.etNewAdminPassword);
        etDeleteAdminEmail = findViewById(R.id.etDeleteAdminEmail);
        btnCreateAdmin = findViewById(R.id.btnCreateAdmin);
        btnDeleteAdmin = findViewById(R.id.btnDeleteAdmin);
        tvAdminStatus = findViewById(R.id.tvAdminStatus);

        btnCreateAdmin.setOnClickListener(v -> createNewAdmin());
        btnDeleteAdmin.setOnClickListener(v -> revokeAdminAccess());
    }

    private void createNewAdmin() {
        String email = etNewAdminEmail.getText().toString().trim();
        String password = etNewAdminPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            tvAdminStatus.setText("❌ Please provide both email and password.");
            return;
        }
        if (password.length() < 6) {
            tvAdminStatus.setText("❌ Password must be at least 6 characters.");
            return;
        }

        tvAdminStatus.setText("Creating admin profile...");
        btnCreateAdmin.setEnabled(false);

        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            btnCreateAdmin.setEnabled(true);
            if (task.isSuccessful()) {
                String newUid = task.getResult().getUser().getUid();

                // 1. Give them access in the 'admins' node
                db.child("admins").child(newUid).setValue(true);

                // 2. Create a lookup table so we can delete them by email later
                // Firebase keys cannot contain '.' so we replace it with ','
                String safeEmail = email.replace(".", ",");
                db.child("admin_emails").child(safeEmail).setValue(newUid);

                tvAdminStatus.setText("✅ Admin created successfully!");
                etNewAdminEmail.setText("");
                etNewAdminPassword.setText("");

            } else {
                tvAdminStatus.setText("Error: " + task.getException().getMessage());
            }
        });
    }

    private void revokeAdminAccess() {
        String email = etDeleteAdminEmail.getText().toString().trim();

        if (email.isEmpty()) {
            tvAdminStatus.setText("❌ Please enter the email to remove.");
            return;
        }

        tvAdminStatus.setText("Looking up admin...");
        btnDeleteAdmin.setEnabled(false);

        // Retrieve the UID using our lookup table
        String safeEmail = email.replace(".", ",");
        db.child("admin_emails").child(safeEmail).get().addOnCompleteListener(task -> {
            btnDeleteAdmin.setEnabled(true);
            if (task.isSuccessful() && task.getResult().exists()) {
                String uidToRemove = task.getResult().getValue(String.class);

                if (uidToRemove != null) {
                    // Revoke access by removing them from the 'admins' node
                    db.child("admins").child(uidToRemove).removeValue();

                    // Remove from lookup table to keep database clean
                    db.child("admin_emails").child(safeEmail).removeValue();

                    tvAdminStatus.setText("✅ Admin privileges revoked for " + email);
                    etDeleteAdminEmail.setText("");
                }
            } else {
                tvAdminStatus.setText("⚠️ Could not find an admin with that email.");
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}