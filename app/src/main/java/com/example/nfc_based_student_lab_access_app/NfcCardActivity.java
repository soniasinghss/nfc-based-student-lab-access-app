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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NfcCardActivity extends AppCompatActivity {

    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    private TextView tvCardUID;
    private TextView tvCardStatus;
    private TextView tvStudentName;
    private TextView tvStudentId;
    private TextView tvAddedAt;
    private TextView tvLab101Access;
    private TextView tvAccessRoomLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nfc_card);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }

        tvCardUID = findViewById(R.id.tvCardUID);
        tvCardStatus = findViewById(R.id.tvCardStatus);
        tvStudentName = findViewById(R.id.tvStudentName);
        tvStudentId = findViewById(R.id.tvStudentId);
        tvAddedAt = findViewById(R.id.tvAddedAt);
        tvLab101Access = findViewById(R.id.tvLab101Access);
        tvAccessRoomLabel = findViewById(R.id.tvAccessRoomLabel);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            finish();
            return;
        }

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
                .orderByChild("auth_uid")
                .equalTo(authUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            showNoCardState();
                            Toast.makeText(NfcCardActivity.this,
                                    "No card linked. Contact admin.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        for (DataSnapshot entry : snapshot.getChildren()) {
                            String nfcUid = entry.getKey();
                            String name = entry.child("student_name").getValue(String.class);
                            Object sidRaw = entry.child("student_id").getValue();
                            String sid = sidRaw != null ? String.valueOf(sidRaw) : "—";
                            String added = entry.child("added_at").getValue(String.class);
                            Boolean access = entry.child("Access").getValue(Boolean.class);

                            tvCardUID.setText(nfcUid != null ? nfcUid : "—");
                            tvCardStatus.setText("✅ Active");
                            tvCardStatus.setTextColor(
                                    getResources().getColor(android.R.color.holo_green_dark)
                            );
                            tvStudentName.setText(name != null ? name : "—");
                            tvStudentId.setText(sid);
                            tvAddedAt.setText(added != null ? added : "—");

                            if (nfcUid != null && !nfcUid.trim().isEmpty()) {
                                loadLatestRoomForCard(nfcUid, access != null && access);
                            } else {
                                updateAccessUi(null, access != null && access);
                            }
                            break;
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(NfcCardActivity.this,
                                "Failed to load card",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadLatestRoomForCard(String nfcUid, boolean hasAccess) {
        mDatabase.child("access_logs")
                .orderByChild("uid")
                .equalTo(nfcUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String latestRoom = null;
                        long latestTimestampMillis = Long.MIN_VALUE;

                        SimpleDateFormat sdf =
                                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());

                        for (DataSnapshot log : snapshot.getChildren()) {
                            String decision = log.child("decision").getValue(String.class);
                            Object tsRaw = log.child("timestamp").getValue();
                            String room = log.child("lab_room").getValue(String.class);

                            if (!"allow".equalsIgnoreCase(decision)
                                    || room == null
                                    || room.trim().isEmpty()
                                    || tsRaw == null) {
                                continue;
                            }

                            Date parsedDate = parseTimestamp(tsRaw, sdf);
                            if (parsedDate == null) {
                                continue;
                            }

                            long millis = parsedDate.getTime();
                            if (millis > latestTimestampMillis) {
                                latestTimestampMillis = millis;
                                latestRoom = room;
                            }
                        }

                        updateAccessUi(latestRoom, hasAccess);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        updateAccessUi(null, hasAccess);
                    }
                });
    }

    private Date parseTimestamp(Object tsRaw, SimpleDateFormat sdf) {
        try {
            if (tsRaw instanceof Long) {
                return new Date((Long) tsRaw);
            } else {
                return sdf.parse(String.valueOf(tsRaw));
            }
        } catch (ParseException e) {
            return null;
        }
    }

    private void updateAccessUi(String room, boolean hasAccess) {
        if (tvAccessRoomLabel != null) {
            tvAccessRoomLabel.setText(room != null ? room : "Assigned Lab");
        }

        if (hasAccess) {
            tvLab101Access.setText("✅ Allowed");
            tvLab101Access.setTextColor(
                    getResources().getColor(android.R.color.holo_green_dark)
            );
        } else {
            tvLab101Access.setText("❌ Not allowed");
            tvLab101Access.setTextColor(
                    getResources().getColor(android.R.color.holo_red_dark)
            );
        }
    }

    private void showNoCardState() {
        tvCardStatus.setText("❌ No NFC card registered");
        tvCardStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        tvCardUID.setText("—");
        tvAddedAt.setText("—");
        tvStudentName.setText("—");
        tvStudentId.setText("—");

        if (tvAccessRoomLabel != null) {
            tvAccessRoomLabel.setText("Assigned Lab");
        }

        tvLab101Access.setText("❌ Not allowed");
        tvLab101Access.setTextColor(
                getResources().getColor(android.R.color.holo_red_dark)
        );
    }
}