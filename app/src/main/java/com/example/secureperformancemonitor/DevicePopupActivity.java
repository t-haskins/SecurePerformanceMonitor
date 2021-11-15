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

import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattCharacteristic;

import android.util.DisplayMetrics;
import android.util.Log;
import java.util.List;
import java.util.UUID;

/*
Class to display BLE device info and connect/disconnect
Author: Tyler Haskins
*/

public class DevicePopupActivity extends AppCompatActivity {
    // tag used for methods to write logs
    private final static String TAG = "DevicePopup";

    private BluetoothLeService bluetoothLeService;
    private String deviceName;
    private String deviceAddress;
    private Button connectButton;
    private Button disconnectButton;
    private TextView update_device_state;
    private TextView update_device_name;
    private TextView update_device_address;
    private TextView update_device_rssi;
    private boolean bound = false;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    // UUID for Secure Performance Monitor BLE module
    public final static UUID UUID_SPM_CHARACTERISTIC =
            UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // bind to BluetoothLeService
        Intent intent = new Intent(this, BluetoothLeService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

        // create popup window
        setTitle(R.string.device_popup_title);
        setContentView(R.layout.activity_device_popup);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;
        getWindow().setLayout((int)(width*0.8), (int)(height*0.6));

        // get device name, address, and state ui
        update_device_name = findViewById(R.id.update_ble_data);
        update_device_address = findViewById(R.id.update_device_address);
        update_device_state = findViewById(R.id.update_device_state);
        update_device_rssi = findViewById(R.id.update_device_rssi);

        // instantiate connect button
        connectButton = findViewById(R.id.connect_button);
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectDevice();
                connectButton.setEnabled(false);
                updateConnectionState(R.string.device_state_connecting);
            }
        });

        // instantiate disconnect button
        disconnectButton = findViewById(R.id.disconnect_button);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnectDevice();
            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        // get device name and address
        deviceName = getIntent().getStringExtra("EXTRA_DEVICE_NAME");
        deviceAddress = getIntent().getStringExtra("EXTRA_DEVICE_ADDRESS");

        // update device name and address
        if (deviceName != null && deviceName.length() > 0) {
            update_device_name.setText(deviceName);
        } else {
            update_device_name.setText(R.string.unknown_device);
        }
        update_device_address.setText(deviceAddress);

        // store default rssi string until device is connected
        update_device_rssi.setText("N/A");
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(gattUpdateReceiver);
        unbindService(connection);
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

            // setup connection state and buttons
            if (bluetoothLeService.getConnectionState() == STATE_CONNECTED  && deviceAddress.equals(bluetoothLeService.getDeviceAddress())) {
                connectButton.setEnabled(false);
                disconnectButton.setEnabled(true);
                update_device_state.setText(R.string.device_state_connected);
            } else {
                connectButton.setEnabled(true);
                disconnectButton.setEnabled(false);
                update_device_state.setText(R.string.device_state_disconnected);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
        }
    };

    // update the BLE device connection state
    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                update_device_state.setText(resourceId);
            }
        });
    }

    // update the BLE device RSSI
    private void updateRSSI(String rssi) {
        update_device_rssi.setText(rssi);
    }

    // connect to BLE device
    public void connectDevice() {
        if (bound) {
            bluetoothLeService.connect(deviceAddress);
        }
    }

    // disconnect BLE device
    public void disconnectDevice() {
        if (bound) {
            bluetoothLeService.disconnect();
        }
    }

    // sets notifications for Secure Performance Monitor data if UUID matches
    // displays a list of all supported GATT services to log for debugging purposes
    public void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices.isEmpty()) {
            Log.i("GATT Table", "No services available");
        } else {
            String uuid;
            for (BluetoothGattService gattService : gattServices) {
                uuid = gattService.getUuid().toString();
                Log.i("GATT Table", "Service: " + uuid);
                List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    uuid = gattCharacteristic.getUuid().toString();
                    Log.i("GATT Table", "Characteristic: " + uuid);
                    // characteristic UUID is the same as the SPM characteristic UUID
                    if (gattCharacteristic.getUuid().equals(UUID_SPM_CHARACTERISTIC)) {
                        final int characteristicProperties = gattCharacteristic.getProperties();
                        // characteristic has the notify property enabled, set characteristic notifications
                        if ((characteristicProperties | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            bluetoothLeService.setCharacteristicNotification(gattCharacteristic, true);
                        }
                    }
                }
            }
        }
    }

    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                updateConnectionState(R.string.device_state_connected);
                connectButton.setEnabled(false);
                disconnectButton.setEnabled(true);
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                updateConnectionState(R.string.device_state_disconnected);
                connectButton.setEnabled(true);
                disconnectButton.setEnabled(false);
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                displayGattServices(bluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_RSSI_AVAILABLE.equals(action)) {
                updateRSSI(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_RSSI_AVAILABLE);
        return intentFilter;
    }
}