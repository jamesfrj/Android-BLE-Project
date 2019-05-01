package com.example.dispenserhelper;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

@TargetApi(Build.VERSION_CODES.LOLLIPOP) // This is required to allow us to use the lollipop and later scan APIs
public class BluetoothLowEnergyService extends Service {
    private final static String TAG = BluetoothLowEnergyService.class.getSimpleName();

    // Bluetooth objects that we need to interact with
    private static BluetoothManager mBluetoothManager;
    private static BluetoothAdapter mBluetoothAdapter;
    private static BluetoothLeScanner mLEScanner;
    private static BluetoothDevice mLeDevice;
    private static BluetoothGatt mBluetoothGatt;

    // Bluetooth characteristics that we need to read/write
    public BluetoothGattCharacteristic user1AmountCharacteristic;
    public BluetoothGattCharacteristic user1DataCharacteristic;
    public BluetoothGattCharacteristic user2AmountCharacteristic;
    public BluetoothGattCharacteristic user2DataCharacteristic;
    public BluetoothGattCharacteristic user3AmountCharacteristic;
    public BluetoothGattCharacteristic user3DataCharacteristic;
    public BluetoothGattCharacteristic refillToothpasteCharacteristic;

    // UUIDs for the service and characteristics that the custom CapSenseLED service uses
    private final static String dispenserServiceUUID           =   "505429BB-3124-4D65-AEF6-62D2E9125190";
    public  final static String user1AmountCharacteristicUUID  =   "1995C829-7766-4013-ABC5-1A37B5C33329";
    public  final static String user1DataCharacteristicUUID    =   "39801DA6-0706-44A9-8C54-36CA3BC13EB7";
    public  final static String user2AmountCharacteristicUUID  =   "7AC473FB-1E9B-45E0-9A00-6FC6EB68611E";
    public  final static String user2DataCharacteristicUUID    =   "EAAAD40A-B615-4C87-9F31-7DECE6A5EC64";
    public  final static String user3AmountCharacteristicUUID  =   "12018801-1CF0-463B-915B-CB43EDED3CDC";
    public  final static String user3DataCharacteristicUUID    =   "9F6B2A34-5251-4B1E-8583-F79E078837D0";
    public  final static String refillToothpasteCharacteristicUUID = "969B2611-BD70-42E2-8519-49743FCC1D5E";

    // Actions used during broadcasts to the main activity
    public final static String ACTION_BLESCAN_CALLBACK    = "ACTION_BLESCAN_CALLBACK";
    public final static String ACTION_BLESCAN_FAIL        = "ACTION_BLESCAN_FAIL";
    public final static String ACTION_CONNECTED           = "ACTION_CONNECTED";
    public final static String ACTION_DISCONNECTED        = "ACTION_DISCONNECTED";
    public final static String ACTION_SERVICES_DISCOVERED = "ACTION_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_RECEIVED       = "ACTION_DATA_RECEIVED";

    public Queue<CharacteristicQueueObject> characteristicQueue = new LinkedList<>();
    public byte[] user1Data;
    public byte[] user2Data;
    public byte[] user3Data;
    public boolean user1DataUpdateFlag = false;
    public boolean user2DataUpdateFlag = false;
    public boolean user3DataUpdateFlag = false;

    public BluetoothLowEnergyService() {
    }

    class LocalBinder extends Binder {
        BluetoothLowEnergyService getService() {
            return BluetoothLowEnergyService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // The BLE close method is called when we unbind the service to free up the resources.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     */
    public void initialize() {

        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
        }
    }

