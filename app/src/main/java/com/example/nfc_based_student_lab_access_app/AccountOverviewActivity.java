package com.example.nfc_based_student_lab_access_app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AccountOverviewActivity extends AppCompatActivity {

    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;
    private PieChart pieChart;
    private LinearLayout llLegend;

    int[] chartColors = {
            Color.parseColor("#7B1A2E"),
            Color.parseColor("#B71C1C"),
            Color.parseColor("#C8A4A4"),
            Color.parseColor("#E8D0D0")
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_account_overview);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }

        mAuth     = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        pieChart = findViewById(R.id.pieChart);
        llLegend = findViewById(R.id.llLegend);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) { finish(); return; }

        fetchStudentProfile(user.getUid());

        // Navigate to NFC Card details page
        findViewById(R.id.btnViewNfcCard).setOnClickListener(v ->
                startActivity(new Intent(AccountOverviewActivity.this, NfcCardActivity.class)));
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void fetchStudentProfile(String authUid) {
        mDatabase.child("authorized_uids")
                .orderByChild("auth_uid")
                .equalTo(authUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot entry : snapshot.getChildren()) {
                                String nfcUid  = entry.getKey();
                                String name    = entry.child("student_name").getValue(String.class);
                                Object sidRaw  = entry.child("student_id").getValue();
                                String sid     = sidRaw != null ? String.valueOf(sidRaw) : null;
                                String addedAt = entry.child("added_at").getValue(String.class);

                                TextView tvAvatar = findViewById(R.id.tvAvatarInitial);
                                if (name != null && !name.isEmpty() && tvAvatar != null)
                                    tvAvatar.setText(String.valueOf(name.charAt(0)).toUpperCase());

                                TextView tvName = findViewById(R.id.tvAccountName);
                                if (tvName != null) tvName.setText(name != null ? name : "Unknown");

                                TextView tvSid = findViewById(R.id.tvAccountStudentId);
                                if (tvSid != null) tvSid.setText("ID: " + (sid != null ? sid : "—"));

                                TextView tvNfc = findViewById(R.id.tvAccountNfcUid);
                                if (tvNfc != null) tvNfc.setText(nfcUid != null ? nfcUid : "—");

                                TextView tvAdded = findViewById(R.id.tvAccountAddedAt);
                                if (tvAdded != null) tvAdded.setText(addedAt != null ? addedAt : "—");

                                TextView tvNfcStatus = findViewById(R.id.tvAccountNfcStatus);
                                if (tvNfcStatus != null)
                                    tvNfcStatus.setText(nfcUid != null ? "✅ Active" : "❌ No card");

                                if (nfcUid != null) fetchStats(nfcUid);
                                break;
                            }
                        } else {
                            Toast.makeText(AccountOverviewActivity.this,
                                    "Profile not linked. Contact admin.", Toast.LENGTH_LONG).show();
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(AccountOverviewActivity.this,
                                "Failed to load profile", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void fetchStats(String nfcUid) {
        mDatabase.child("access_logs")
                .orderByChild("uid")
                .equalTo(nfcUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int total = 0;
                        HashMap<String, Integer> roomCounts = new HashMap<>();

                        Calendar now = Calendar.getInstance();
                        int currentMonth = now.get(Calendar.MONTH);
                        int currentYear  = now.get(Calendar.YEAR);
                        SimpleDateFormat sdf = new SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());

                        android.util.Log.d("STATS", "Total logs found: " + snapshot.getChildrenCount());

                        for (DataSnapshot log : snapshot.getChildren()) {
                            String decision = log.child("decision").getValue(String.class);
                            Object tsRaw    = log.child("timestamp").getValue();

                            if (!"allow".equals(decision) || tsRaw == null) continue;

                            try {
                                Calendar c = Calendar.getInstance();
                                if (tsRaw instanceof Long) {
                                    c.setTimeInMillis((Long) tsRaw);
                                } else {
                                    Date d = sdf.parse(String.valueOf(tsRaw));
                                    if (d == null) continue;
                                    c.setTime(d);
                                }

                                if (c.get(Calendar.MONTH) == currentMonth
                                        && c.get(Calendar.YEAR) == currentYear) {
                                    total++;
                                    String room = log.child("lab_room").getValue(String.class);
                                    if (room != null)
                                        roomCounts.put(room, roomCounts.getOrDefault(room, 0) + 1);
                                }
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                        }

                        TextView tvTotal = findViewById(R.id.tvTotalVisits);
                        if (tvTotal != null) tvTotal.setText(String.valueOf(total));

                        TextView tvMost = findViewById(R.id.tvMostVisited);
                        if (!roomCounts.isEmpty()) {
                            String best = ""; int max = 0;
                            for (Map.Entry<String, Integer> e : roomCounts.entrySet()) {
                                if (e.getValue() > max) { max = e.getValue(); best = e.getKey(); }
                            }
                            if (tvMost != null) tvMost.setText(best);
                        } else {
                            if (tvMost != null) tvMost.setText("None");
                        }

                        buildPieChart(roomCounts);
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(AccountOverviewActivity.this,
                                "Failed to load stats", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void buildPieChart(HashMap<String, Integer> roomCounts) {
        if (pieChart == null || roomCounts.isEmpty()) {
            if (pieChart != null) pieChart.setVisibility(android.view.View.GONE);
            return;
        }

        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors  = new ArrayList<>();
        int colorIndex = 0;

        if (llLegend != null) llLegend.removeAllViews();

        for (Map.Entry<String, Integer> entry : roomCounts.entrySet()) {
            entries.add(new PieEntry(entry.getValue(), entry.getKey()));
            colors.add(chartColors[colorIndex % chartColors.length]);

            if (llLegend != null) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, 6, 0, 6);

                TextView dot = new TextView(this);
                dot.setText("●  ");
                dot.setTextColor(chartColors[colorIndex % chartColors.length]);
                dot.setTextSize(14);

                TextView label = new TextView(this);
                label.setText(entry.getKey());
                label.setTextSize(14);
                label.setTextColor(Color.BLACK);
                label.setTypeface(null, android.graphics.Typeface.BOLD);
                label.setLayoutParams(new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                TextView count = new TextView(this);
                count.setText(String.valueOf(entry.getValue()));
                count.setTextSize(14);
                count.setTextColor(Color.GRAY);

                row.addView(dot);
                row.addView(label);
                row.addView(count);
                llLegend.addView(row);
            }
            colorIndex++;
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);
        dataSet.setSliceSpace(4f);

        PieData data = new PieData(dataSet);
        pieChart.setData(data);
        pieChart.setHoleRadius(45f);
        pieChart.setTransparentCircleRadius(50f);
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setEnabled(false);
        pieChart.setDrawEntryLabels(false);
        pieChart.setHoleColor(Color.WHITE);
        pieChart.invalidate();
    }
}