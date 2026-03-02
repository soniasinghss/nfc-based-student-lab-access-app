package com.example.nfc_based_student_lab_access_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import android.widget.CheckBox;


public class LoginActivity extends AppCompatActivity {

    EditText etEmail, etPassword;
    Button btnLogin;
    TextView tvError;

    CheckBox cbKeepSignedIn;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
// Check if user is already logged in
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() != null) {
            // User already logged in, skip login screen
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvError = findViewById(R.id.tvError);

        btnLogin.setOnClickListener(v -> validateAndLogin());
        cbKeepSignedIn = findViewById(R.id.cbKeepSignedIn);

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
                    saveSessionAndNavigate(authResult.getUser().getUid());
                })
                .addOnFailureListener(e -> {
                    tvError.setText("Login failed: " + e.getMessage());
                });
    }

    // UI-1.1.4 - Save session and go to dashboard
    private void saveSessionAndNavigate(String uid) {
        SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
        prefs.edit().putString("uid", uid).apply();
        prefs.edit().putBoolean("keepSignedIn", cbKeepSignedIn.isChecked()).apply();


        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}