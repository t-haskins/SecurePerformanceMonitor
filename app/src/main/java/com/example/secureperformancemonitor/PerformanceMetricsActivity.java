package com.example.secureperformancemonitor;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;

import android.util.Log;

import java.math.BigInteger;

/*
Main class to decrypt, parse, and display performance metrics
Author: Tyler Haskins
Edited: Kyle Holman
*/

public class PerformanceMetricsActivity extends AppCompatActivity {
    // tag used for methods to write logs
    private final static String TAG = "PerformanceMetricsActivity";

    private BluetoothLeService bluetoothLeService;
    private TextView update_device_state_main;
    private TextView update_heart_rate;
    private TextView update_speed;
    private TextView update_temperature;
    private TextView update_pressure;
    private TextView update_humidity;
    private TextView update_latitude;
    private TextView update_longitude;
    private TextView update_altitude;
    private TextView update_steps;
    private TextView update_x_force;
    private TextView update_y_force;
    private TextView update_z_force;
    private Button rawDataButton;
    private Button mapButton;
    private boolean bound;
    private double lat = 999, lng = 999;

    // security variables
    private StringBuilder stringBuilder = new StringBuilder();
    private StringBuilder stringBuilder2 = new StringBuilder();
    private String transmission = null;
    private boolean transmitted = false;
    public static boolean passwordEntered = false;
    public static String password;
    public static BigInteger privateKey = null;
    public static boolean key = true;
    // end of security variables

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_performance_metrics);

        // start the BluetoothLeService
        Intent intent = new Intent(this, BluetoothLeService.class);
        startService(intent);

        // setup toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(R.string.main_activity_title);

        // get performance metrics ui
        update_device_state_main = findViewById(R.id.update_device_state_main);
        update_heart_rate = findViewById(R.id.update_heart_rate);
        update_speed = findViewById(R.id.update_speed);
        update_temperature = findViewById(R.id.update_temperature);
        update_pressure = findViewById(R.id.update_pressure);
        update_humidity = findViewById(R.id.update_humidity);
        update_latitude = findViewById(R.id.update_latitude);
        update_longitude = findViewById(R.id.update_longitude);
        update_altitude = findViewById(R.id.update_altitude);
        update_steps = findViewById(R.id.update_steps);
        update_x_force = findViewById(R.id.update_x_force);
        update_y_force = findViewById(R.id.update_y_force);
        update_z_force = findViewById(R.id.update_z_force);

        // instantiate raw data button
        rawDataButton = findViewById(R.id.raw_data_button);
        rawDataButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                openRawDataPopup();
                return true;
            }
        });

        // instantiate map button
        mapButton = findViewById(R.id.map_button);
        mapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(PerformanceMetricsActivity.this, MapsActivity.class);
                Bundle extras = new Bundle();
                extras.putDouble("lat", lat);
                extras.putDouble("lng", lng);
                intent.putExtras(extras);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // bind to BluetoothLeService
        Intent intent = new Intent(this, BluetoothLeService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

        // register receiver
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());

        // password popup
        if (!passwordEntered) {
            intent = new Intent(PerformanceMetricsActivity.this, PasswordPopupActivity.class);
            startActivity(intent);
        }

        if (passwordEntered && key) {
            privateKey = AppPasswordService.getKey(password);
            key = false;
            password = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // unbind from BluetoothLeService
        unbindService(connection);
        // unregister receiver
        unregisterReceiver(gattUpdateReceiver);
    }

    // method to create action buttons from xml file
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.main_app_bar, menu);
        return true;
    }

    // method to handle action button actions
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            // user chose bluetooth settings, launch BluetoothLeActivity
            Intent intent = new Intent(this, DeviceScanActivity.class);
            startActivity(intent);
            return true;
        } else {
            // user action was not recognized, invoke superclass to handle it
            return super.onOptionsItemSelected(item);
        }
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
                bound = false;
            } else {
                bound = true;
            }

            // setup connection state
            if (bluetoothLeService.getConnectionState() == STATE_CONNECTED) {
                update_device_state_main.setText(R.string.device_state_connected);
            } else {
                update_device_state_main.setText(R.string.device_state_disconnected);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            // close the current Bluetooth GATT client
            bluetoothLeService.close();
        }
    };

    // method to open raw data popup
    private void openRawDataPopup() {
        Intent intent = new Intent(PerformanceMetricsActivity.this, RawDataPopupActivity.class);
        startActivity(intent);
    }

    // method to display the data
    private void displayData(String data) {
        //Log.i(TAG, "Transmitted Data: " + data);
        // generate a string based off beginning and end of data transmission
        for (int i = 0; i < data.length(); i++) {
            char c = data.charAt(i);
            if ((c == 'B') || (c == 'b')) {
                // clear the string builder for new data
                transmitted = false;
                stringBuilder.delete(0, stringBuilder.length());
            } else if ((c == 'E') || (c == 'e')) {
                // generate string transmission variable
                transmission = stringBuilder.toString();
                // clear the string builder for new data
                stringBuilder.delete(0, stringBuilder.length());
                // check that the string is not empty
                if ((transmission != null) && (transmission.length() > 0)) {
                    transmitted = true;
                }
            } else {
                stringBuilder.append(c);
            }
        }

        // error handling if transmitted string is not encrypted
        if (transmitted) {
            for (int i = 0; i < transmission.length(); i++) {
                if (!Character.isDigit(transmission.charAt(i))) {
                    transmitted = false;
                }
            }
        }

        if (transmitted)
        {
            Log.i(TAG, "Transmitted String: " + transmission);

            // security subsystem by Kyle Holman
            BigInteger mod = new BigInteger("10283216039871810935867070308763590033267924706279480036805479708407577958672010439491502023522562278580887361154108790868660131671345775095268853731990497");
            BigInteger encData = new BigInteger(transmission);
            //Log.i(TAG, "Decryption Check1: " + String.valueOf(encData));
            BigInteger decData = encData.modPow(privateKey,mod);
            //Log.i(TAG, "Decryption Check2: " + String.valueOf(decData));
            String table="==========1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ.  ";

            String output="";
            BigInteger zero = new BigInteger("0");
            BigInteger rem;
            BigInteger hund = new BigInteger("100");
            int index = 0;
            int success=1;
               while ((decData.compareTo(zero)) == 1) {
                   rem = decData.mod(hund);
                   decData = decData.subtract(rem);
                   decData = decData.divide(hund);
                   index = rem.intValue();
                   if (index > table.length()) {
                       success = 0;
                       break;
                   }
                   output = output + "" + table.charAt(index);
               }
                if(success==1) {
                    transmission = output;
                }
                else{
                    transmission=null;
                }
            Log.i(TAG, "Decrypted String: " + transmission);
            // end of security subsystem

            // parse data into respective text displays
            if (transmission != null) {
                for (int i = 0; i < transmission.length(); i++) {
                    char c = transmission.charAt(i);
                    stringBuilder2.delete(0, stringBuilder2.length());

                    // latitude
                    if ((c == 'A') || (c == 'a')) {
                        for (int j = i+1; j < transmission.length(); j++) {
                            if (Character.isDigit(transmission.charAt(j))) {
                                stringBuilder2.append(transmission.charAt(j));
                            } else if (transmission.charAt(j) == '.') {
                                stringBuilder2.append(transmission.charAt(j));
                            } else {
                                i = j-1;
                                break;
                            }
                        }
                        if (stringBuilder2.length() > 0) {
                            // convert from decimal degrees to degrees minutes seconds
                            double tempDouble1 = Double.parseDouble(stringBuilder2.toString());

                            // adjustment made for sensor/encryption limitations
                            tempDouble1 -= 90;

                            // account for out of bounds condition
                            if (tempDouble1 < -90) {
                                tempDouble1 = -90;
                            }
                            if (tempDouble1 > 90) {
                                tempDouble1 = 90;
                            }

                            //
                            lat = tempDouble1;
                            boolean north = true;
                            if (tempDouble1 < 0) {
                                north = false;
                                tempDouble1 *= -1;
                            }
                            String tempString1 = String.format("%.4f", tempDouble1);

                            String[] coordinates1 = tempString1.split("\\.");
                            String degrees = coordinates1[0];
                            String degreesRemainder = "0." + coordinates1[1];
                            double tempDouble2 = Double.parseDouble(degreesRemainder) * 60;
                            String tempString2 = String.format("%.4f", tempDouble2);

                            String[] coordinates2 = tempString2.split("\\.");
                            String minutes = coordinates2[0];
                            String minutesRemainder = "0." + coordinates2[1];
                            double tempDouble3 = Double.parseDouble(minutesRemainder) * 60;
                            String seconds = String.format("%.2f", tempDouble3);

                            String formattedCoordinates;
                            if (north) {
                                formattedCoordinates = (degrees + "°" + minutes + "'" + seconds + "\"" + " N");
                            } else {
                                formattedCoordinates = (degrees + "°" + minutes + "'" + seconds + "\"" + " S");
                            }
                            update_latitude.setText(formattedCoordinates);
                        }

                    // longitude
                    } else if ((c == 'O') || (c == 'o')) {
                        for (int j = i+1; j < transmission.length(); j++) {
                            if (Character.isDigit(transmission.charAt(j))) {
                                stringBuilder2.append(transmission.charAt(j));
                            } else if (transmission.charAt(j) == '.') {
                                stringBuilder2.append(transmission.charAt(j));
                            } else {
                                i = j-1;
                                break;
                            }
                        }
                        if (stringBuilder2.length() > 0) {
                            // convert from decimal degrees to degrees minutes seconds
                            double tempDouble1 = Double.parseDouble(stringBuilder2.toString());

                            // adjustment made for sensor/encryption limitations
                            tempDouble1 -= 180;

                            // account for out of bounds condition
                            if (tempDouble1 < -180) {
                                tempDouble1 = -180;
                            }
                            if (tempDouble1 > 180) {
                                tempDouble1 = 180;
                            }

                            lng = tempDouble1;
                            boolean east = true;
                            if (tempDouble1 < 0) {
                                east = false;
                                tempDouble1 *= -1;
                            }
                            String tempString1 = String.format("%.4f", tempDouble1);

                            String[] coordinates1 = tempString1.split("\\.");
                            String degrees = coordinates1[0];
                            String degreesRemainder = "0." + coordinates1[1];
                            double tempDouble2 = Double.parseDouble(degreesRemainder) * 60;
                            String tempString2 = String.format("%.4f", tempDouble2);

                            String[] coordinates2 = tempString2.split("\\.");
                            String minutes = coordinates2[0];
                            String minutesRemainder = "0." + coordinates2[1];
                            double tempDouble3 = Double.parseDouble(minutesRemainder) * 60;
                            String seconds = String.format("%.2f", tempDouble3);

                            String formattedCoordinates;
                            if (east) {
                                formattedCoordinates = (degrees + "°" + minutes + "'" + seconds + "\"" + " E");
                            } else {
                                formattedCoordinates = (degrees + "°" + minutes + "'" + seconds + "\"" + " W");
                            }
                            update_longitude.setText(formattedCoordinates);
                        }

                    // temperature
                    } else if ((c == 'T') || (c == 't')) {
                        for (int j = i+1; j < transmission.length(); j++) {
                            if (Character.isDigit(transmission.charAt(j))) {
                                stringBuilder2.append(transmission.charAt(j));
                            } else if (transmission.charAt(j) == '.') {
                                stringBuilder2.append(transmission.charAt(j));
                            } else {
                                i = j-1;
                                break;
                            }
                        }
                        if (stringBuilder2.length() > 0) {
                            // convert to integer then display
                            double tempDouble = Double.parseDouble(stringBuilder2.toString());
                            int tempInt = (int) tempDouble;
                            update_temperature.setText(String.valueOf(tempInt));
                        }

                    // pressure
                    } else if ((c == 'P') || (c == 'p')) {
                        for (int j = i+1; j < transmission.length(); j++) {
                            if (Character.isDigit(transmission.charAt(j))) {
                                stringBuilder2.append(transmission.charAt(j));
                            } else if (transmission.charAt(j) == '.') {
                                stringBuilder2.append(transmission.charAt(j));
                            } else {
                                i = j-1;
                                break;
                            }
                        }
                        if (stringBuilder2.length() > 0) {
                            double tempDouble = Double.parseDouble(stringBuilder2.toString());
                            String tempString = String.format("%.2f", tempDouble);
                            update_pressure.setText((tempString + "kPa"));
                        }

                    // humidity
                    } else if ((c == 'W') || (c == 'w')) {
                        for (int j = i+1; j < transmission.length(); j++) {
                            if (Character.isDigit(transmission.charAt(j))) {
                                stringBuilder2.append(transmission.charAt(j));
                            } else if (transmission.charAt(j) == '.') {
                                stringBuilder2.append(transmission.charAt(j));
                            } else {
                                i = j-1;
                                break;
                            }
                        }
                        if (stringBuilder2.length() > 0) {
                            // convert to integer then display
                            double tempDouble = Double.parseDouble(stringBuilder2.toString());
                            int tempInt = (int) tempDouble;
                            update_humidity.setText((String.valueOf(tempInt) + "%"));
                        }

                    // heart rate
                    } else if ((c == 'R') || (c == 'r')) {
                        for (int j = i+1; j < transmission.length(); j++) {
                            if (Character.isDigit(transmission.charAt(j))) {
                                stringBuilder2.append(transmission.charAt(j));
                            } else if (transmission.charAt(j) == '.') {
                                stringBuilder2.append(transmission.charAt(j));
                            } else {
                                i = j-1;
                                break;
                            }
                        }
                        if (stringBuilder2.length() > 0) {
                            // convert to integer then display
                            double tempDouble = Double.parseDouble(stringBuilder2.toString());
                            int tempInt = (int) tempDouble;
                            update_heart_rate.setText(String.valueOf(tempInt));
                        }

                    // altitude
                    } else if ((c == 'H') || (c == 'h')) {
                        for (int j = i+1; j < transmission.length(); j++) {
                            if (Character.isDigit(transmission.charAt(j))) {
                                stringBuilder2.append(transmission.charAt(j));
                            } else if (transmission.charAt(j) == '.') {
                                stringBuilder2.append(transmission.charAt(j));
                            } else {
                                i = j-1;
                                break;
                            }
                        }
                        if (stringBuilder2.length() > 0) {
                            double tempDouble = Double.parseDouble(stringBuilder2.toString());
                            String tempString = String.format("%.1f", tempDouble);
                            update_altitude.setText((tempString + "ft"));
                        }

                    // x g-force
                    } else if ((c == 'X') || (c == 'x')) {
                        for (int j = i+1; j < transmission.length(); j++) {
                            if (Character.isDigit(transmission.charAt(j))) {
                                stringBuilder2.append(transmission.charAt(j));
                            } else if (transmission.charAt(j) == '.') {
                                stringBuilder2.append(transmission.charAt(j));
                            } else {
                                i = j-1;
                                break;
                            }
                        }
                        if (stringBuilder2.length() > 0) {
                            double tempDouble = Double.parseDouble(stringBuilder2.toString());
                            String tempString = String.format("%.1f", tempDouble);
                            update_x_force.setText((tempString + "g"));
                        }

                    // y g-force
                    } else if ((c == 'Y') || (c == 'y')) {
                        for (int j = i+1; j < transmission.length(); j++) {
                            if (Character.isDigit(transmission.charAt(j))) {
                                stringBuilder2.append(transmission.charAt(j));
                            } else if (transmission.charAt(j) == '.') {
                                stringBuilder2.append(transmission.charAt(j));
                            } else {
                                i = j-1;
                                break;
                            }
                        }
                        if (stringBuilder2.length() > 0) {
                            double tempDouble = Double.parseDouble(stringBuilder2.toString());
                            String tempString = String.format("%.1f", tempDouble);
                            update_y_force.setText((tempString + "g"));
                        }

                    // z g-force
                    } else if ((c == 'Z') || (c == 'z')) {
                        for (int j = i+1; j < transmission.length(); j++) {
                            if (Character.isDigit(transmission.charAt(j))) {
                                stringBuilder2.append(transmission.charAt(j));
                            } else if (transmission.charAt(j) == '.') {
                                stringBuilder2.append(transmission.charAt(j));
                            } else {
                                i = j-1;
                                break;
                            }
                        }
                        if (stringBuilder2.length() > 0) {
                            double tempDouble = Double.parseDouble(stringBuilder2.toString());
                            String tempString = String.format("%.1f", tempDouble);
                            update_z_force.setText((tempString + "g"));
                        }

                    // speed
                    } else if ((c == 'S') || (c == 's')) {
                        for (int j = i+1; j < transmission.length(); j++) {
                            if (Character.isDigit(transmission.charAt(j))) {
                                stringBuilder2.append(transmission.charAt(j));
                            } else if (transmission.charAt(j) == '.') {
                                stringBuilder2.append(transmission.charAt(j));
                            } else {
                                i = j-1;
                                break;
                            }
                        }
                        if (stringBuilder2.length() > 0) {
                            double tempDouble = Double.parseDouble(stringBuilder2.toString());
                            String tempString = String.format("%.1f", tempDouble);
                            update_speed.setText(tempString);
                        }

                    // steps
                    } else if ((c == 'C') || (c == 'c')) {
                        for (int j = i + 1; j < transmission.length(); j++) {
                            if (Character.isDigit(transmission.charAt(j))) {
                                stringBuilder2.append(transmission.charAt(j));
                            } else if (transmission.charAt(j) == '.') {
                                stringBuilder2.append(transmission.charAt(j));
                            } else {
                                i = j - 1;
                                break;
                            }
                        }
                        if (stringBuilder2.length() > 0) {
                            double tempDouble = Double.parseDouble(stringBuilder2.toString());
                            int tempInt = (int) tempDouble;
                            update_steps.setText(String.valueOf(tempInt));
                        }
                    }
                }
            }

        }
    }

    // update the BLE device connection state
    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                update_device_state_main.setText(resourceId);
            }
        });
    }

    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                updateConnectionState(R.string.device_state_connected);
                update_device_state_main.setText(R.string.device_state_connected);
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                updateConnectionState(R.string.device_state_disconnected);
                update_device_state_main.setText(R.string.device_state_disconnected);
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}