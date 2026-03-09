package com.example.nfc_based_student_lab_access_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
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
    TextView tvRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login); // ALWAYS call this first

        mAuth     = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        etEmail        = findViewById(R.id.etEmail);
        etPassword     = findViewById(R.id.etPassword);
        btnLogin       = findViewById(R.id.btnLogin);
        tvError        = findViewById(R.id.tvError);
        cbKeepSignedIn = findViewById(R.id.cbKeepSignedIn);
        tvRegister     = findViewById(R.id.tvRegister);
        // Check existing session AFTER views are bound
        SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
        boolean keepSignedIn = prefs.getBoolean("keepSignedIn", false);
        if (mAuth.getCurrentUser() != null && keepSignedIn) {
            btnLogin.setEnabled(false);
            routeUser(mAuth.getCurrentUser().getUid());
            return;
        }

        btnLogin.setOnClickListener(v -> validateAndLogin());

        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
    }

    // ── Validation ────────────────────────────────────────────────
    private void validateAndLogin() {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty()) { showError("Email is required"); return; }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Invalid email format"); return;
        }
        if (password.isEmpty()) { showError("Password is required"); return; }

        loginWithFirebase(email, password);
    }

    // ── Firebase Auth ─────────────────────────────────────────────
    private void loginWithFirebase(String email, String password) {
        btnLogin.setEnabled(false);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    if(authResult.getUser() == null) {
                        btnLogin.setEnabled(true);
                        showError("Login failed: user not found.");
                        return;
                    }

                    String uid = authResult.getUser().getUid();
                    authResult.getUser().getIdToken(true)
                            .addOnSuccessListener(getTokenResult -> {
                                    String jwtToken = getTokenResult.getToken();

                                    if (jwtToken == null) {
                                        btnLogin.setEnabled(true);
                                        showError("Failed to get JWT token.");
                                        return;
                                    }

                                    Log.d("JWT_TOKEN", jwtToken);

                                    getSharedPreferences("session", MODE_PRIVATE).edit()
                                                .putString("uid", uid)
                                                .putBoolean("keepSignedIn", cbKeepSignedIn.isChecked())
                                                .apply();
                                    routeUser(uid);
                            })
                            .addOnFailureListener(e -> {
                                btnLogin.setEnabled(true);
                                showError("Failed to get JWT token: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    btnLogin.setEnabled(true);
                    showError("Login failed: " + e.getMessage());
                });
    }

    // ── Route: Admin or Student ───────────────────────────────────
    private void routeUser(String uid) {
        mDatabase.child("admins").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            goTo(AdminActivity.class);
                        } else {
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

    // ── Loop through all entries manually (avoids index requirement)
    private void checkIfStudent(String uid) {
        mDatabase.child("authorized_uids")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        boolean found = false;
                        for (DataSnapshot entry : snapshot.getChildren()) {
                            String storedAuthUid = entry.child("auth_uid").getValue(String.class);
                            if (uid.equals(storedAuthUid)) {
                                found = true;
                                break;
                            }
                        }
                        if (found) {
                            goTo(UserActivity.class);
                        } else {
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