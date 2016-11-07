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

package com.dreamshooter.myapplication;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.text.DecimalFormat;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Logger;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.win16.bluetoothclass6.R;

/**
 * 对于一个BLE设备，该activity向用户提供设备连接，显示数据，显示GATT服务和设备的字符串支持等界面，
 * 另外这个activity还与BluetoothLeService通讯，反过来与Bluetooth LE API进行通讯
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = "test";

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    //连接状态
    private TextView mConnectionState;
    private EditText mDataField;
    private String mDeviceName;
    private String mDeviceAddress;

    private Button button_send_value; // 发送按钮
    private EditText edittext_input_value; // 数据在这里输入

    private BluetoothLeService mBluetoothLeService;

    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    //写数据
    private BluetoothGattCharacteristic writecharacteristic;
    private BluetoothGattService mnotyGattService;

    //读数据
    private BluetoothGattCharacteristic readCharacteristic;
    byte[] WriteBytes = new byte[20];

    StringBuilder stringBuilder;

    private int count = 0;

    private boolean mReceive = false;

    private double date = 0.0;

    // 管理服务的生命周期
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

    // Handles various events fired by the Service.处理服务所激发的各种事件
    // ACTION_GATT_CONNECTED: connected to a GATT server.连接一个GATT服务
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.从GATT服务中断开连接
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.查找GATT服务
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    // or notification operations.从服务中接受数据
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
                clearUI();
            }
            //发现有可支持的服务
            else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.i(TAG, "onReceive: " + "test");
                //读数据的服务和characteristic
                mnotyGattService = mBluetoothLeService.getSupportedGattServices(UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"));
                readCharacteristic = mnotyGattService.getCharacteristic(UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E"));
                mBluetoothLeService.setCharacteristicNotification(readCharacteristic, true);
                //写数据的characteristic
                writecharacteristic = mnotyGattService.getCharacteristic(UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E"));
            }
            //显示数据
            else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                //将数据显示在mDataField上
                byte[] data = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                System.out.println("data----" + data);

                displayData(data);
            }
        }
    };

    private void clearUI() {
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);
        final Intent intent = getIntent();
        stringBuilder = new StringBuilder();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        Log.i(TAG, "onCreate: " + mDeviceName + " " + mDeviceAddress);
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (EditText) findViewById(R.id.data_value);

        button_send_value = (Button) findViewById(R.id.button_send_value);
        edittext_input_value = (EditText) findViewById(R.id.edittext_input_value);

        button_send_value.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String string = edittext_input_value.getText().toString();
                sendMessage(string);
            }
        });
        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }


    void sendMessage(String data) {
        byte[] value = new byte[20];
        value[0] = (byte) 0x00;
        WriteBytes = data.getBytes();
        writecharacteristic.setValue(value[0], BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        writecharacteristic.setValue(WriteBytes);
        mBluetoothLeService.writeCharacteristic(writecharacteristic);
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
        switch (item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                saveFile(stringBuilder.toString());
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(byte[] data) {
        if (data != null) {
            int Cx = ((data[1] * 256) + ((data[0]) & 0x0ff));
            int Cy = ((data[3] * 256) + ((data[2]) & 0x0ff));
            int Cz = ((data[5] * 256) + ((data[4]) & 0x0ff));

            int Wx = ((data[7] * 256) + ((data[6]) & 0x0ff));
            int Wy = ((data[9] * 256) + ((data[8]) & 0x0ff));
            int Wz = ((data[11] * 256) + ((data[10]) & 0x0ff));

            double Ax = ((data[13] * 256) + ((data[12]) & 0x0ff)) / 16384.0;
            double Ay = ((data[15] * 256) + ((data[14]) & 0x0ff)) / 16384.0;
            double Az = ((data[17] * 256) + ((data[16]) & 0x0ff)) / 16384.0;
            DecimalFormat df = new DecimalFormat("######0.00");
            DecimalFormat df1 = new DecimalFormat("######0.0");
            String string1 = mDataField.getText().toString();
            StringBuilder stringBuilder1 = new StringBuilder();
            stringBuilder1.append(string1);
            stringBuilder1.append("\n");
            stringBuilder1.append(df.format(date) + " " + Cx + " " + Cy + " " + Cz + " " + Wx + " " + Wy + " " + Wz + " " + df1.format(Ax) + " " + df1.format(Ay) + " " + df1.format(Az));
            //           stringBuilder1.append(df.format(date) +  " " + +Cz + " " + data[4] + " " + data[5]);
            mDataField.setText(stringBuilder1.toString());
            mDataField.setSelection(mDataField.length());

            stringBuilder.append(df.format(date) + " " + Cx + " " + Cy + " " + Cz + " " + Wx + " " + Wy + " " + Wz + " " + df1.format(Ax) + " " + df1.format(Ay) + " " + df1.format(Az));
//            stringBuilder.append(df.format(date) + " " + +Cz + " " + data[4] + " " + data[5]);
            stringBuilder.append("\r\r\n");
            date = date + 0.04;
        }

        if (data != null) {
            int Cx = ((data[1] * 256) + ((data[0]) & 0x0ff));
            int Cy = ((data[3] * 256) + ((data[2]) & 0x0ff));
            int Cz = ((data[5] * 256) + ((data[4]) & 0x0ff));

            int Wx = ((data[7] * 256) + ((data[6]) & 0x0ff));
            int Wy = ((data[9] * 256) + ((data[8]) & 0x0ff));
            int Wz = ((data[11] * 256) + ((data[10]) & 0x0ff));

            double Ax = ((data[13] * 256) + ((data[12]) & 0x0ff)) / 16384.0;
            double Ay = ((data[15] * 256) + ((data[14]) & 0x0ff)) / 16384.0;
            double Az = ((data[17] * 256) + ((data[16]) & 0x0ff)) / 16384.0;
            DecimalFormat df = new DecimalFormat("######0.00");
            DecimalFormat df1 = new DecimalFormat("######0.0");
            String string1 = mDataField.getText().toString();
            StringBuilder stringBuilder1 = new StringBuilder();
            stringBuilder1.append(string1);
            stringBuilder1.append("\n");
            stringBuilder1.append(df.format(date) + " " + Cx + " " + Cy + " " + Cz + " " + Wx + " " + Wy + " " + Wz + " " + df1.format(Ax) + " " + df1.format(Ay) + " " + df1.format(Az));
            //           stringBuilder1.append(df.format(date) +  " " + +Cz + " " + data[4] + " " + data[5]);
            mDataField.setText(stringBuilder1.toString());
            mDataField.setSelection(mDataField.length());

            stringBuilder.append(df.format(date) + " " + Cx + " " + Cy + " " + Cz + " " + Wx + " " + Wy + " " + Wz + " " + df1.format(Ax) + " " + df1.format(Ay) + " " + df1.format(Az));
//            stringBuilder.append(df.format(date) + " " + +Cz + " " + data[4] + " " + data[5]);
            stringBuilder.append("\r\r\n");
            date = date + 0.04;
        }
    }


    public static void saveFile(String str) {
        String filePath = null;
        boolean hasSDCard = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        if (hasSDCard) { // SD卡根目录的hello.text
            filePath = Environment.getExternalStorageDirectory().toString() + File.separator + "helloworld.txt";
            Log.i("filepath", filePath);
        } else  // 系统下载缓存根目录的hello.text
            filePath = Environment.getDownloadCacheDirectory().toString() + File.separator + "helloworld.txt";
        Log.i("filepath", filePath);
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                File dir = new File(file.getParent());
                dir.mkdirs();
                file.createNewFile();
            }
            FileOutputStream outStream = new FileOutputStream(file);
            outStream.write(str.getBytes());
            outStream.close();
        } catch (Exception e) {
            e.printStackTrace();
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

