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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
@TargetApi(21)
public class DeviceScanActivity extends ListActivity {
    private final static String TAG = "XtremeScan";

    public final static String SHARED_PREF_NAME = "solowheelxtreme";
    public final static String LAST_MAC_ADDRESS = "lastmac";

    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;

    private BluetoothAdapter.LeScanCallback mLeScanCallback;

    // Android 5.0 or above BLE support only (not backwards compatible)
    private ScanSettings settings;
    private BluetoothLeScanner mLeScanner50;
    private List<ScanFilter> filters;
    private BluetoothGatt mGatt;
    private ScanCallback mScanCallback50;

    private boolean mScanning;
    private Handler mHandler;
    private String lastMacAddress;

    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 5 minutes.
    private static final long SCAN_PERIOD = 5 * 60 * 1000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "DeviceScan onCreate");

        super.onCreate(savedInstanceState);
        getActionBar().setTitle(R.string.title_devices);
        mHandler = new Handler();

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // The newer BLE APIs are only supported in 5.0 or above.
        if (Build.VERSION.SDK_INT >= 21 && mScanCallback50 == null) {
            mScanCallback50 = new ScanCallback() {

                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    Log.i("callbackType", String.valueOf(callbackType));
                    Log.i("result", result.toString());

                    if (mScanning) {
                        BluetoothDevice btDevice = result.getDevice();


                        //connectToDevice50(btDevice);


                        String name = btDevice.getName();

                        if (name != null) {
                            Log.v(TAG, "onLeScan device found: " + name);

                            // only look for Solowheel devices
                            if (name.equals("EXTREME")) {
                                // Log.i(TAG, "rssi = " + rssi);

                                boolean found = false;
                                for (DeviceContainer dev : mLeDeviceListAdapter.mLeDevices) {
                                    if (dev.device.getAddress().equals(btDevice.getAddress())) {
                                        found = true;

                                        dev.rssi = result.getRssi();
                                        mLeDeviceListAdapter.refresh();
                                    }
                                    break;
                                }
                                if (!found) {
                                    Log.v(TAG, "XTREME found");
                                    mLeDeviceListAdapter.addDevice(btDevice, result.getRssi());
                                }

                                int devNum = checkLastMacAddress();
                                if (devNum != -1) {
                                    Log.v(TAG, "Last Address! Launching gauges");

                                    displayGauges(devNum);
                                    return;
                                }
                            }
                        }
                    }
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    for (ScanResult sr : results) {
                        Log.i("ScanResult - Results", sr.toString());
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.e("Scan Failed", "Error Code: " + errorCode);
                }
            };

        }
        else  // prior to Android 5.0
        {
            mLeScanCallback =
                new BluetoothAdapter.LeScanCallback() {

                    @Override
                    public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {

                                if (device != null && mScanning == true) {
                                    String name = device.getName();

                                    if (name != null) {
                                        Log.v(TAG, "onLeScan device found: " + name);

                                        // only look for Solowheel devices
                                        if (name.equals("EXTREME")) {
                                            Log.i(TAG, "rssi = " + rssi);

                                            boolean found = false;
                                            for (DeviceContainer dev : mLeDeviceListAdapter.mLeDevices) {
                                                if (dev.device.getAddress().equals(device.getAddress())) {
                                                    found = true;

                                                    dev.rssi = rssi;
                                                    mLeDeviceListAdapter.refresh();
                                                }
                                                break;
                                            }
                                            if (!found) {
                                                Log.v(TAG, "XTREME found");
                                                mLeDeviceListAdapter.addDevice(device, rssi);
                                            }

                                            int devNum = checkLastMacAddress();
                                            if (devNum != -1) {
                                                Log.v(TAG, "Last Address! Launching gauges");

                                                displayGauges(devNum);
                                                return;
                                            }
                                        }
                                    }
                                }
                            }
                        });
                    }
                };

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                mLeDeviceListAdapter.clear();
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                mLeDeviceListAdapter.clear();
                scanLeDevice(false);

                // if user hits stop, clear the last mac address.
                lastMacAddress = "0";
                saveMacAddress("1");
                break;
        }
        return true;
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "DeviceScan onResume");

        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            if (Build.VERSION.SDK_INT >= 21) {
                mLeScanner50 = mBluetoothAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                filters = new ArrayList<ScanFilter>();
            }
            scanLeDevice(true);
        }


        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
//        if (!mBluetoothAdapter.isEnabled()) {
//            if (!mBluetoothAdapter.isEnabled()) {
//                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
//            }
//        }

        // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        setListAdapter(mLeDeviceListAdapter);

        lastMacAddress = getLastMacAddress();

        mLeDeviceListAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
            }
        });
