package com.example.dispenserhelper;

import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        InitializeSettingFile("userSettings.txt");
        InitializeDataFile("historyData.txt");
    }

    public void InitializeSettingFile(String fileName){
        File file = new File(getApplicationContext().getFilesDir(), fileName);
        if (!file.exists()){
            try {
                if (file.createNewFile()) {
                    FileOutputStream outputStream = openFileOutput(fileName, Context.MODE_PRIVATE);
                    String emptyLine = ",\n";
                    outputStream.write(emptyLine.getBytes());
                    outputStream.write(emptyLine.getBytes());
                    outputStream.write(emptyLine.getBytes());
                    outputStream.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void InitializeDataFile(String fileName) {
        File file = new File(getApplicationContext().getFilesDir(), fileName);
        if (!file.exists()){
            try {
                if (file.createNewFile()) {
                    FileOutputStream outputStream = openFileOutput(fileName, Context.MODE_PRIVATE);
                    outputStream.write("0\n".getBytes());
                    outputStream.write("0\n".getBytes());
                    outputStream.write("0\n".getBytes());
                    outputStream.write("0\n".getBytes());
                    outputStream.write("0\n".getBytes());
                    outputStream.write("0\n".getBytes());
                    outputStream.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void ManageUser(View view) {
        Intent intent = new Intent(this, ManageUserActivity.class);
        startActivity(intent);
    }

    public void RefillToothpaste(View view) {
        Intent intent = new Intent(this, RefillToothpasteActivity.class);
        startActivity(intent);
    }

    public void HistoricalData(View view) {
        Intent intent = new Intent(this, HistoricalDataActivity.class);
        startActivity(intent);
    }

    public void UserGuide(View view) {
        Intent intent = new Intent(this, UserGuideActivity.class);
        startActivity(intent);
    }
}
