package com.example.nfc_based_student_lab_access_app;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class NfcCardActivity extends AppCompatActivity {

    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    TextView tvCardUID, tvCardStatus, tvStudentName,
            tvStudentId, tvAddedAt, tvLab101Access;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nfc_card);

        mAuth     = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }

        tvCardUID      = findViewById(R.id.tvCardUID);
        tvCardStatus   = findViewById(R.id.tvCardStatus);
        tvStudentName  = findViewById(R.id.tvStudentName);
        tvStudentId    = findViewById(R.id.tvStudentId);
        tvAddedAt      = findViewById(R.id.tvAddedAt);
        tvLab101Access = findViewById(R.id.tvLab101Access);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) { finish(); return; }

        loadNfcCard(user.getUid());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadNfcCard(String authUid) {
        mDatabase.child("authorized_uids")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean found = false;
                        for (DataSnapshot entry : snapshot.getChildren()) {
                            String storedUid = entry.child("auth_uid").getValue(String.class);
                            if (authUid.equals(storedUid)) {
                                found = true;

                                String nfcUid = entry.getKey();
                                String name   = entry.child("student_name").getValue(String.class);
                                Object sidRaw = entry.child("student_id").getValue();
                                String sid    = sidRaw != null ? String.valueOf(sidRaw) : "—";
                                String added  = entry.child("added_at").getValue(String.class);
                                Boolean access = entry.child("Access").getValue(Boolean.class);

                                tvCardUID.setText(nfcUid != null ? nfcUid : "—");
                                tvCardStatus.setText("✅ Active");
                                tvCardStatus.setTextColor(
                                        getResources().getColor(android.R.color.holo_green_dark));
                                tvStudentName.setText(name != null ? name : "—");
                                tvStudentId.setText(sid);
                                tvAddedAt.setText(added != null ? added : "—");

                                // Dynamic access check
                                if (access != null && access) {
                                    tvLab101Access.setText("✅ Allowed");
                                    tvLab101Access.setTextColor(
                                            getResources().getColor(android.R.color.holo_green_dark));
                                } else {
                                    tvLab101Access.setText("❌ Not allowed");
                                    tvLab101Access.setTextColor(
                                            getResources().getColor(android.R.color.holo_red_dark));
                                }

                                break;
                            }
                        }
                        if (!found) {
                            tvCardStatus.setText("❌ No NFC card registered");
                            tvCardUID.setText("—");
                            tvAddedAt.setText("—");
                            tvStudentName.setText("—");
                            tvStudentId.setText("—");
                            tvLab101Access.setText("❌ Not allowed");
                            tvLab101Access.setTextColor(
                                    getResources().getColor(android.R.color.holo_red_dark));
                            Toast.makeText(NfcCardActivity.this,
                                    "No card linked. Contact admin.",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(NfcCardActivity.this,
                                "Failed to load card", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}