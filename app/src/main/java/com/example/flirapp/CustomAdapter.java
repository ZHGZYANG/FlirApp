package com.example.flirapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;

public class CustomAdapter extends BaseAdapter {
    Context context;
    File[] files;
    LayoutInflater inflter;
    public CustomAdapter(Context applicationContext, File[] files) {
        this.context = applicationContext;
        this.files = files;
        inflter = (LayoutInflater.from(applicationContext));
    }
    @Override
    public int getCount() {
        return files.length;
    }
    @Override
    public Object getItem(int i) {
        return null;
    }
    @Override
    public long getItemId(int i) {
        return 0;
    }
    @SuppressLint({"ViewHolder", "InflateParams"})
    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        view = inflter.inflate(R.layout.activity_gridview, null);
        ImageView icon = (ImageView) view.findViewById(R.id.icon);
        Bitmap bitmap = BitmapFactory.decodeFile(files[i].getAbsolutePath());
        icon.setImageBitmap(bitmap);
        TextView textView=view.findViewById(R.id.textViewUndergridimg);
        textView.setText(files[i].getName().split("\\.")[0]);
        return view;
    }
}