package com.example.flirapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import java.io.File;

public class EndPage extends AppCompatActivity {
    private String fileName;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_end_page);
//        CameraHandler cameraHandler = CameraDetected.cameraHandler;
//        cameraHandler.disconnect();

        androidx.appcompat.app.ActionBar actionBar = getSupportActionBar();
        if(actionBar != null){
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        Intent intent = getIntent();
        fileName = intent.getStringExtra("filename");

        ImageView img = findViewById(R.id.imageView13);
        Bitmap bitmap = BitmapFactory.decodeFile(fileName + ".jpg");
        img.setImageBitmap(bitmap);
    }

    public void goToGallery(View view) {
        Intent intent1 = new Intent(EndPage.this, Gallery.class);
        startActivity(intent1);
        finish();
//        int IMAGE_REQUEST_CODE = 0x102;
//        String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/S3/";
//
//        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
//        Uri uri = Uri.parse(dirPath);
//        intent.setDataAndType(uri, "*/*");
//        startActivity(Intent.createChooser(intent, "Open folder"));
    }

//    public void returnToLast(View view){
//        finish();
//    }

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