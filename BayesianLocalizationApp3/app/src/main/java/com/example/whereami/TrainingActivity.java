package com.example.whereami;

import android.os.Handler;
import android.os.Message;
import android.view.View.OnClickListener;
import android.Manifest;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class TrainingActivity extends AppCompatActivity implements OnClickListener {

    // Manager and Receiver
    private WifiManager wifiManager;
    WifiReceiver receiverWifi;

    // Storage helpers
    SQLiteDatabase db;

    // UI Elements
    TableLayout tableSamples;
    Button trainButton;
    Spinner cellSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_train);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Obtain database endpoint
        db = openOrCreateDatabase("database.db", MODE_PRIVATE, null);

        // Initialize wifi manager and enable wifi if off
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled()) {
            Toast.makeText(getApplicationContext(), "Turning WiFi ON...", Toast.LENGTH_LONG).show();
            wifiManager.setWifiEnabled(true);
        }

        // Load statistics in table
        tableSamples = (TableLayout) findViewById(R.id.table_samples);
        tableSamples.setStretchAllColumns(true);
        loadTableData();

        // Set values for dropdown spinner
        cellSpinner = (Spinner) findViewById(R.id.spinner_cell);
        ArrayAdapter<String> cellAdapter = new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item, getResources().getStringArray(R.array.cell_array));
        cellSpinner.setAdapter(cellAdapter);

        // Add button
        trainButton = (Button) findViewById(R.id.button_add_training);
        trainButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_add_training: {
                int idxCell = cellSpinner.getSelectedItemPosition();
                addToTraining(idxCell);
                break;
            }
        }
    }

    // Method that adds training samples to the database
    public void addToTraining(int cellID) {
        if (ActivityCompat.checkSelfPermission(TrainingActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(TrainingActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            Toast.makeText(getApplicationContext(), R.string.error_add_training_text, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this,"SAMPLING STARTED FOR THIS CELL", Toast.LENGTH_SHORT).show();
            Toast toast = Toast.makeText(getApplicationContext(),"SAMPLING FINISHED FOR THIS CELL", Toast.LENGTH_SHORT);

            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    int nScans = 10;
                    int scanned = 0;
                    int scanID = Database.getMaximumScanID(db);

                    try {
                        while(scanned < nScans) {
                            scanID += 1;
                            Thread.sleep(10000);
                            Log.i("Scan ",""+scanned);
                            Util.train(wifiManager,db,cellID, scanID);
                            scanned++;

                            Message message = new Message();
                            updateTableHandler.sendMessage(message);

                        }
                        toast.show();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
            thread.start();
        }
    }

    // Handler that updates the table with training data gathered
    private Handler updateTableHandler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            loadTableData();
        }
    };

    // Method that loads the data of the table, removes current data and inserts new data
    public void loadTableData() {

        this.tableSamples.removeAllViews();

        int[] cellCount = new int[8];
        int totalCount = 0;

        for(int i = 0; i < cellCount.length; i++) {
            int count = Database.getTrainingCount(db,i);
            cellCount[i] = count;
            totalCount += count;
        }

        String[] allCells = getResources().getStringArray(R.array.cell_array);

        for(int i = 0; i < 9; i++) {
            String nameCell,sampleCell;
            int fontSize = 14;
            if(i < 8) {
                nameCell = allCells[i];
                sampleCell = cellCount[i] + " scans";
            }else{
                nameCell = getResources().getString(R.string.textview_label_total_samples);
                sampleCell = totalCount+" scans";
                fontSize = 16;
            }

            TableRow tableRow = new TableRow(this);
            tableRow.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));

            TextView textViewLeft = new TextView(this);
            textViewLeft.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
            textViewLeft.setText(nameCell);
            textViewLeft.setTextColor(Color.WHITE);
            textViewLeft.setTextSize(TypedValue.COMPLEX_UNIT_SP,fontSize);
            tableRow.addView(textViewLeft);
            textViewLeft.setHeight(50);
            textViewLeft.setTypeface(Typeface.DEFAULT_BOLD);

            TextView textViewRight = new TextView(this);
            textViewRight.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
            textViewRight.setText(sampleCell);
            textViewRight.setTextColor(Color.WHITE);
            textViewRight.setTextSize(TypedValue.COMPLEX_UNIT_SP,fontSize);

            tableRow.addView(textViewRight);
            this.tableSamples.addView(tableRow, new TableLayout.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));
        }
    }

    private void getWifi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(TrainingActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(TrainingActivity.this, "location turned off", Toast.LENGTH_SHORT).show();
                ActivityCompat.requestPermissions(TrainingActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            } else {
                wifiManager.startScan();
            }
        } else {
            Toast.makeText(TrainingActivity.this, "scanning", Toast.LENGTH_SHORT).show();

        }
    }

    @Override
    public boolean onSupportNavigateUp(){
        finish();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        this.loadTableData();
        receiverWifi = new WifiReceiver(wifiManager);
        registerReceiver(receiverWifi, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        getWifi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiverWifi);
    }
}
