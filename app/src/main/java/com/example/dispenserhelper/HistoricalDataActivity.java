package com.example.dispenserhelper;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

public class HistoricalDataActivity extends AppCompatActivity {
    private static BluetoothLowEnergyService BleService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historical_data);
        SetTableLayout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register the broadcast receiver.
        // This specified the messages the main activity looks for from the PSoCCapSenseLedService
        final IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothLowEnergyService.ACTION_BLESCAN_CALLBACK);
        filter.addAction(BluetoothLowEnergyService.ACTION_BLESCAN_FAIL);
        filter.addAction(BluetoothLowEnergyService.ACTION_CONNECTED);
        filter.addAction(BluetoothLowEnergyService.ACTION_DISCONNECTED);
        filter.addAction(BluetoothLowEnergyService.ACTION_SERVICES_DISCOVERED);
        filter.addAction(BluetoothLowEnergyService.ACTION_DATA_RECEIVED);
        registerReceiver(mBleUpdateReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBleUpdateReceiver);
    }

    private void SetTableLayout() {
        TextView textview;
        int u1all = 0;
        int u2all = 0;
        int u3all = 0;
        String temp;

        try {
            FileInputStream inputStream = openFileInput("historyData.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            textview = findViewById(R.id.u1high);
            temp = reader.readLine();
            u1all += Integer.valueOf(temp);
            textview.setText(temp);

            textview = findViewById(R.id.u1low);
            temp = reader.readLine();
            u1all += Integer.valueOf(temp);
            textview.setText(temp);

            textview = findViewById(R.id.u2high);
            temp = reader.readLine();
            u2all += Integer.valueOf(temp);
            textview.setText(temp);

            textview = findViewById(R.id.u2low);
            temp = reader.readLine();
            u2all += Integer.valueOf(temp);
            textview.setText(temp);

            textview = findViewById(R.id.u3high);
            temp = reader.readLine();
            u3all += Integer.valueOf(temp);
            textview.setText(temp);

            textview = findViewById(R.id.u3low);
            temp = reader.readLine();
            u3all += Integer.valueOf(temp);
            textview.setText(temp);

            textview = findViewById(R.id.u1all);
            textview.setText(String.valueOf(u1all));

            textview = findViewById(R.id.u2all);
            textview.setText(String.valueOf(u2all));

            textview = findViewById(R.id.u3all);
            textview.setText(String.valueOf(u3all));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * This manages the lifecycle of the BLE service.
     * When the service starts we get the service object and initialize the service.
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        /**
         * This is called when the PSoCCapSenseLedService is connected
         *
         * @param componentName the component name of the service that has been connected
         * @param service service being bound
         */
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.d("Service Connection", "connected");
            BleService = ((BluetoothLowEnergyService.LocalBinder) service).getService();
            BleService.initialize();
            BleService.scan();
        }

        /**
         * This is called when the PSoCCapSenseService is disconnected.
         *
         * @param componentName the component name of the service that has been connected
         */
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d("Service Connection", "unconnected");
            BleService = null;
        }
    };

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void InitiateBle(View view) {
        // Find BLE service and adapter
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }

        while (!mBluetoothAdapter.isEnabled()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Start the BLE Service
        Intent gattServiceIntent = new Intent(this, BluetoothLowEnergyService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    public void UpdateData() {
        BleService.readLedCharacteristic(BleService.user1DataCharacteristic);
        BleService.readLedCharacteristic(BleService.user2DataCharacteristic);
        BleService.readLedCharacteristic(BleService.user3DataCharacteristic);
    }

    private void ScanFail() {
        BleService.setAlertDialog(this, "Can't find device");
        unbindService(mServiceConnection);
    }

    private final BroadcastReceiver mBleUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case BluetoothLowEnergyService.ACTION_BLESCAN_CALLBACK:
                        Log.d("Broadcast Receiver", "Scan Callback");
                        BleService.connect();
                        break;

                    case BluetoothLowEnergyService.ACTION_BLESCAN_FAIL:
                        Log.d("Broadcast Receiver", "Device not Found");
                        ScanFail();
                        break;

                    case BluetoothLowEnergyService.ACTION_CONNECTED:
                        Log.d("Broadcast Receiver", "Connected");
                        BleService.discoverServices();
                        break;

                    case BluetoothLowEnergyService.ACTION_DISCONNECTED:
                        Log.d("Broadcast Receiver", "Disconnected");
                        BleService.close();
                        unbindService(mServiceConnection);
                        break;

                    case BluetoothLowEnergyService.ACTION_SERVICES_DISCOVERED:
                        Log.d("Broadcast Receiver", "Service Discovered");
                        UpdateData();
                        break;

                    case BluetoothLowEnergyService.ACTION_DATA_RECEIVED:
                        Log.d("Broadcast Receiver", "Data Received");
                        if (BleService.user1DataUpdateFlag && BleService.user2DataUpdateFlag && BleService.user3DataUpdateFlag) {
                            UpdateHistoryData();
                            BleService.user1DataUpdateFlag = false;
                            BleService.user2DataUpdateFlag = false;
                            BleService.user3DataUpdateFlag = false;
                            SetTableLayout();
                            BleService.disconnect();
                        }
                        break;
                }
            }
        }
    };

    private void UpdateHistoryData(){
        try {
            FileOutputStream outputStream = openFileOutput("historyData.txt", Context.MODE_PRIVATE);
            outputStream.write((Integer.toString(BleService.user1Data[0] & 0xFF)+"\n").getBytes());
            outputStream.write((Integer.toString(BleService.user1Data[1] & 0xFF)+"\n").getBytes());
            outputStream.write((Integer.toString(BleService.user2Data[0] & 0xFF)+"\n").getBytes());
            outputStream.write((Integer.toString(BleService.user2Data[1] & 0xFF)+"\n").getBytes());
            outputStream.write((Integer.toString(BleService.user3Data[0] & 0xFF)+"\n").getBytes());
            outputStream.write((Integer.toString(BleService.user3Data[1] & 0xFF)+"\n").getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void ClearData(View view){
        try {
            FileOutputStream outputStream = openFileOutput("historyData.txt", Context.MODE_PRIVATE);
            outputStream.write("0\n".getBytes());
            outputStream.write("0\n".getBytes());
            outputStream.write("0\n".getBytes());
            outputStream.write("0\n".getBytes());
            outputStream.write("0\n".getBytes());
            outputStream.write("0\n".getBytes());
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        SetTableLayout();
    }
}
