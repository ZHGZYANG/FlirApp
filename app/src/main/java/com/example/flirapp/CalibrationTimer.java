package com.example.flirapp;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;

public class CalibrationTimer extends AppCompatActivity {
    private Chronometer warmtimer;
    private Button button995;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration_timer);
        warmtimer = findViewById(R.id.warmtimer);
        button995=findViewById(R.id.button995);
    }

    @Override
    protected void onStart() {

        super.onStart();
        warmtimer.setCountDown(true);
        warmtimer.setBase(SystemClock.elapsedRealtime() + 300000);
//        warmtimer.setBase(SystemClock.elapsedRealtime() + 10000);

        warmtimer.start();
        warmtimer.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
            @Override
            public void onChronometerTick(Chronometer chronometer) {
                if (SystemClock.elapsedRealtime()-warmtimer.getBase()>= 0) {
                    warmtimer.stop();
                    Intent intent1 = new Intent(CalibrationTimer.this, RecordProcess.class);
                    startActivity(intent1);
                    finish();
                }
            }
        });
    }

    public void go(View view){
        warmtimer.stop();
        Intent intent1 = new Intent(CalibrationTimer.this, RecordProcess.class);
        startActivity(intent1);
        finish();
    }
}