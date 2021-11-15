package com.example.secureperformancemonitor;

import android.app.Service;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothGattService;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.List;
import android.util.Log;

/*
Service to initialize and handle BLE connections
Author: Tyler Haskins
*/

public class BluetoothLeService extends Service {
    // tag used for methods to write logs
    private static final String TAG = "BluetoothLeService";

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private String deviceAddress;

    // default connection state before any devices connect
    private int connectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    // string constants
    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_RSSI_AVAILABLE =
            "com.example.bluetooth.le.ACTION_RSSI_AVAILABLE";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    // UUIDs for Secure Performance Monitor BLE module
    public final static UUID UUID_SPM_SERVICE =
            UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    public final static UUID UUID_SPM_CHARACTERISTIC =
            UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    // setup binders for BluetoothLeService
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            // return this instance of BluetoothLeService so clients can call public methods
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // initialize BluetoothLeService
    public boolean initialize() {
        // check for bluetoothManager
        if (bluetoothManager == null) {
            // initialize bluetoothManager
            bluetoothManager = getSystemService(BluetoothManager.class);
            if (bluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager");
                return false;
            }
        }
        // check if bluetoothAdapter can be acquired
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter");
            return false;
        }
        return true;
    }

    // method to connect to the BLE device
    public boolean connect(final String address) {
        // bluetooth adapter not initialized or unspecified address
        if (bluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address");
            return false;
        }

        // initialize BLE device
        final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        // device not found
        if (device == null) {
            Log.w(TAG, "Device not found, unable to connect");
            return false;
        }

        // connect to device
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
        Log.d(TAG, "Trying to create a new connection");
        deviceAddress = address;
        connectionState = STATE_CONNECTING;
        return true;
    }

    // method to disconnect from the BLE device
    public void disconnect() {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        // disconnect from device
        bluetoothGatt.disconnect();
        deviceAddress = null;
    }

    // method to close current Bluetooth GATT client to release resources
    public void close() {
        if (bluetoothGatt == null) {
            return;
        }
        bluetoothGatt.close();
        bluetoothGatt = null;
    }

    // read a given BluetoothGattCharacteristic
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        bluetoothGatt.readCharacteristic(characteristic);
    }

    // set notifications for a given BluetoothGattCharacteristic
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        bluetoothGatt.setCharacteristicNotification(characteristic, enabled);
    }

    // bluetoothGattCallback for methods to connect and disconnect from BLE device
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                connectionState = STATE_CONNECTED;
                bluetoothGatt.readRemoteRssi();
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server");
                Log.i(TAG, "Attempting to start service discover: " +
                        bluetoothGatt.discoverServices());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                connectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                Log.i(TAG, "Characteristic successfully read: " + characteristic.getUuid());
            } else if(status == BluetoothGatt.GATT_READ_NOT_PERMITTED) {
                Log.w(TAG, "Characteristic read not permitted: " + characteristic.getUuid());
            } else {
                Log.e(TAG, "Characteristic read failed: " + characteristic.getUuid());
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            Log.i(TAG, "Characteristic changed: " + characteristic.getUuid());
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_RSSI_AVAILABLE, String.valueOf(rssi));
                Log.i(TAG, "Rssi read: " + rssi);
            }
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, final String data) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DATA, data);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        final byte[] data = characteristic.getValue();
        if (UUID_SPM_CHARACTERISTIC.equals(characteristic.getUuid())) {
            // HM-10 Bluetooth module puts transmitted string in characteristic value
            if (data != null && data.length > 0) {
                // convert byte array to ASCII string
                String performanceMetricsString = new String(data, StandardCharsets.UTF_8);
                intent.putExtra(EXTRA_DATA, performanceMetricsString);
            }
        } else {
            // for other GATT profiles write the data formatted in Hexadecimal
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data) {
                    stringBuilder.append(String.format("%02X", byteChar));
                }
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            }
        }
        sendBroadcast(intent);
    }

    // method to return currently connected device's address
    public String getDeviceAddress() {
        return deviceAddress;
    }

    // method to return if device is connected
    public int getConnectionState() {
        return connectionState;
    }

    // returns a list of supported GATT services on the connected device
    public List<BluetoothGattService> getSupportedGattServices() {
        if (bluetoothGatt == null) {
            return null;
        }
        return bluetoothGatt.getServices();
    }

}
