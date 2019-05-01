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
import android.widget.EditText;
import android.widget.RadioGroup;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

public class ManageUserActivity extends AppCompatActivity {

    private static BluetoothLowEnergyService BleService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String line;
        String [] userData;
        EditText name;
        RadioGroup userRBGroup;

        int [] u_name = {R.id.edittext_u1_name, R.id.edittext_u2_name, R.id.edittext_u3_name};
        int [] u_rbGroup = {R.id.u1_rb_group, R.id.u2_rb_group, R.id.u3_rb_group};
        int [][] u_rbButtons = {{R.id.u1_rb_high, R.id.u1_rb_low},
                {R.id.u2_rb_high, R.id.u2_rb_low},
                {R.id.u3_rb_high, R.id.u3_rb_low}};

        setContentView(R.layout.activity_manage_user);

        try {
            FileInputStream inputStream = openFileInput("userSettings.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            for (int i=0; i<3; i++) {
                line = reader.readLine();
                userData = line.split(",");
                name = findViewById(u_name[i]);
                userRBGroup = findViewById(u_rbGroup[i]);
                name.setText(userData[0]);
                if (userData[1].equals("1")) {
                    userRBGroup.check(u_rbButtons[i][0]);
                } else if (userData[1].equals("0")) {
                    userRBGroup.check(u_rbButtons[i][1]);
                }
            }

            inputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    private void SaveUserSettings() {
        String line;
        EditText name;
        RadioGroup userRBGroup;
        int userSelectedAmount;
        String indicator;
        byte[] one = {1};
        byte[] zero = {0};

        int [] u_name = {R.id.edittext_u1_name, R.id.edittext_u2_name, R.id.edittext_u3_name};

        // Clean File / Save Input to Internal Storage
        try {
            FileOutputStream outputStream = openFileOutput("userSettings.txt", Context.MODE_PRIVATE);

            name = findViewById(u_name[0]);
            userRBGroup = findViewById(R.id.u1_rb_group);
            userSelectedAmount = userRBGroup.getCheckedRadioButtonId();
            if (userSelectedAmount == R.id.u1_rb_high) {
                indicator = "1";
                BleService.writeCharacteristic(BleService.user1AmountCharacteristic, one);
            } else {
                indicator = "0";
                BleService.writeCharacteristic(BleService.user1AmountCharacteristic, zero);
            }
            line = name.getText().toString() + "," + indicator + "\n";
            outputStream.write(line.getBytes());


            name = findViewById(u_name[1]);
            userRBGroup = findViewById(R.id.u2_rb_group);
            userSelectedAmount = userRBGroup.getCheckedRadioButtonId();
            if (userSelectedAmount == R.id.u2_rb_high) {
                indicator = "1";
                BleService.writeCharacteristic(BleService.user2AmountCharacteristic, one);
            } else {
                indicator = "0";
                BleService.writeCharacteristic(BleService.user2AmountCharacteristic, zero);
            }
            line = name.getText().toString() + "," + indicator + "\n";
            outputStream.write(line.getBytes());

            name = findViewById(u_name[2]);
            userRBGroup = findViewById(R.id.u3_rb_group);
            userSelectedAmount = userRBGroup.getCheckedRadioButtonId();
            if (userSelectedAmount == R.id.u3_rb_high) {
                indicator = "1";
                BleService.writeCharacteristic(BleService.user3AmountCharacteristic, one);
            } else {
                indicator = "0";
                BleService.writeCharacteristic(BleService.user3AmountCharacteristic, zero);
            }
            line = name.getText().toString() + "," + indicator + "\n";
            outputStream.write(line.getBytes());

            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        BleService.setAlertDialog(this, "Settings Updated");
    }

    private void BackToMain() {
        BleService.close();
        unbindService(mServiceConnection);
        BleService = null;
        // Close and unbind the service when the activity goes away
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
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
                        BackToMain();
                        break;

                    case BluetoothLowEnergyService.ACTION_SERVICES_DISCOVERED:
                        Log.d("Broadcast Receiver", "Service Discovered");
                        SaveUserSettings();
                        break;

                    case BluetoothLowEnergyService.ACTION_DATA_RECEIVED:
                        Log.d("Broadcast Receiver", "Data Received");
                        break;
                }
            }
        }
    };
}
