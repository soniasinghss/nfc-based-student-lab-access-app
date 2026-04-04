package com.example.nfc_based_student_lab_access_app;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ManageOccupancyActivity extends AppCompatActivity {

    private LinearLayout llOccupancyContainer;
    private EditText etSearchRoom;
    private Spinner spinnerFloor;
    private DatabaseReference db;
    private DataSnapshot currentSnapshot;

    private String searchText = "";
    private String selectedFloor = "All";
    private boolean isGlobalEditMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_occupancy);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        db = FirebaseDatabase.getInstance().getReference();

        // Ensure these IDs match activity_manage_occupancy.xml exactly
        llOccupancyContainer = findViewById(R.id.llOccupancyContainer);
        etSearchRoom = findViewById(R.id.etSearchRoom);
        spinnerFloor = findViewById(R.id.spinnerFloor);

        setupFilters();
        setupOccupancyListener();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.occupancy_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_edit) {
            isGlobalEditMode = !isGlobalEditMode;

            // Null-safe check for the menu icon
            if (item.getIcon() != null) {
                if (isGlobalEditMode) {
                    item.getIcon().setColorFilter(Color.parseColor("#FFCCCC"), PorterDuff.Mode.SRC_IN);
                } else {
                    item.getIcon().clearColorFilter();
                }
            }

            updateUI();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupFilters() {
        String[] floors = {"All", "8", "9", "10", "11"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, floors);
        spinnerFloor.setAdapter(adapter);

        spinnerFloor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedFloor = floors[position];
                updateUI();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        etSearchRoom.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                searchText = s.toString().toLowerCase().trim();
                updateUI();
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
    }

    private void setupOccupancyListener() {
        db.child("occupancy").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentSnapshot = snapshot;
                updateUI();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateUI() {
        if (currentSnapshot == null || llOccupancyContainer == null) return;
        llOccupancyContainer.removeAllViews();

        for (DataSnapshot lab : currentSnapshot.getChildren()) {
            String labKey = lab.getKey();
            String name = lab.child("display_name").getValue(String.class);
            Long count = lab.child("current_count").getValue(Long.class);
            Long max = lab.child("max_capacity").getValue(Long.class);

            if (name == null) name = (labKey != null) ? labKey : "Unknown";

            // Null-safe Filter logic
            if (!selectedFloor.equals("All") && !name.startsWith(selectedFloor)) continue;
            if (!searchText.isEmpty() && !name.toLowerCase().contains(searchText)) continue;

            addLabCard(labKey, name, count != null ? count : 0, max != null ? max : 30);
        }
    }

    private void addLabCard(String labKey, String name, long count, long max) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, 20);
        card.setLayoutParams(params);
        card.setRadius(12f);
        card.setCardElevation(4f);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(30, 35, 30, 35);

        TextView tvName = new TextView(this);
        tvName.setText(String.format("Lab %s", name));
        tvName.setTextSize(16f);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        int btnSize = (int) (42 * getResources().getDisplayMetrics().density);

        Button btnMinus = new Button(this);
        btnMinus.setText("-");
        btnMinus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FFE0E0")));
        btnMinus.setLayoutParams(new LinearLayout.LayoutParams(btnSize, btnSize));
        btnMinus.setVisibility(isGlobalEditMode ? View.VISIBLE : View.GONE);
        btnMinus.setOnClickListener(v -> adjustOccupancy(labKey, -1));

        TextView tvOcc = new TextView(this);
        tvOcc.setText(String.format("%d / %d", count, max));
        tvOcc.setPadding(25, 0, 25, 0);
        tvOcc.setTextSize(15f);
        tvOcc.setTypeface(null, Typeface.BOLD);

        if (isGlobalEditMode) {
            tvOcc.setTextColor(Color.parseColor("#7B1A2E"));
            tvOcc.setOnClickListener(v -> showMaxEditDialog(labKey, name, max));
        }

        Button btnPlus = new Button(this);
        btnPlus.setText("+");
        btnPlus.setTextColor(Color.WHITE);
        btnPlus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#7B1A2E")));
        btnPlus.setLayoutParams(new LinearLayout.LayoutParams(btnSize, btnSize));
        btnPlus.setVisibility(isGlobalEditMode ? View.VISIBLE : View.GONE);
        btnPlus.setOnClickListener(v -> adjustOccupancy(labKey, 1));

        row.addView(tvName);
        row.addView(btnMinus);
        row.addView(tvOcc);
        row.addView(btnPlus);
        card.addView(row);
        llOccupancyContainer.addView(card);
    }

    private void showMaxEditDialog(String labKey, String name, long currentMax) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Update Max Capacity: " + name);
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(currentMax));
        input.setPadding(50, 40, 50, 40);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String val = input.getText().toString();
            if (!val.isEmpty()) {
                db.child("occupancy").child(labKey).child("max_capacity").setValue(Long.parseLong(val));
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void adjustOccupancy(String labKey, int delta) {
        if (labKey == null) return;
        db.child("occupancy").child(labKey).child("current_count").get().addOnSuccessListener(snapshot -> {
            Long val = snapshot.getValue(Long.class);
            if (val != null) {
                long newVal = val + delta;
                if (newVal >= 0) db.child("occupancy").child(labKey).child("current_count").setValue(newVal);
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        // Updated to use non-deprecated method
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}