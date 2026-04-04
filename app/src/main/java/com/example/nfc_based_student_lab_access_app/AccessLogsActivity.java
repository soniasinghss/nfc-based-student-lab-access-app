package com.example.nfc_based_student_lab_access_app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;
import androidx.core.util.Pair;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class AccessLogsActivity extends AppCompatActivity {

    private LinearLayout llLogsContainer;
    private DatabaseReference db;
    private EditText etLogSearch;
    private Spinner spinnerSearchMode;
    private Button btnSelectWeek, btnDownloadTxt;
    private TextView tvCurrentRange;

    private DataSnapshot usersSnapshot;
    private long weekStartMillis, weekEndMillis;
    private String startIso, endIso;
    private ValueEventListener activeLogListener;
    private Query currentQuery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_access_logs);

        // 1. Setup UI Components
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        db = FirebaseDatabase.getInstance().getReference();
        llLogsContainer = findViewById(R.id.llLogsContainer);
        etLogSearch = findViewById(R.id.etLogSearch);
        spinnerSearchMode = findViewById(R.id.spinnerSearchMode);
        btnSelectWeek = findViewById(R.id.btnSelectWeek);
        btnDownloadTxt = findViewById(R.id.btnDownloadJson);
        tvCurrentRange = findViewById(R.id.tvCurrentRange);

        // 2. Setup Spinner
        String[] modes = {"Name", "UID", "ID"};
        spinnerSearchMode.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes));

        // 3. Initialize data (Snap to current week)
        setWeekRangeFromCalendar(Calendar.getInstance());
        fetchUsers();
        fetchWeeklyLogs();

        // 4. Click Listeners
        btnSelectWeek.setOnClickListener(v -> showWeekPicker());
        btnDownloadTxt.setOnClickListener(v -> downloadAllTimeLogsAsTxt());

        etLogSearch.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable s) { /* Logic handled by Firebase listener refresh */ }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
    }

    private void showWeekPicker() {
        // Material Range Picker for the "Autofilled Block" look
        MaterialDatePicker.Builder<Pair<Long, Long>> builder = MaterialDatePicker.Builder.dateRangePicker();
        builder.setTitleText("Select Week to View");

        // Pre-select the current week range
        builder.setSelection(new Pair<>(weekStartMillis, weekEndMillis));

        final MaterialDatePicker<Pair<Long, Long>> picker = builder.build();
        picker.show(getSupportFragmentManager(), "WEEK_PICKER");

        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection != null && selection.first != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(selection.first);

                // Force selection to snap to a clean Sunday-Saturday block
                setWeekRangeFromCalendar(cal);
                fetchWeeklyLogs();
            }
        });
    }

    private void setWeekRangeFromCalendar(Calendar cal) {
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        SimpleDateFormat displayFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());

        // Snap to Sunday morning
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        weekStartMillis = cal.getTimeInMillis();
        startIso = isoFormat.format(cal.getTime());
        String startDisplay = displayFormat.format(cal.getTime());

        // Snap to Saturday night
        cal.add(Calendar.DAY_OF_WEEK, 6);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        weekEndMillis = cal.getTimeInMillis();
        endIso = isoFormat.format(cal.getTime());
        String endDisplay = displayFormat.format(cal.getTime());

        tvCurrentRange.setText("Week: " + startDisplay + " — " + endDisplay);
    }

    private void fetchUsers() {
        db.child("authorized_uids").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) { usersSnapshot = snapshot; }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void fetchWeeklyLogs() {
        // Clean up old listener to save phone battery/data
        if (currentQuery != null && activeLogListener != null) {
            currentQuery.removeEventListener(activeLogListener);
        }

        llLogsContainer.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText("Updating logs...");
        loading.setGravity(Gravity.CENTER);
        llLogsContainer.addView(loading);

        // Server-Side: Only download the specific week
        currentQuery = db.child("access_logs")
                .orderByChild("timestamp")
                .startAt(startIso)
                .endAt(endIso);

        activeLogListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) { renderUI(snapshot); }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        currentQuery.addValueEventListener(activeLogListener);
    }

    private void renderUI(DataSnapshot snapshot) {
        if (snapshot == null) return;
        llLogsContainer.removeAllViews();

        String query = etLogSearch.getText().toString().toLowerCase().trim();
        String mode = spinnerSearchMode.getSelectedItem().toString();

        int logCount = 0;
        for (DataSnapshot log : snapshot.getChildren()) {
            // Safe parsing to avoid Long-to-String crashes
            String uid = String.valueOf(log.child("uid").getValue());
            String time = String.valueOf(log.child("timestamp").getValue());
            String decision = String.valueOf(log.child("decision").getValue());
            String lab = String.valueOf(log.child("lab_room").getValue());

            String name = "Unknown Student", sid = "N/A";
            if (usersSnapshot != null && usersSnapshot.hasChild(uid)) {
                name = String.valueOf(usersSnapshot.child(uid).child("student_name").getValue());
                sid = String.valueOf(usersSnapshot.child(uid).child("student_id").getValue());
            }

            boolean matches = query.isEmpty();
            if (!query.isEmpty()) {
                if (mode.equals("Name")) matches = name.toLowerCase().contains(query);
                else if (mode.equals("UID")) matches = uid.toLowerCase().contains(query);
                else if (mode.equals("ID")) matches = sid.toLowerCase().contains(query);
            }

            if (matches) {
                addLogEntry(uid, name, sid, time, decision, lab);
                logCount++;
            }
        }
        if (logCount == 0) {
            TextView empty = new TextView(this);
            empty.setText("No activity this week.");
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 100, 0, 0);
            llLogsContainer.addView(empty);
        }
    }

    private void addLogEntry(String uid, String name, String sid, String time, String decision, String lab) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, 16);
        card.setLayoutParams(params);
        card.setRadius(12f);
        card.setCardElevation(3f);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(30, 30, 30, 30);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView tvMain = new TextView(this);
        tvMain.setText(String.format("%s (%s)", name, sid));
        tvMain.setTypeface(null, Typeface.BOLD);
        tvMain.setTextColor(Color.BLACK);

        TextView tvSub = new TextView(this);
        tvSub.setText("UID: " + uid + " | Room: " + (lab.equals("null") ? "Main" : lab));
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

        TextView tvTime = new TextView(this);
        tvTime.setText(time.replace("T", " "));
        tvTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvTime.setTextColor(Color.GRAY);

        textCol.addView(tvMain);
        textCol.addView(tvSub);
        textCol.addView(tvTime);

        TextView badge = new TextView(this);
        badge.setText(decision.toUpperCase());
        badge.setPadding(20, 10, 20, 10);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        badge.setTypeface(null, Typeface.BOLD);

        if ("ALLOW".equals(decision.toUpperCase())) {
            badge.setBackgroundColor(Color.parseColor("#E8F5E9"));
            badge.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            badge.setBackgroundColor(Color.parseColor("#FFEBEE"));
            badge.setTextColor(Color.parseColor("#C62828"));
        }

        row.addView(textCol);
        row.addView(badge);
        card.addView(row);
        llLogsContainer.addView(card, 0);
    }

    private void downloadAllTimeLogsAsTxt() {
        Toast.makeText(this, "Fetching all data...", Toast.LENGTH_SHORT).show();
        // One-time fetch of everything for the .txt report
        db.child("access_logs").get().addOnSuccessListener(snapshot -> {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("LAB ACCESS REPORT - ALL TIME HISTORY\n====================================\n\n");

                for (DataSnapshot log : snapshot.getChildren()) {
                    String uid = String.valueOf(log.child("uid").getValue());
                    String time = String.valueOf(log.child("timestamp").getValue()).replace("T", " ");
                    String res = String.valueOf(log.child("decision").getValue()).toUpperCase();
                    String name = (usersSnapshot != null && usersSnapshot.hasChild(uid))
                            ? String.valueOf(usersSnapshot.child(uid).child("student_name").getValue()) : "Unknown";

                    sb.append(String.format("%-20s | %-10s | %-20s | %s\n", name, uid, time, res));
                }

                File file = new File(getExternalFilesDir(null), "all_time_logs.txt");
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(sb.toString().getBytes());
                fos.close();

                Uri path = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_STREAM, path);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent, "Share All-Time Logs"));

            } catch (Exception e) { Toast.makeText(this, "Export Failed", Toast.LENGTH_SHORT).show(); }
        });
    }

    @Override public boolean onSupportNavigateUp() { getOnBackPressedDispatcher().onBackPressed(); return true; }
}