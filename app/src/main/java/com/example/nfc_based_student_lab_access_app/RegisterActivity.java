package com.example.nfc_based_student_lab_access_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText etEmail, etPassword, etConfirmPassword;
    private Button btnRegister;
    private TextView tvError, tvBackToLogin;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvError = findViewById(R.id.tvError);
        tvBackToLogin = findViewById(R.id.tvBackToLogin);

        btnRegister.setOnClickListener(v -> validateAndRegister());

        tvBackToLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void validateAndRegister() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirm = etConfirmPassword.getText().toString().trim();

        tvError.setVisibility(View.GONE);

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
        if (password.length() < 6) {
            tvError.setVisibility(View.VISIBLE);
            tvError.setText("Password must be at least 6 characters");
            return;
        }
        if (!password.equals(confirm)) {
            tvError.setVisibility(View.VISIBLE);
            tvError.setText("Passwords do not match");
            return;
        }

        registerWithFirebase(email, password);
    }

    private void registerWithFirebase(String email, String password) {
        btnRegister.setEnabled(false);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    if (authResult.getUser() == null) {
                        btnRegister.setEnabled(true);
                        tvError.setVisibility(View.VISIBLE);
                        tvError.setText("Registration failed: user not created.");
                        return;
                    }

                    String uid = authResult.getUser().getUid();

                    Map<String, Object> studentData = new HashMap<>();
                    studentData.put("auth_uid", uid);
                    studentData.put("email", email);
                    studentData.put("role", "student");

                    mDatabase.child("authorized_uids").push()
                            .setValue(studentData)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();

                                Intent intent = new Intent(this, LoginActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                            })
                            .addOnFailureListener(e -> {
                                btnRegister.setEnabled(true);
                                tvError.setVisibility(View.VISIBLE);
                                tvError.setText("Failed to save user data: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    btnRegister.setEnabled(true);
                    tvError.setVisibility(View.VISIBLE);
                    tvError.setText("Registration failed: " + e.getMessage());
                });
    }
}