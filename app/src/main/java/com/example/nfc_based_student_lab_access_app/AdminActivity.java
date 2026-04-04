package com.example.nfc_based_student_lab_access_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AdminActivity extends AppCompatActivity {

    Button btnLogout;
    TextView tvStatus;
    CardView cvManageStudents, cvLabOccupancy, cvLogs, cvAdminSetup;

    DatabaseReference db;
    FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseDatabase.getInstance().getReference();

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            goToLogin();
            return;
        }

        btnLogout         = findViewById(R.id.btnLogout);
        tvStatus          = findViewById(R.id.tvStatus);
        cvManageStudents  = findViewById(R.id.cvManageStudents);
        cvLabOccupancy    = findViewById(R.id.cvLabOccupancy);
        cvLogs            = findViewById(R.id.cvLogs);
        cvAdminSetup      = findViewById(R.id.cvAdminSetup);

        // Disable cards initially until admin status is confirmed
        setCardsEnabled(false);

        // Admin Auth Check
        String currentUID = user.getUid();
        db.child("admins").child(currentUID)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            tvStatus.setText("Status: Verified");
                            setCardsEnabled(true);
                        } else {
                            tvStatus.setText("Status: Access denied (Admins only)");
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        tvStatus.setText("Status: Database Error");
                    }
                });

        // Setup Navigation Click Listeners
        cvManageStudents.setOnClickListener(v -> startActivity(new Intent(AdminActivity.this, ManageStudentsActivity.class)));
        cvLabOccupancy.setOnClickListener(v -> startActivity(new Intent(AdminActivity.this, ManageOccupancyActivity.class)));
        cvLogs.setOnClickListener(v -> startActivity(new Intent(AdminActivity.this, AccessLogsActivity.class)));
        cvAdminSetup.setOnClickListener(v -> startActivity(new Intent(AdminActivity.this, AdminSetupActivity.class)));

        btnLogout.setOnClickListener(v -> logoutUser());
    }

    private void setCardsEnabled(boolean isEnabled) {
        cvManageStudents.setEnabled(isEnabled);
        cvLabOccupancy.setEnabled(isEnabled);
        cvLogs.setEnabled(isEnabled);
        cvAdminSetup.setEnabled(isEnabled);

        // Optional: Dim the cards visually if disabled
        float alpha = isEnabled ? 1.0f : 0.5f;
        cvManageStudents.setAlpha(alpha);
        cvLabOccupancy.setAlpha(alpha);
        cvLogs.setAlpha(alpha);
        cvAdminSetup.setAlpha(alpha);
    }

    private void logoutUser() {
        mAuth.signOut();
        SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
        prefs.edit().clear().apply();
        goToLogin();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}