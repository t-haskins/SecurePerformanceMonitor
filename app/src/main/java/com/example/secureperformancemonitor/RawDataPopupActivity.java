package com.example.secureperformancemonitor;

import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import android.widget.TextView;

import android.util.DisplayMetrics;
import android.util.Log;

/*
Class to display raw BLE data
Author: Tyler Haskins
*/

public class RawDataPopupActivity extends AppCompatActivity {
    // tag used for methods to write logs
    private final static String TAG = "RawDataPopup";

    private BluetoothLeService bluetoothLeService;
    private TextView update_ble_data;
    private boolean bound = false;

    StringBuilder stringBuilder = new StringBuilder();
    String transmission = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // bind to BluetoothLeService
        Intent intent = new Intent(this, BluetoothLeService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

        // create popup window
        setTitle(R.string.raw_data_popup_title);
        setContentView(R.layout.activity_raw_data_popup);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;
        getWindow().setLayout((int)(width*0.8), (int)(height*0.6));

        // get raw ble data ui
        update_ble_data = findViewById(R.id.update_ble_data);
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onResume() {
        super.onResume();
        // bind to BluetoothLeService
        Intent intent = new Intent(this, BluetoothLeService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
        // register receiver
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    protected void onStop() {
        super.onStop();
        // unbind from BluetoothLeService
        unbindService(connection);
        // unregister receiver
        unregisterReceiver(gattUpdateReceiver);
    }

    // manage BluetoothLeService connection
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothLeService.LocalBinder binder = (BluetoothLeService.LocalBinder) service;
            bluetoothLeService = binder.getService();

            // BluetoothLeService cant initialize Bluetooth
            if (!bluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            bound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
        }
    };

    // method to display the data
    private void displayData(String data) {
        // generate a string based off beginning and end of data transmission
        for (int i = 0; i < data.length(); i++) {
            char c = data.charAt(i);
            if ((c == 'B') || (c == 'b')) {
                // clear the string for new data
                stringBuilder.delete(0, stringBuilder.length());
            } else if ((c == 'E') || (c == 'e')) {
                transmission = stringBuilder.toString();
            } else {
                stringBuilder.append(c);
            }
        }

        if (data != null && !data.contentEquals(update_ble_data.getText())) {
            update_ble_data.setText(transmission);
        }
    }

    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}