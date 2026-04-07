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

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class LabDetailActivity extends AppCompatActivity {

    private TextView tvHeaderLabName;
    private TextView tvLabOccupancy;
    private TextView tvLabStatus;
    private TextView tvSelectedDate;
    private TextView btnPrevDay;
    private TextView btnNextDay;
    private LinearLayout layoutScheduleList;

    private DatabaseReference occupancyRef;
    private DatabaseReference scheduleRef;

    private String roomId;
    private String scheduleRoomId;
    private Calendar selectedDate;

    private Integer currentOccupancyCount = null;
    private Integer currentMaxCapacity = null;
    private boolean currentIsScheduledNow = false;
    private String currentSlotText = null;

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

        scheduleRoomId = convertToScheduleRoomId(roomId);
        selectedDate = Calendar.getInstance();

        tvHeaderLabName.setText(roomId);
        updateDisplayedDate();

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        occupancyRef = database.getReference("occupancy").child(roomId);
        scheduleRef = database.getReference("lab_schedule").child(scheduleRoomId);

        loadOccupancy();
        loadScheduleForDate();

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

    private void loadScheduleForDate() {
        String firebaseDate = firebaseDateFormat.format(selectedDate.getTime());

        scheduleRef.child(firebaseDate).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                layoutScheduleList.removeAllViews();

                boolean isScheduledNow = false;
                String slotText = null;

                Calendar now = Calendar.getInstance();
                String todayString = firebaseDateFormat.format(now.getTime());

                java.util.List<ScheduleSlot> slots = new java.util.ArrayList<>();

                for (DataSnapshot slotSnapshot : snapshot.getChildren()) {
                    String startTime = slotSnapshot.child("start_time").getValue(String.class);
                    String endTime = slotSnapshot.child("end_time").getValue(String.class);

                    if (startTime == null || endTime == null) {
                        continue;
                    }

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

                    if (firebaseDate.equals(todayString) && isCurrentTimeInRange(slot.startTime, slot.endTime)) {
                        isScheduledNow = true;
                        slotText = slot.startTime + " - " + slot.endTime;
                    }
                }

                if (slots.isEmpty()) {
                    addMessageRow("No schedule for this day");
                }

                currentIsScheduledNow = isScheduledNow;
                currentSlotText = slotText;

                refreshStatusUI();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                layoutScheduleList.removeAllViews();
                addMessageRow("Could not load schedule");

                currentIsScheduledNow = false;
                currentSlotText = null;
                refreshStatusUI();
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

    private void addScheduleRow(String startTime, String endTime) {
        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 12);
        card.setLayoutParams(cardParams);
        card.setRadius(20f);
        card.setCardElevation(2f);
        card.setCardBackgroundColor(Color.parseColor("#F9E8ED"));

        LinearLayout content = new LinearLayout(this);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
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
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 12);
        card.setLayoutParams(cardParams);
        card.setRadius(20f);
        card.setCardElevation(2f);
        card.setCardBackgroundColor(Color.parseColor("#F8F8F8"));

        TextView tv = new TextView(this);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
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
        if (occupancyRoomId == null || occupancyRoomId.isEmpty()) {
            return occupancyRoomId;
        }

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
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}