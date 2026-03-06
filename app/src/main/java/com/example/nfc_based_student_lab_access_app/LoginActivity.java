package com.example.nfc_based_student_lab_access_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
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
        setContentView(R.layout.activity_login);

        // Initialize Firebase Auth and Database
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvError = findViewById(R.id.tvError);
        cbKeepSignedIn = findViewById(R.id.cbKeepSignedIn);

        // Check if user is already logged in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // User is logged in, check their role and skip the login screen
            checkRoleAndNavigate(currentUser.getUid());
        }

        btnLogin.setOnClickListener(v -> validateAndLogin());
    }

    // UI-1.1.2 - Validation
    private void validateAndLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty()) {
            tvError.setText("Email is required");
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tvError.setText("Invalid email format");
            return;
        }
        if (password.isEmpty()) {
            tvError.setText("Password is required");
            return;
        }

        loginWithFirebase(email, password);
    }

    // UI-1.1.3 - Call Firebase Auth
    private void loginWithFirebase(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    saveSessionPreference();
                    checkRoleAndNavigate(uid);
                })
                .addOnFailureListener(e -> {
                    tvError.setText("Login failed: " + e.getMessage());
                });
    }

    // Save the "Keep me signed in" preference
    private void saveSessionPreference() {
        SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
        prefs.edit().putBoolean("keepSignedIn", cbKeepSignedIn.isChecked()).apply();
    }

    // Query Realtime Database to determine role, then navigate
    private void checkRoleAndNavigate(String uid) {
        DatabaseReference adminsRef = mDatabase.child("admins");

        adminsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean isAdmin = false;

                // Loop through the 'admins' node to see if the user's UID is listed
                for (DataSnapshot child : snapshot.getChildren()) {
                    String adminUid = child.getValue(String.class);
                    if (uid.equals(child.getKey()) || uid.equals(adminUid)) {
                        isAdmin = true;
                        break;
                    }
                }

                // Route to the appropriate Activity
                Intent intent;
                if (isAdmin) {
                    intent = new Intent(LoginActivity.this, AdminActivity.class);
                } else {
                    intent = new Intent(LoginActivity.this, UserActivity.class);
                }

                // Clear the backstack so the user can't press 'back' to return to the login screen
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvError.setText("Database error: " + error.getMessage());
            }
        });
    }
}