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

package com.inventist.solowheel.xtreme;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.List;
import java.util.Locale;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = "solowheel"; // BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;
    private long mLastWatchUpdateTime = 0;
    private GoogleApiClient mGoogleApiClient;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.inventist.solowheel.xtreme.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.inventist.solowheel.xtreme.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.inventist.solowheel.xtreme.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.inventist.solowheel.xtreme.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.inventist.solowheel.xtreme.EXTRA_DATA";
    public final static String EXTRA_DATA_SPEED =
            "com.inventist.solowheel.xtreme.EXTRA_DATA_SPEED";
    public final static String EXTRA_DATA_CHARGE_PERCENT =
            "com.inventist.solowheel.xtreme.EXTRA_DATA_CHARGE_PERCENT";
    public final static String EXTRA_DATA_CHARGE_VOLTS =
            "com.inventist.solowheel.xtreme.EXTRA_DATA_CHARGE_VOLTS";
    public final static String EXTRA_DATA_DIRECTION =
            "com.inventist.solowheel.xtreme.EXTRA_DATA_DIRECTION";

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

                initGoogleApiClient();

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);

                disconnectGoogleClient();
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
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);


        final byte[] data = characteristic.getValue();

        if ((data != null) && (data.length > 0)){
            Log.d(TAG, "Data=" + data);

            String s = new String(data);
            String[] swValues = s.split(",");
            if (swValues != null)
            {
                switch (swValues.length)
                {
                case 3:
                    // If the last field doesn't look like the direction, ignore the message
                    String direction = swValues[2].trim();
                    boolean isValid = false;
                    if (!TextUtils.isEmpty(direction))
                    {
                        try
                        {
                            Integer i = Integer.parseInt(direction);
                            isValid = ((i == 0) || (i == 1));
                        }
                        catch(Exception ex) {}
                    }
                    if (isValid)
                    {
                        Double speedMPH = 0.0;
                        Double percent = 0.0;
                        Double batteryDouble = 0.0;

                        String speed = swValues[0].trim();

                        if (!TextUtils.isEmpty(speed))
                        {
                            try {
                                Double speedCmPerSecond = Double.parseDouble(speed);
                                Double fudgeFactor = .80;
                                speedCmPerSecond *= fudgeFactor;

                                Double speedCmPerHour = speedCmPerSecond * 60 * 60;
                                Double speedKmPerHour = speedCmPerHour / 100000;
                                speedMPH = speedKmPerHour * 0.6214;
                            }
                            catch(Exception ex) {}
                        }

                        String batteryUnformatted = swValues[1].trim();

                        if (!TextUtils.isEmpty(batteryUnformatted))
                        {
                            try {
                                String volts = batteryUnformatted.substring(0, batteryUnformatted.length() - 1);
                                char[] voltsArray = batteryUnformatted.toCharArray();
                                char digit = voltsArray[voltsArray.length - 1];
                                String battery = volts + "." + digit;

                                Double full = 58.0;

                                // Mine vibrated at 46.8v when I ran it down completely.
                                Double empty = 47.0d;

                                Double fullRange = full - empty;
                                batteryDouble = Double.parseDouble(battery);

                                Double actualRange = batteryDouble - empty;
                                actualRange = actualRange < 0 ? 0 : actualRange;  // don't allow negative

                                percent = ((actualRange * 100) / fullRange);
                                if (percent.compareTo(100.0) > 0)
                                    percent = 100.0;
                                else if (percent.compareTo(0.0) < 0)
                                    percent = 0.0;
                            }
                            catch(Exception ex) {}
                        }

                        intent.putExtra(EXTRA_DATA_CHARGE_PERCENT, new Double(percent));
                        intent.putExtra(EXTRA_DATA_CHARGE_VOLTS, new Double(batteryDouble));
                        intent.putExtra(EXTRA_DATA_DIRECTION, new Boolean(direction.equals("00001") ? true : false));
                        intent.putExtra(EXTRA_DATA_SPEED, new Double(speedMPH));

                        sendBroadcast(intent);

                        // Wear support
                        SendWearMessage(speedMPH, percent);
                        break;
                    }
                }
            }
        }
    }

    private void SendWearMessage(Double speedMPH, Double percent) {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            long now = System.currentTimeMillis();
            if (now - mLastWatchUpdateTime > 1000) {
                mLastWatchUpdateTime = now;

                Locale loc = this.getResources().getConfiguration().locale;
                boolean useMph = (loc.getISO3Country().equalsIgnoreCase("usa") || loc.getISO3Country().equalsIgnoreCase("mmr"));

                final String message = String.format(
                        "%d", percent.intValue()) + "," +
                        String.format("%.1f", speedMPH) + "," +
                        (useMph ? "MPH" : "KPH");

                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();

                            for (Node node : nodes.getNodes()) {
                                MessageApi.SendMessageResult result =
                                        Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), "/solowheelxtreme", message.getBytes()).await();
                            }
                            Log.i(TAG, "Wear message sent: " + message);
                        } catch (Exception e) {
                            Log.e(TAG, e.getMessage());
                        }
                    }
                });
                t.start();
            }
        }
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    private void disconnectGoogleClient()
    {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            final String message = "0,0, ";
            NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();

            for (Node node : nodes.getNodes()) {
                MessageApi.SendMessageResult result =
                        Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), "/solowheelxtreme", message.getBytes()).await();
            }

            mGoogleApiClient.disconnect();
        }

        mGoogleApiClient = null;
    }

    private void initGoogleApiClient() {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected())
        {
            Log.i(TAG, "Google API client already connected");
        }
        else
        {
            if (mGoogleApiClient == null)
            {
                mGoogleApiClient = new GoogleApiClient.Builder(this)
                        .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(TAG, "Google API client connected");

                            }

                            @Override
                            public void onConnectionSuspended(int i) {

                            }
                        })
                        .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                            @Override
                            public void onConnectionFailed(ConnectionResult connectionResult) {
                                Log.i(TAG, "Google API onConnectionFailed: " + connectionResult);
                            }
                        })
                        .addApi(Wearable.API)
                        .build();
            }

            if (!mGoogleApiClient.isConnected())
                mGoogleApiClient.connect();
        }
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }
}
