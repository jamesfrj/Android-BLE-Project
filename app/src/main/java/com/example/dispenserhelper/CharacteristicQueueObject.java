package com.example.dispenserhelper;

import android.bluetooth.BluetoothGattCharacteristic;

class CharacteristicQueueObject {
    private final BluetoothGattCharacteristic characteristic;
    private final String action;
    private final byte[] byteVal;

    CharacteristicQueueObject(BluetoothGattCharacteristic characteristic,
                                     String action,
                                     byte[] byteVal) {
        this.characteristic = characteristic;
        this.action = action;
        this.byteVal = byteVal;
    }

    BluetoothGattCharacteristic getCharacteristic() {
        return characteristic;
    }

    String getAction() {
        return action;
    }

    byte[] getByteVal() {
        return byteVal;
    }
}
