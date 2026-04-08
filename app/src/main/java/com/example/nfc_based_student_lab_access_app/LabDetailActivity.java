package com.example.nfc_based_student_lab_access_app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LabDetailActivity extends AppCompatActivity {

    private TextView tvHeaderLabName;
    private TextView tvLabOccupancy;
    private TextView tvLabStatus;
    private TextView tvSelectedDate;
    private TextView btnPrevDay;
    private TextView btnNextDay;
    private LinearLayout layoutScheduleList;
    private BarChart barChart;
    private TextView tvNoData;

    private DatabaseReference occupancyRef;
    private DatabaseReference scheduleRef;

    private String roomId;
    private String scheduleRoomId;
    private Calendar selectedDate;

    private Integer currentOccupancyCount = null;
    private Integer currentMaxCapacity = null;
    private boolean currentIsScheduledNow = false;

    private final SimpleDateFormat firebaseDateFormat =
            new SimpleDateFormat("yyyy-MM-dd", Locale.CANADA);
    private final SimpleDateFormat displayDateFormat =
            new SimpleDateFormat("EEEE, MMM d, yyyy", Locale.CANADA);
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.CANADA);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lab_detail);

        initViews();
        setupToolbar();

        Intent intent = getIntent();

        roomId = intent.getStringExtra("room_id");
        if (roomId == null || roomId.trim().isEmpty()) {
            roomId = intent.getStringExtra("LAB_ROOM");
        }

        if (roomId == null || roomId.trim().isEmpty()) {
            Toast.makeText(this, "No lab room selected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        roomId = roomId.trim();
        scheduleRoomId = convertToScheduleRoomId(roomId);
        selectedDate = Calendar.getInstance();

        tvHeaderLabName.setText(roomId);
        updateDisplayedDate();

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        occupancyRef = database.getReference("occupancy").child(roomId);
        scheduleRef = database.getReference("lab_schedule").child(scheduleRoomId);

        loadOccupancy();
        loadCurrentStatus();
        loadScheduleForDate();
        loadOccupancyHistory();

        btnPrevDay.setOnClickListener(v -> {
            selectedDate.add(Calendar.DAY_OF_MONTH, -1);
            updateDisplayedDate();
            loadScheduleForDate();
        });

        btnNextDay.setOnClickListener(v -> {
            selectedDate.add(Calendar.DAY_OF_MONTH, 1);
            updateDisplayedDate();
            loadScheduleForDate();
        });
    }

    private void initViews() {
        tvHeaderLabName = findViewById(R.id.tvHeaderLabName);
        tvLabOccupancy = findViewById(R.id.tvLabOccupancy);
        tvLabStatus = findViewById(R.id.tvLabStatus);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        btnPrevDay = findViewById(R.id.btnPrevDay);
        btnNextDay = findViewById(R.id.btnNextDay);
        layoutScheduleList = findViewById(R.id.layoutScheduleList);
        barChart = findViewById(R.id.barChart);
        tvNoData = findViewById(R.id.tvNoData);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Lab Details");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void updateDisplayedDate() {
        tvSelectedDate.setText(displayDateFormat.format(selectedDate.getTime()));
    }

    private void loadOccupancy() {
        occupancyRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Integer currentCount = snapshot.child("current_count").getValue(Integer.class);
                Integer maxCapacity = snapshot.child("max_capacity").getValue(Integer.class);
                String displayName = snapshot.child("display_name").getValue(String.class);

                if (currentCount == null) currentCount = 0;
                if (maxCapacity == null) maxCapacity = 0;

                currentOccupancyCount = currentCount;
                currentMaxCapacity = maxCapacity;

                if (displayName != null && !displayName.trim().isEmpty()) {
                    tvHeaderLabName.setText(displayName);
                } else {
                    tvHeaderLabName.setText(roomId);
                }

                tvLabOccupancy.setText(currentCount + " / " + maxCapacity);
                refreshStatusUI();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvLabOccupancy.setText("Unavailable");
            }
        });
    }

    private void loadCurrentStatus() {
        String today = firebaseDateFormat.format(Calendar.getInstance().getTime());

        scheduleRef.child(today).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean isScheduledNow = false;

                for (DataSnapshot slot : snapshot.getChildren()) {
                    String start = slot.child("start_time").getValue(String.class);
                    String end = slot.child("end_time").getValue(String.class);

                    if (start != null && end != null && isCurrentTimeInRange(start, end)) {
                        isScheduledNow = true;
                        break;
                    }
                }

                currentIsScheduledNow = isScheduledNow;
                refreshStatusUI();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                currentIsScheduledNow = false;
                refreshStatusUI();
            }
        });
    }

    private void loadScheduleForDate() {
        String firebaseDate = firebaseDateFormat.format(selectedDate.getTime());

        scheduleRef.child(firebaseDate).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                layoutScheduleList.removeAllViews();

                List<ScheduleSlot> slots = new ArrayList<>();

                for (DataSnapshot slotSnapshot : snapshot.getChildren()) {
                    String startTime = slotSnapshot.child("start_time").getValue(String.class);
                    String endTime = slotSnapshot.child("end_time").getValue(String.class);

                    if (startTime == null || endTime == null) continue;
                    slots.add(new ScheduleSlot(startTime, endTime));
                }

                java.util.Collections.sort(slots, (slot1, slot2) -> {
                    try {
                        Date d1 = timeFormat.parse(slot1.startTime);
                        Date d2 = timeFormat.parse(slot2.startTime);
                        if (d1 == null || d2 == null) return 0;
                        return d1.compareTo(d2);
                    } catch (ParseException e) {
                        return 0;
                    }
                });

                for (ScheduleSlot slot : slots) {
                    addScheduleRow(slot.startTime, slot.endTime);
                }

                if (slots.isEmpty()) {
                    addMessageRow("No schedule for this day");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                layoutScheduleList.removeAllViews();
                addMessageRow("Could not load schedule");
            }
        });
    }

    private void refreshStatusUI() {
        if (currentIsScheduledNow) {
            tvLabStatus.setText("Not available");
            tvLabStatus.setTextColor(Color.parseColor("#932339"));
        } else {
            tvLabStatus.setText("Available");
            tvLabStatus.setTextColor(Color.parseColor("#22C55E"));
        }
    }

    private void loadOccupancyHistory() {
        FirebaseDatabase.getInstance().getReference("access_logs")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Map<String, Integer> hourlyCount = new LinkedHashMap<>();
                        for (int i = 0; i < 24; i++) {
                            hourlyCount.put(String.format("%02d:00", i), 0);
                        }

                        SimpleDateFormat sdf = new SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());

                        Calendar now = Calendar.getInstance();
                        int currentMonth = now.get(Calendar.MONTH);
                        int currentYear = now.get(Calendar.YEAR);

                        String normalizedCurrentRoom = normalizeRoomId(roomId);

                        for (DataSnapshot log : snapshot.getChildren()) {
                            String decision = log.child("decision").getValue(String.class);
                            Object tsRaw = log.child("timestamp").getValue();
                            Object labRoomRaw = log.child("lab_room").getValue();

                            if (!"allow".equalsIgnoreCase(decision) || tsRaw == null || labRoomRaw == null) {
                                continue;
                            }

                            String logRoom = String.valueOf(labRoomRaw).trim();
                            if (!normalizeRoomId(logRoom).equals(normalizedCurrentRoom)) {
                                continue;
                            }

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
                                    int hour = c.get(Calendar.HOUR_OF_DAY);
                                    String key = String.format("%02d:00", hour);
                                    hourlyCount.put(key, hourlyCount.get(key) + 1);
                                }

                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                        }

                        buildBarChart(hourlyCount);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        tvNoData.setVisibility(View.VISIBLE);
                        barChart.setVisibility(View.GONE);
                    }
                });
    }

    private String normalizeRoomId(String room) {
        if (room == null) return "";

        String normalized = room.trim().toUpperCase();
        normalized = normalized.replace(" ", "");
        normalized = normalized.replace("-", "");
        normalized = normalized.replace(".", "_");

        return normalized;
    }

    private void buildBarChart(Map<String, Integer> hourlyCount) {
        boolean hasData = false;
        for (int v : hourlyCount.values()) {
            if (v > 0) {
                hasData = true;
                break;
            }
        }

        if (!hasData) {
            tvNoData.setVisibility(View.VISIBLE);
            barChart.setVisibility(View.GONE);
            return;
        }

        tvNoData.setVisibility(View.GONE);
        barChart.setVisibility(View.VISIBLE);

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int index = 0;

        for (Map.Entry<String, Integer> entry : hourlyCount.entrySet()) {
            int hour = Integer.parseInt(entry.getKey().split(":")[0]);
            if (hour >= 7 && hour <= 22) {
                entries.add(new BarEntry(index, entry.getValue()));
                labels.add(entry.getKey());
                index++;
            }
        }

        BarDataSet dataSet = new BarDataSet(entries, "Visits per hour");
        dataSet.setColor(Color.parseColor("#932339"));
        dataSet.setDrawValues(false);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);

        barChart.setData(data);
        barChart.setFitBars(true);
        barChart.getDescription().setEnabled(false);
        barChart.getLegend().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.setDrawBorders(false);
        barChart.setExtraBottomOffset(10f);
        barChart.animateY(800);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.parseColor("#666666"));
        xAxis.setTextSize(9f);
        xAxis.setLabelRotationAngle(-45f);
        xAxis.setLabelCount(Math.min(labels.size(), 8), true);
        xAxis.setDrawAxisLine(true);
        xAxis.setAvoidFirstLastClipping(true);
        barChart.setExtraBottomOffset(20f);

        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.parseColor("#EEEEEE"));
        leftAxis.setTextColor(Color.parseColor("#666666"));
        leftAxis.setAxisMinimum(0f);
        leftAxis.setGranularity(1f);
        leftAxis.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        barChart.getAxisRight().setEnabled(false);
        barChart.invalidate();
    }

    private void addScheduleRow(String startTime, String endTime) {
        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 12);
        card.setLayoutParams(cardParams);
        card.setRadius(20f);
        card.setCardElevation(2f);
        card.setCardBackgroundColor(Color.parseColor("#F9E8ED"));

        LinearLayout content = new LinearLayout(this);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(24, 22, 24, 22);

        TextView tvTime = new TextView(this);
        tvTime.setText(startTime + " - " + endTime);
        tvTime.setTextSize(14f);
        tvTime.setTextColor(Color.parseColor("#5B6475"));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Lab unavailable");
        tvTitle.setTextSize(16f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(Color.parseColor("#1A1A1A"));
        tvTitle.setPadding(0, 8, 0, 4);

        TextView tvSubtitle = new TextView(this);
        tvSubtitle.setText("Scheduled class or reserved time");
        tvSubtitle.setTextSize(13f);
        tvSubtitle.setTextColor(Color.parseColor("#7A7A7A"));

        content.addView(tvTime);
        content.addView(tvTitle);
        content.addView(tvSubtitle);
        card.addView(content);
        layoutScheduleList.addView(card);
    }

    private void addMessageRow(String message) {
        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 12);
        card.setLayoutParams(cardParams);
        card.setRadius(20f);
        card.setCardElevation(2f);
        card.setCardBackgroundColor(Color.parseColor("#F8F8F8"));

        TextView tv = new TextView(this);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        tv.setPadding(24, 24, 24, 24);
        tv.setText(message);
        tv.setTextSize(14f);
        tv.setTextColor(Color.parseColor("#666666"));

        card.addView(tv);
        layoutScheduleList.addView(card);
    }

    private boolean isCurrentTimeInRange(String start, String end) {
        try {
            Date now = timeFormat.parse(timeFormat.format(Calendar.getInstance().getTime()));
            Date startTime = timeFormat.parse(start);
            Date endTime = timeFormat.parse(end);

            if (now == null || startTime == null || endTime == null) return false;

            return !now.before(startTime) && !now.after(endTime);
        } catch (ParseException e) {
            return false;
        }
    }

    private String convertToScheduleRoomId(String occupancyRoomId) {
        if (occupancyRoomId == null || occupancyRoomId.isEmpty()) return occupancyRoomId;

        if (occupancyRoomId.matches("^H\\d{3}$")) {
            return "H-" + occupancyRoomId.substring(1);
        }

        if (occupancyRoomId.matches("^H\\d{3}_\\d{2}$")) {
            String mainPart = occupancyRoomId.substring(1, 4);
            String subPart = occupancyRoomId.substring(5);
            return "H-" + mainPart + "." + subPart;
        }

        return occupancyRoomId;
    }

    private static class ScheduleSlot {
        String startTime;
        String endTime;

        ScheduleSlot(String startTime, String endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCurrentStatus();
        loadOccupancyHistory();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}