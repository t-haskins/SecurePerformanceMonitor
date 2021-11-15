package com.example.secureperformancemonitor;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.ActionBar;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Toast;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.ListView;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;

import java.util.ArrayList;
import android.util.Log;

/*
Class to initiate BLE scan and list available devices
Author: Tyler Haskins
*/

public class DeviceScanActivity extends AppCompatActivity {
    // tag used for methods to write logs
    private final static String TAG = "BluetoothLeActivity";

    private BluetoothLeService bluetoothLeService;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private LeDeviceListAdapter leDeviceListAdapter;
    private MenuItem startScan;
    private MenuItem stopScan;
    private Handler handler = new Handler();
    private ListView listView;
    private boolean scanning;
    private boolean bound;

    // scan period of 10 seconds
    private static final long SCAN_PERIOD = 10000;

    // constant passed to startActivityForResult, if constant >= 0 requestCode parameter will be
    // returned in onActivityResult() when the activity exits
    private static final int REQUEST_ENABLE_BT = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_scan);

        // setup toolbar
        Toolbar toolbar = findViewById(R.id.scanToolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // sets title
            actionBar.setTitle(R.string.bluetooth_le_activity_title);
            // enables the up action in toolbar
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // set leDeviceListAdapter as new adapter for list view
        listView = findViewById(R.id.listview);
        leDeviceListAdapter = new LeDeviceListAdapter();
        listView.setAdapter(leDeviceListAdapter);

        // initialize Bluetooth adapter
        final BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }

        // initialize BluetoothLeScanner
        if (bluetoothAdapter != null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        } else {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // check if device supports BLE
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            // display message that ble is not supported
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            // close activity, because device does not support BLE
            finish();
        }
    }

    // method to create action buttons from xml file
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.scan_app_bar, menu);

        // find the stop and start scan actions
        startScan = menu.findItem(R.id.action_scan);
        stopScan = menu.findItem(R.id.action_stop_scan);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        startScan.setVisible(true);
        stopScan.setVisible(false);
        return true;
    }

    // method to handle action button actions
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // handle item selection
        if (item.getItemId() == R.id.action_scan) {
            // user chose to scan, disconnect any devices and launch scan
            if (bound) {
                bluetoothLeService.disconnect();
            }
            leDeviceListAdapter.clear();
            scanLeDevice(true);
            return true;
        } else if (item.getItemId() == R.id.action_stop_scan) {
            scanLeDevice(false);
            return true;
        } else {
            // user action was not recognized, invoke superclass to handle it
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // bind to BluetoothLeService
        Intent intent = new Intent(this, BluetoothLeService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

        // check if Bluetooth is enabled, if not prompt user to enable Bluetooth
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // handle listview clicks
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final BluetoothDevice device = leDeviceListAdapter.getDevice(position);
                if (device != null) {
                    // open new popup activity with device data
                    Intent intent = new Intent(DeviceScanActivity.this, DevicePopupActivity.class);
                    // pass device name and address to popup activity
                    intent.putExtra("EXTRA_DEVICE_NAME", device.getName());
                    intent.putExtra("EXTRA_DEVICE_ADDRESS", device.getAddress());

                    // stop any scanning
                    if (scanning) {
                        scanLeDevice(false);
                    }

                    // launch new popup activity
                    startActivity(intent);
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // stop scan
        scanLeDevice(false);

        // unbind from BluetoothLeService
        unbindService(connection);
        bound = false;
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

    // scan for devices
    private void scanLeDevice(final boolean enable) {
        if (bluetoothLeScanner != null) {
            if (enable) {
                // make stopScan visible
                startScan.setVisible(false);
                stopScan.setVisible(true);

                // stop scan after SCAN_PERIOD has elapsed
                handler.postDelayed(new Runnable() {
                    public void run() {
                        scanLeDevice(false);
                    }
                }, SCAN_PERIOD);

                scanning = true;
                bluetoothLeScanner.startScan(leScanCallback);
                Toast.makeText(this, R.string.scanning, Toast.LENGTH_SHORT).show();
            } else {
                // make startScan visible
                startScan.setVisible(true);
                stopScan.setVisible(false);

                // send message that scanning has stopped
                if (scanning) {
                    Toast.makeText(this, R.string.scanning_finished, Toast.LENGTH_SHORT).show();
                }
                scanning = false;
                bluetoothLeScanner.stopScan(leScanCallback);
            }
        } else {
            scanning = false;
            Toast.makeText(this, R.string.bluetooth_scanner_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    // class to extend BaseAdapter for display of scanned Bluetooth devices
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> leDevices;
        private LayoutInflater layoutInflater;

        // constructor
        public LeDeviceListAdapter() {
            super();
            leDevices = new ArrayList<BluetoothDevice>();
            layoutInflater = DeviceScanActivity.this.getLayoutInflater();
        }

        // if device is not in array then add the device to the array
        public void addDevice(BluetoothDevice device) {
            if (!leDevices.contains(device)) {
                leDevices.add(device);
            }
        }

        // return the BLE device in the array at given position
        public BluetoothDevice getDevice(int position) {
            return leDevices.get(position);
        }

        // clear all devices in the array
        public void clear() {
            leDevices.clear();
        }

        // override BaseAdapter getCount method
        @Override
        public int getCount() {
            return leDevices.size();
        }

        // override BaseAdapter getItem method
        @Override
        public Object getItem(int position) {
            return leDevices.get(position);
        }

        // override BaseAdapter getItemId method
        @Override
        public long getItemId(int position) {
            return position;
        }

        // override BaseAdapter getView method
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;

            if (convertView == null) {
                convertView = layoutInflater.inflate(R.layout.list_row_devices, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = convertView.findViewById(R.id.device_address);
                viewHolder.deviceName = convertView.findViewById(R.id.device_name);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            BluetoothDevice device = leDevices.get(position);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0) {
                viewHolder.deviceName.setText(deviceName);
            } else {
                viewHolder.deviceName.setText(R.string.unknown_device);
            }
            viewHolder.deviceAddress.setText(device.getAddress());

            return convertView;
        }

    }

    // ViewHolder pattern for LeDeviceListAdapter getView method
    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }

    // scan callback for device scans
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            // create a new thread to update list with device
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    leDeviceListAdapter.addDevice(result.getDevice());
                    leDeviceListAdapter.notifyDataSetChanged();
                }
            });

        }
    };
}