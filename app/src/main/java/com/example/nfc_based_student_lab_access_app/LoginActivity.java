package com.example.nfc_based_student_lab_access_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class LoginActivity extends AppCompatActivity {

    EditText etEmail, etPassword;
    Button btnLogin;
    TextView tvError, tvRegister;
    CheckBox cbKeepSignedIn;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        // Auto-login if already signed in
        if (mAuth.getCurrentUser() != null) {
            routeUser(mAuth.getCurrentUser().getUid());
            return;
        }

        etEmail        = findViewById(R.id.etEmail);
        etPassword     = findViewById(R.id.etPassword);
        btnLogin       = findViewById(R.id.btnLogin);
        tvError        = findViewById(R.id.tvError);
        tvRegister     = findViewById(R.id.tvRegister);
        cbKeepSignedIn = findViewById(R.id.cbKeepSignedIn);

        btnLogin.setOnClickListener(v -> validateAndLogin());

        tvRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));

        TextView tvContactIt = findViewById(R.id.tvContactIt);
        tvContactIt.setOnClickListener(v -> {
            android.net.Uri uri = android.net.Uri.parse("https://fcms.concordia.ca/idss/pages/account/passwordreset.aspx");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        });
    }

    private void validateAndLogin() {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty()) {
            tvError.setVisibility(View.VISIBLE);
            tvError.setText("Email is required");
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tvError.setVisibility(View.VISIBLE);
            tvError.setText("Invalid email format");
            return;
        }
        if (password.isEmpty()) {
            tvError.setVisibility(View.VISIBLE);
            tvError.setText("Password is required");
            return;
        }
        tvError.setVisibility(View.GONE);
        loginWithFirebase(email, password);
    }

    private void loginWithFirebase(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    // Don't save SharedPreferences yet - wait until they are fully authorized
                    routeUser(uid);
                })
                .addOnFailureListener(e -> {
                    tvError.setVisibility(View.VISIBLE);
                    tvError.setText("Login failed: " + e.getMessage());
                });
    }

    private void routeUser(String uid) {
        // Step 1: Check if the user is an admin
        FirebaseDatabase.getInstance().getReference()
                .child("admins").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            // User is an Admin
                            saveSessionAndLaunch(uid, AdminActivity.class);
                        } else {
                            // Step 2: If not an admin, check if they are an authorized student
                            checkIfAuthorizedStudent(uid);
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        tvError.setVisibility(View.VISIBLE);
                        tvError.setText("Error checking admin role. Try again.");
                    }
                });
    }

    private void checkIfAuthorizedStudent(String uid) {
        // Query the authorized_uids node to find any child where "auth_uid" matches the logged-in user
        FirebaseDatabase.getInstance().getReference()
                .child("authorized_uids")
                .orderByChild("auth_uid").equalTo(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            // A match was found! The student is authorized.
                            saveSessionAndLaunch(uid, UserActivity.class);
                        } else {
                            // No match found. The student is NOT authorized.
                            mAuth.signOut(); // Force log them out

                            tvError.setVisibility(View.VISIBLE);
                            tvError.setText("Access Denied: Your account is not linked to an authorized student UID.");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        tvError.setVisibility(View.VISIBLE);
                        tvError.setText("Error verifying student authorization.");
                    }
                });
    }

    private void saveSessionAndLaunch(String uid, Class<?> activityClass) {
        // Only save session state if they actually pass the authorization checks
        SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
        prefs.edit().putString("uid", uid).apply();

        Intent intent = new Intent(LoginActivity.this, activityClass);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}