//        scanLeDevice(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "DeviceScan onActivityResult");

        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scanLeDevice(false);

        if (mGatt != null) {
            mGatt.close();
            mGatt = null;
        }
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "DeviceScan onPause");

        super.onPause();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        displayGauges(position);
    }

    private void displayGauges(int position) {
        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
        if (device == null) return;

        scanLeDevice(false);

        final String newMacAddress = device.getAddress();
        final Intent intent = new Intent(this, XtremeGaugesActivity.class);
        intent.putExtra(XtremeGaugesActivity.EXTRAS_DEVICE_NAME, device.getName());
        intent.putExtra(XtremeGaugesActivity.EXTRAS_DEVICE_ADDRESS, newMacAddress);

        if (!newMacAddress.equals(lastMacAddress)) {
            lastMacAddress = newMacAddress;
            saveMacAddress(lastMacAddress);
        }

        startActivity(intent);
    }

    public void saveMacAddress(String macAddress) {
        SharedPreferences settings = getSharedPreferences(SHARED_PREF_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(LAST_MAC_ADDRESS, macAddress);
        editor.commit();
    }

    public String getLastMacAddress() {
        SharedPreferences settings = getSharedPreferences(SHARED_PREF_NAME, 0);
        return settings.getString(LAST_MAC_ADDRESS, "");
    }

    private int checkLastMacAddress() {
        int numDevices = mLeDeviceListAdapter.getCount();
        for (int i = 0; i < numDevices; i++) {
            BluetoothDevice device = mLeDeviceListAdapter.getDevice(i);
            if (device.getAddress().equals(lastMacAddress))
            {
                return i;
            }
        }
        return -1;
    }

    private void scanLeDevice(final boolean enable) {
        Log.i(TAG, "scanLeDevice: " + enable + "  mBluetoothAdapter: " + mBluetoothAdapter);

        if (enable) {

/*            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT < 21) {
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    } else {
                        mLeScanner50.stopScan(mScanCallback50);

                    }
                }
            }, SCAN_PERIOD);*/

            if (!mScanning)
            {
                Log.i(TAG, "scanLeDevice: Start scan");

                if (Build.VERSION.SDK_INT < 21) {
                    mBluetoothAdapter.startLeScan(mLeScanCallback);
                } else {
                    mLeScanner50.startScan(filters, settings, mScanCallback50);
                }
                mScanning = true;
            }
        } else {
            if (mScanning) {
                Log.i(TAG, "scanLeDevice: Stop scan");

                if (Build.VERSION.SDK_INT < 21) {
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                } else {
                    mLeScanner50.stopScan(mScanCallback50);
                }
                mScanning = false;
            }
        }

        invalidateOptionsMenu();
    }

    class DeviceContainer {
        int rssi;
        BluetoothDevice device;

        DeviceContainer(BluetoothDevice device, int rssi) {
            this.rssi = rssi;
            this.device = device;
        }
    }

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
        TextView deviceRssi;
    }

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {


        private ArrayList<DeviceContainer> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<DeviceContainer>();
            mInflator = DeviceScanActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device, int rssi) {
            if(!mLeDevices.contains(device)) {
                mLeDevices.add(new DeviceContainer(device, rssi));
                notifyDataSetChanged();
            }
        }

        public BluetoothDevice getDevice(int position) {
            return (mLeDevices.size() > position ? mLeDevices.get(position).device : null);
        }

        public void clear() {
            mLeDevices.clear();
            notifyDataSetChanged();
        }

        public void refresh() {
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceRssi = (TextView) view.findViewById(R.id.device_rssi);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i).device;
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                if (deviceName.equals("EXTREME"))
                    viewHolder.deviceName.setText("XTREME");  // workaround for firmware broadcast
                else
                    viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.unknown_device);
            viewHolder.deviceAddress.setText(device.getAddress());
            viewHolder.deviceRssi.setText(mLeDevices.get(i).rssi + " dBm");

            int bondState = device.getBondState();
            int contents = device.describeContents();

            return view;
        }

        private ArrayList<DataSetObserver> observers = new ArrayList<DataSetObserver>();

//        @Override
//        public void registerDataSetObserver(DataSetObserver observer) {
//            super.registerDataSetObserver(observer);
//        }
//
//        @Override
//        public void notifyDataSetChanged() {
//            super.notifyDataSetChanged();
//            for (DataSetObserver observer : observers) {
//                observer.onChanged();
//            }
//        }
    }

}