    /**
     * Scans for BLE devices that support the service we are looking for
     */
    public void scan() {
        /* Scan for devices and look for the one with the service that we want */
        UUID dispenserService = UUID.fromString(dispenserServiceUUID);

        ScanSettings settings;
        List<ScanFilter> filters;
        mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
        settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        filters = new ArrayList<>();
        // We will scan just for the CAR's UUID
        ParcelUuid PUuid = new ParcelUuid(dispenserService);
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(PUuid).build();
        filters.add(filter);
        Log.d("scan", "scanned");
        mLEScanner.startScan(filters, settings, mScanCallback);
        // Handler of scanning fail
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mLeDevice == null) {
                    mLEScanner.stopScan(mScanCallback);
                    broadcastUpdate(ACTION_BLESCAN_FAIL);
                }
            }
        }, 10000);
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void connect() {
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            mBluetoothGatt.connect();
        }

        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = mLeDevice.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
    }

    /**
     * Runs service discovery on the connected device.
     */
    public void discoverServices() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.discoverServices();
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

    /**
     * This method is used to read the state of a characteristic
     *
     * @param characteristic target BLE GATT Characteristic
     */
    public void readLedCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        CharacteristicQueueObject queueElement = new CharacteristicQueueObject(characteristic, "r", new byte[0]);
        characteristicQueue.add(queueElement);
        if (characteristicQueue.size() == 1) {
            mBluetoothGatt.readCharacteristic(characteristic);
        }
    }

    /**
     * This method is write value to a characteristic
     *
     * @param characteristic target BLE GATT Characteristic
     * @param byteVal value to write
     */
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] byteVal) {
        String UUID = characteristic.getUuid().toString();
        Log.i(TAG, "Write " + UUID + " " + Arrays.toString(byteVal));
        CharacteristicQueueObject queueElement = new CharacteristicQueueObject(characteristic, "w", byteVal);
        characteristicQueue.add(queueElement);
        if (characteristicQueue.size() == 1) {
            characteristic.setValue(byteVal);
            mBluetoothGatt.writeCharacteristic(characteristic);
        }
    }

    /**
     * Implements the callback for when scanning for devices has found a device with
     * the service we are looking for.
     *
     * This is the callback for BLE scanning for LOLLIPOP and later
     */
    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            mLeDevice = result.getDevice();
            mLEScanner.stopScan(mScanCallback); // Stop scanning after the first device is found
            Log.d("scan callback", "found device " + mLeDevice.toString());
            broadcastUpdate(ACTION_BLESCAN_CALLBACK); // Tell the main activity that a device has been found
        }
    };

    /**
     * Implements callback methods for GATT events that the app cares about.  For example,
     * connection change and services discovered.
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                broadcastUpdate(ACTION_CONNECTED);
                Log.i(TAG, "Connected to GATT server.");
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(ACTION_DISCONNECTED);
            }
        }

        /**
         * This is called when a service discovery has completed.
         *
         * It gets the characteristics we are interested in and then
         * broadcasts an update to the main activity.
         *
         * @param gatt The GATT database object
         * @param status Status of whether the write was successful.
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            // Get just the service that we are looking for
            BluetoothGattService mService = gatt.getService(UUID.fromString(dispenserServiceUUID));
            /* Get characteristics from our desired service */
            user1AmountCharacteristic = mService.getCharacteristic(UUID.fromString(user1AmountCharacteristicUUID));
            user1DataCharacteristic   = mService.getCharacteristic(UUID.fromString(user1DataCharacteristicUUID));
            user2AmountCharacteristic = mService.getCharacteristic(UUID.fromString(user2AmountCharacteristicUUID));
            user2DataCharacteristic   = mService.getCharacteristic(UUID.fromString(user2DataCharacteristicUUID));
            user3AmountCharacteristic = mService.getCharacteristic(UUID.fromString(user3AmountCharacteristicUUID));
            user3DataCharacteristic   = mService.getCharacteristic(UUID.fromString(user3DataCharacteristicUUID));
            refillToothpasteCharacteristic = mService.getCharacteristic(UUID.fromString(refillToothpasteCharacteristicUUID));

            // Broadcast that service/characteristic/descriptor discovery is done
            broadcastUpdate(ACTION_SERVICES_DISCOVERED);
        }

        /**
         * This is called when a read completes
         *
         * @param gatt the GATT database object
         * @param characteristic the GATT characteristic that was read
         * @param status the status of the transaction
         */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String UUID = characteristic.getUuid().toString();
                if (UUID.equalsIgnoreCase(user1DataCharacteristicUUID)) {
                    user1Data = characteristic.getValue();
                    user1DataUpdateFlag = true;
                } else if (UUID.equalsIgnoreCase(user2DataCharacteristicUUID)) {
                    user2Data = characteristic.getValue();
                    user2DataUpdateFlag = true;
                } else if (UUID.equalsIgnoreCase(user3DataCharacteristicUUID)) {
                    user3Data = characteristic.getValue();
                    user3DataUpdateFlag = true;
                }
                // Notify the main activity that new data is available
                broadcastUpdate(ACTION_DATA_RECEIVED);
            }

            runNextQueueAction();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            String UUID = characteristic.getUuid().toString();
            Log.d("onCharWrite", "Write Callback " + UUID);
            runNextQueueAction();
        }
    }; // End of GATT event callback methods

    /**
     * Sends a broadcast to the listener in the main activity.
     *
     * @param action The type of action that occurred.
     */
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
        Log.d("Broadcast Update", action);
    }

    public void setAlertDialog(Context context, String alertMessage) {
        AlertDialog alertDialog = new AlertDialog.Builder(context).create();
        alertDialog.setTitle("Message");
        alertDialog.setMessage(alertMessage);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        disconnect();
                    }
                });
        alertDialog.show();
    }

    public void runNextQueueAction () {
        characteristicQueue.remove();
        if (characteristicQueue.size() > 0) {
            CharacteristicQueueObject queueElement = characteristicQueue.element();
            BluetoothGattCharacteristic nextCharacteristic = queueElement.getCharacteristic();
            switch (queueElement.getAction()) {
                case "r":
                    mBluetoothGatt.readCharacteristic(nextCharacteristic);
                    break;
                case "w":
                    nextCharacteristic.setValue(queueElement.getByteVal());
                    mBluetoothGatt.writeCharacteristic(nextCharacteristic);
                    break;
            }
        }
    }
}
