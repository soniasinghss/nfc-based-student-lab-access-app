package com.example.nfc_based_student_lab_access_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
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

public class RegisterActivity extends AppCompatActivity {

    private EditText etEmail, etPassword, etConfirmPassword, etStudentId;
    private Button btnRegister;
    private TextView tvError, tvBackToLogin;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth     = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        etEmail           = findViewById(R.id.etEmail);
        etPassword        = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        etStudentId       = findViewById(R.id.etStudentId);
        btnRegister       = findViewById(R.id.btnRegister);
        tvError           = findViewById(R.id.tvError);
        tvBackToLogin     = findViewById(R.id.tvBackToLogin);

        btnRegister.setOnClickListener(v -> validateAndRegister());

        tvBackToLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void validateAndRegister() {
        String email     = etEmail.getText().toString().trim();
        String password  = etPassword.getText().toString().trim();
        String confirm   = etConfirmPassword.getText().toString().trim();
        String studentId = etStudentId.getText().toString().trim();

        tvError.setVisibility(View.GONE);

        if (email.isEmpty()) {
            showError("Email is required");
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Invalid email format");
            return;
        }
        if (studentId.isEmpty()) {
            showError("Student ID is required");
            return;
        }
        if (password.isEmpty()) {
            showError("Password is required");
            return;
        }
        if (password.length() < 6) {
            showError("Password must be at least 6 characters");
            return;
        }
        if (!password.equals(confirm)) {
            showError("Passwords do not match");
            return;
        }

        checkStudentIdExists(studentId, email, password);
    }

    private void checkStudentIdExists(String studentId, String email, String password) {
        btnRegister.setEnabled(false);

        mDatabase.child("authorized_uids")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean found = false;
                        for (DataSnapshot entry : snapshot.getChildren()) {
                            Object sidRaw = entry.child("student_id").getValue();
                            String sid = sidRaw != null ? String.valueOf(sidRaw) : "";

                            if (studentId.equals(sid)) {
                                String existingAuthUid = entry.child("auth_uid")
                                        .getValue(String.class);
                                if (existingAuthUid != null && !existingAuthUid.isEmpty()) {
                                    btnRegister.setEnabled(true);
                                    showError("This student ID is already registered.");
                                    return;
                                }
                                found = true;
                                String nfcKey = entry.getKey();
                                registerWithFirebase(email, password, nfcKey, studentId);
                                break;
                            }
                        }
                        if (!found) {
                            btnRegister.setEnabled(true);
                            showError("Student ID not found. Please contact your admin.");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        btnRegister.setEnabled(true);
                        showError("Error checking student ID. Try again.");
                    }
                });
    }

    private void registerWithFirebase(String email, String password,
                                      String nfcKey, String studentId) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    if (authResult.getUser() == null) {
                        btnRegister.setEnabled(true);
                        showError("Registration failed: user not created.");
                        return;
                    }

                    String uid = authResult.getUser().getUid();
                    String timestamp = new SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                            .format(new Date());

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("auth_uid", uid);
                    updates.put("added_at", timestamp);

                    mDatabase.child("authorized_uids").child(nfcKey)
                            .updateChildren(updates)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this,
                                        "Account created and linked to your NFC card!",
                                        Toast.LENGTH_LONG).show();
                                Intent intent = new Intent(this, UserActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                            })
                            .addOnFailureListener(e -> {
                                btnRegister.setEnabled(true);
                                showError("Failed to link account: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    btnRegister.setEnabled(true);
                    showError("Registration failed: " + e.getMessage());
                });
    }

    private void showError(String message) {
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(message);
    }
}
