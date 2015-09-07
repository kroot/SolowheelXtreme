/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.inventist.xtreme;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.sql.Time;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class XtremeGaugesActivity extends Activity {
    private final static String TAG = "solowheel"; //.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

   // private TextView mConnectionState;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private Double previousVoltage = 0d;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();

                finish();

            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());

            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                //displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));


                Double chargePercent = intent.getDoubleExtra(BluetoothLeService.EXTRA_DATA_CHARGE_PERCENT, 0);
                Double chargeVolts = intent.getDoubleExtra(BluetoothLeService.EXTRA_DATA_CHARGE_VOLTS, 0);
                Boolean direction = intent.getBooleanExtra(BluetoothLeService.EXTRA_DATA_DIRECTION, true);
                Double speed = intent.getDoubleExtra(BluetoothLeService.EXTRA_DATA_SPEED, 0);


                displayData(chargePercent, chargeVolts, speed, direction);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gauges);

        findViewById(R.id.green_arrow_up).setVisibility(View.GONE);
        findViewById(R.id.red_arrow_down).setVisibility(View.GONE);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setTitle(mDeviceAddress);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
               // clearMacAddress(mDeviceAddress);
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
               // clearMacAddress(mDeviceAddress);
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(final Double chargePercent, final Double chargeVolts, final Double speed, final boolean forward) {

        if ((chargePercent != null) && (speed != null)){
            BatteryGauge f = (BatteryGauge) findViewById(R.id.reading1);
            f.setFullPercent(chargePercent.intValue());

            TextView tv = (TextView) findViewById(R.id.volts);
            tv.setText(new DecimalFormat("##.##").format(chargeVolts) + "v");

            Locale loc = this.getResources().getConfiguration().locale;
            boolean useMph = (loc.getISO3Country().equalsIgnoreCase("usa") || loc.getISO3Country().equalsIgnoreCase("mmr"));

            TextView tvSpeed = (TextView) findViewById(R.id.tvSpeed);
            TextView tvSpeedUnits = (TextView) findViewById(R.id.tvSpeedUnits);

            //String formattedSpeed;
            if (useMph) {
                tvSpeed.setText(String.format("%.1f", speed));
                tvSpeedUnits.setText("MPH");
//                formattedSpeed = String.format("%.1f", speed) + " MPH";
            }
            else {
                tvSpeed.setText(String.format("%.1f", speed * 1.6));
                tvSpeedUnits.setText("KPH");
//                formattedSpeed = String.format("%.1f", speed * 1.6) + " KPH";
            }

            findViewById(R.id.green_arrow_up).setVisibility(chargeVolts > previousVoltage ? View.VISIBLE : View.GONE);
            findViewById(R.id.red_arrow_down).setVisibility(chargeVolts < previousVoltage ? View.VISIBLE : View.GONE);
            findViewById(R.id.noArrow).setVisibility(chargeVolts.equals(previousVoltage) ? View.VISIBLE : View.GONE);
            previousVoltage = chargeVolts;
        }
    }


    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuidService;
        String uuidCharacteristic;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuidService = gattService.getUuid().toString();

            // for Solowheel, the only service we care about is the one with the serial port
            String SOLOWHEEL_UUID = "0000fff0-0000-1000-8000-00805f9b34fb";
            if (uuidService.equals(SOLOWHEEL_UUID)) {

                currentServiceData.put(LIST_NAME, unknownServiceString);
                currentServiceData.put(LIST_UUID, uuidService);
                gattServiceData.add(currentServiceData);

                ArrayList<HashMap<String, String>> gattCharacteristicGroupData = new ArrayList<>();
                List<BluetoothGattCharacteristic> gattCharacteristics =
                        gattService.getCharacteristics();
                ArrayList<BluetoothGattCharacteristic> charas = new ArrayList<>();

                // Loops through available Characteristics.
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    charas.add(gattCharacteristic);
                    HashMap<String, String> currentCharaData = new HashMap<String, String>();
                    uuidCharacteristic = gattCharacteristic.getUuid().toString();
                    currentCharaData.put(LIST_NAME, unknownCharaString);
                    currentCharaData.put(LIST_UUID, uuidCharacteristic);
                    gattCharacteristicGroupData.add(currentCharaData);

                    // for Solowheel, the only characteristic we care about is the serial port
                    String SOLOWHEEL_DATAPORT = "0000fff7-0000-1000-8000-00805f9b34fb";
                    if (uuidCharacteristic.equals(SOLOWHEEL_DATAPORT)) {

                        if (mGattCharacteristics != null) {

                            final int charaProp = gattCharacteristic.getProperties();

                            // Even though this port supports read, we only need notify
                            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                                mNotifyCharacteristic = gattCharacteristic;
                                mBluetoothLeService.setCharacteristicNotification(
                                        gattCharacteristic, true);
                            }
                        }
                    }
                }
                mGattCharacteristics.add(charas);
                gattCharacteristicData.add(gattCharacteristicGroupData);
            }
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
