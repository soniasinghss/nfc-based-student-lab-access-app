package com.example.nfc_based_student_lab_access_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class LoginActivity extends AppCompatActivity {

    EditText etEmail, etPassword;
    Button btnLogin;
    TextView tvError;
    CheckBox cbKeepSignedIn;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if user is already logged in
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
        boolean keepSignedIn = prefs.getBoolean("keepSignedIn", false);

        if (mAuth.getCurrentUser() != null && keepSignedIn) {
            // Already logged in — figure out where to send them
            routeUser(mAuth.getCurrentUser().getUid());
            return;
        }

        setContentView(R.layout.activity_login);

        etEmail       = findViewById(R.id.etEmail);
        etPassword    = findViewById(R.id.etPassword);
        btnLogin      = findViewById(R.id.btnLogin);
        tvError       = findViewById(R.id.tvError);
        cbKeepSignedIn = findViewById(R.id.cbKeepSignedIn);

        btnLogin.setOnClickListener(v -> validateAndLogin());
    }

    // ── Validation ────────────────────────────────────────────────
    private void validateAndLogin() {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty()) {
            showError("Email is required");
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Invalid email format");
            return;
        }
        if (password.isEmpty()) {
            showError("Password is required");
            return;
        }

        loginWithFirebase(email, password);
    }

    // ── Firebase Auth ─────────────────────────────────────────────
    private void loginWithFirebase(String email, String password) {
        btnLogin.setEnabled(false);
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();

                    // Save session preference
                    SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
                    prefs.edit()
                            .putString("uid", uid)
                            .putBoolean("keepSignedIn", cbKeepSignedIn.isChecked())
                            .apply();

                    routeUser(uid);
                })
                .addOnFailureListener(e -> {
                    btnLogin.setEnabled(true);
                    showError("Login failed: " + e.getMessage());
                });
    }

    // ── Route: Admin or Student ───────────────────────────────────
    private void routeUser(String uid) {
        // First check if UID is in /admins
        mDatabase.child("admins").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            // → Admin
                            goTo(AdminActivity.class);
                        } else {
                            // Not admin — check if they're an authorized student
                            checkIfStudent(uid);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        btnLogin.setEnabled(true);
                        showError("Database error: " + error.getMessage());
                    }
                });
    }

    private void checkIfStudent(String uid) {
        // Search /authorized_uids for an entry where auth_uid == uid
        mDatabase.child("authorized_uids")
                .orderByChild("auth_uid")
                .equalTo(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            // → Student
                            goTo(UserActivity.class);
                        } else {
                            // Neither admin nor authorized student
                            mAuth.signOut();
                            btnLogin.setEnabled(true);
                            showError("Access denied: your account is not authorized.");
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        btnLogin.setEnabled(true);
                        showError("Database error: " + error.getMessage());
                    }
                });
    }

    // ── Helpers ───────────────────────────────────────────────────
    private void goTo(Class<?> destination) {
        Intent intent = new Intent(this, destination);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showError(String message) {
        if (tvError != null) {
            tvError.setVisibility(View.VISIBLE);
            tvError.setText(message);
        }
    }
}