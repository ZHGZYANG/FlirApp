package com.example.flirapp;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;

public class Gallery extends AppCompatActivity {
    GridView grid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        androidx.appcompat.app.ActionBar actionBar = getSupportActionBar();
        if(actionBar != null){
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        CameraDetected.activityList.add(this);

        grid = (GridView) findViewById(R.id.grid);
        setTitle("S3 Gallery");
    }

    @Override
    protected void onResume() {
        super.onResume();
        File[] listFile;
        String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/S3/";
        File file = new File(dirPath);
        if (file.isDirectory()) {
            FileFilter filter = new FileFilter() {
                public boolean accept(File f) {
                    return f.getName().endsWith("jpg");
                }
            };
            listFile = file.listFiles(filter);
            CustomAdapter customAdapter = new CustomAdapter(getApplicationContext(), listFile);
            grid.setAdapter(customAdapter);
            grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    Intent intent = new Intent(Gallery.this, GalleryDetail.class);
                    intent.putExtra("imagePath", listFile[position].getAbsolutePath());
                    startActivity(intent);
                }
            });
        }
    }

//    public void returnToLast(View view) {
//        finish();
//    }

    public void exit(View view) {
        for (Activity act : CameraDetected.activityList) {
            act.finish();
        }

        System.exit(0);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                this.finish(); // back button
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}