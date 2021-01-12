package com.example.flirapp;
// CameraDetected is main activity

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.icu.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.icu.util.GregorianCalendar;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.flir.thermalsdk.ErrorCode;
import com.flir.thermalsdk.androidsdk.live.connectivity.UsbPermissionHandler;
import com.flir.thermalsdk.image.Rectangle;
import com.flir.thermalsdk.image.ThermalImage;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;

public class RecordProcess extends AppCompatActivity {
    private static final String TAG = "RecordProcess";
    private static int count=0;
    //Handles Android permission for eg Network
    private PermissionHandler permissionHandler;

    //Handles network camera operations
    private CameraHandler cameraHandler;

    private Identity connectedIdentity = null;
    private Chronometer itimer;
    //    private Button videoRecord;
    private ImageButton captureButton;
    private ImageView msxImage;
    private FloatingActionButton fab;
    //        private ImageView photoImage;
//    private FrameDataHolder currentDataHolder; //for capture
//    private Bitmap currentMsxBitmap;//for capture
//        private Bitmap currentDcBitmap;//for capture
    private LinkedBlockingQueue<Bitmap> currentFramesBuffer = new LinkedBlockingQueue<>(100); //for video
    private boolean videoRecordFinished;
    private boolean isVideoRecord = false;
    private boolean photoCapture;

    private LinkedBlockingQueue<FrameDataHolder> framesBuffer = new LinkedBlockingQueue<>(21);
    private UsbPermissionHandler usbPermissionHandler = new UsbPermissionHandler();
    protected static ThermalImage currentThermalImage;

    private VideoHandler videoHandlerIns;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_process);

//        ThermalLog.LogLevel enableLoggingInDebug = BuildConfig.DEBUG ? ThermalLog.LogLevel.DEBUG : ThermalLog.LogLevel.NONE;

        //ThermalSdkAndroid has to be initiated from a Activity with the Application Context to prevent leaking Context,
        // and before ANY using any ThermalSdkAndroid functions
        //ThermalLog will show log from the Thermal SDK in standards android log framework
//        ThermalSdkAndroid.init(getApplicationContext(), enableLoggingInDebug);

        permissionHandler = new PermissionHandler(showMessage, RecordProcess.this);
        cameraHandler = CameraDetected.cameraHandler;
        setupViews();

        permissionHandler.checkForStoragePermission();

        new Thread(new Runnable() {
            @Override
            public void run() {
                cameraHandler.startStream(streamDataListener);
            }
        }).start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                video2(view);
            }
        });
        fab.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                capture(view);
                return true;
            }
        });

    }
//    public void startDiscovery(View view) {
//        startDiscovery();
//    }
//
//    public void stopDiscovery(View view) {
//        stopDiscovery();
//    }


//    public void connect(View view) {
////        connect(cameraHandler.getFlirOne());
//        connect(cameraHandler.getFlirOneEmulator());
//
//    }


//    public void disconnect(View view) {
//        disconnect();
//    }

    /**
     * Handle Android permission request response for Bluetooth permissions
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult() called with: requestCode = [" + requestCode + "], permissions = [" + permissions + "], grantResults = [" + grantResults + "]");
        permissionHandler.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * Connect to a Camera
     */
    private void connect(Identity identity) {
        if (connectedIdentity != null) {
            Log.d(TAG, "connect(), in *this* code sample we only support one camera connection at the time");
            showMessage.show("connect(), in *this* code sample we only support one camera connection at the time");
            return;
        }

        if (identity == null) {
            Log.d(TAG, "connect(), can't connect, no camera available");
            showMessage.show("connect(), can't connect, no camera available");
            return;
        }

        connectedIdentity = identity;

        //IF your using "USB_DEVICE_ATTACHED" and "usb-device vendor-id" in the Android Manifest
        // you don't need to request permission, see documentation for more information
        if (UsbPermissionHandler.isFlirOne(identity)) {
            usbPermissionHandler.requestFlirOnePermisson(identity, this, permissionListener);
        }
    }

    private UsbPermissionHandler.UsbPermissionListener permissionListener = new UsbPermissionHandler.UsbPermissionListener() {
        @Override
        public void permissionGranted(Identity identity) {
            doConnect(identity);
        }

        @Override
        public void permissionDenied(Identity identity) {
            showMessage.show("Permission was denied for identity ");
        }

        @Override
        public void error(UsbPermissionHandler.UsbPermissionListener.ErrorType errorType, final Identity identity) {
            showMessage.show("Error when asking for permission for FLIR ONE, error:" + errorType + " identity:" + identity);
        }
    };

    private void doConnect(Identity identity) {
        new Thread(() -> {
            try {
                cameraHandler.connect(identity, connectionStatusListener);
                runOnUiThread(() -> {
                    cameraHandler.startStream(streamDataListener);
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Could not connect: " + e);
//                    updateConnectionText(identity, "DISCONNECTED");
                });
            }
        }).start();
    }

    /**
     * Disconnect to a camera
     */
    private void disconnect() {
//        updateConnectionText(connectedIdentity, "DISCONNECTING");
        connectedIdentity = null;
        Log.d(TAG, "disconnect() called with: connectedIdentity = [" + connectedIdentity + "]");
        new Thread(() -> {
            cameraHandler.disconnect();
//            runOnUiThread(() -> {
//                updateConnectionText(null, "DISCONNECTED");
//            });
        }).start();
    }

    /**
     * Update the UI text for connection status
     */
//    private void updateConnectionText(Identity identity, String status) {
//        String deviceId = identity != null ? identity.deviceId : "";
//        connectionStatus.setText(getString(R.string.connection_status_text, deviceId + " " + status));
//    }

    /**
     * Start camera discovery
     */
//    private void startDiscovery() {
//        cameraHandler.startDiscovery(cameraDiscoveryListener, discoveryStatusListener);
//    }

    /**
     * Stop camera discovery
     */
//    private void stopDiscovery() {
//        cameraHandler.stopDiscovery(discoveryStatusListener);
//    }

    /**
     * Callback for discovery status, using it to update UI
     */
//    private CameraHandler.DiscoveryStatus discoveryStatusListener = new CameraHandler.DiscoveryStatus() {
//        @Override
//        public void started() {
//            discoveryStatus.setText(getString(R.string.connection_status_text, "discovering"));
//
//        }
//
//        @Override
//        public void stopped() {
//            discoveryStatus.setText(getString(R.string.connection_status_text, "not discovering"));
////            Intent intent = new Intent();
////            intent.setClass(MainActivity.this, failure.class);
////            startActivity(intent);
//        }
//    };

    /**
     * Camera connecting state thermalImageStreamListener, keeps track of if the camera is connected or not
     * <p>
     * Note that callbacks are received on a non-ui thread so have to eg use {@link #runOnUiThread(Runnable)} to interact view UI components
     */
    private ConnectionStatusListener connectionStatusListener = new ConnectionStatusListener() {
        @Override
        public void onDisconnected(@org.jetbrains.annotations.Nullable ErrorCode errorCode) {
            Log.d(TAG, "onDisconnected errorCode:" + errorCode);

//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
////                    updateConnectionText(connectedIdentity, "DISCONNECTED");
//                }
//            });
        }
    };

    private final CameraHandler.StreamDataListener streamDataListener = new CameraHandler.StreamDataListener() {

        @Override
        public void images(FrameDataHolder dataHolder) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    msxImage.setImageBitmap(dataHolder.msxBitmap);
//                    photoImage.setImageBitmap(dataHolder.dcBitmap);
                }
            });
        }

        @Override
        public void images(Bitmap msxBitmap, Bitmap dcBitmap, ThermalImage thermalImage) throws IOException {
//            currentThermalImage = thermalImage;

            try {
                framesBuffer.put(new FrameDataHolder(msxBitmap, dcBitmap));
            } catch (InterruptedException e) {
                //if interrupted while waiting for adding a new item in the queue
                Log.e(TAG, "images(), unable to add incoming images to frames buffer, exception:" + e);
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "framebuffer size:" + framesBuffer.size());
                    FrameDataHolder poll = framesBuffer.poll();
                    msxImage.setImageBitmap(poll.msxBitmap);
//                    photoImage.setImageBitmap(poll.dcBitmap);
                }
            });

            if (photoCapture) { // photo capture
                photoCapture = false;
                String state = Environment.getExternalStorageState();
                if (state.equals(Environment.MEDIA_MOUNTED)) {

                    String imagePath = getImageName("photo");

//                        thermalImage.setTemperatureUnit(TemperatureUnit.SIGNAL);
//                        thermalImage.saveAs(imagePath);
                    ///////END - MODEL 1

                    ///////START - MODEL 2 -- SAVE TO PNG WITH 16 BITS
//                        ImageInfo imi = new ImageInfo(480, 640, 16, false,true,false);
//                        File img=new File(imagePath);
//                        PngWriter png = new PngWriter(img, imi);
//                        ImageLineInt iline = new ImageLineInt(imi);


                    /////// START - MODEL 3 -- SAVE TO TXT
//                        FileWriter file = new FileWriter(imagePath);
//                        for (int i = 0; i < 640; i++) {
//                            double[] temperature = thermalImage.getValues(new Rectangle(0, i, 480, 1));
                    double[] temperature = thermalImage.getValues(new Rectangle(0, 0, 480, 640));
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {

//                                FileWriter file = new FileWriter(imagePath);
                                FileOutputStream file=new FileOutputStream(new File(imagePath));
//                            file.write((Arrays.toString(temperature) + "\n"));
                                file.write((handleDataForCSV(temperature) ).getBytes());
//                                file.flush();

                                ///////// MODEL 3
//                            int[] tempera={1,5};
//                            ImageLineHelper.setPixelsRGBA8(iline,tempera);
//                            png.writeRow(iline);
//                        }
                                file.close();
//                        png.end();
                                runOnUiThread(() -> {
                                    showMessage.show("Photo Saved.");
                                });
                            } catch (Exception e) {
                                runOnUiThread(() -> {
                                    showMessage.show("Save photo failed! " + e);
                                });
                            }

                        }
                    }).start();

                } else {
                    runOnUiThread(() -> {
                        showMessage.show("Save photo failed! Media is not mounted.");
                    });
                }
            }

            if (!videoRecordFinished && isVideoRecord) { //video record
                count++;

                String state = Environment.getExternalStorageState();
                if (state.equals(Environment.MEDIA_MOUNTED)) {
                    double[] temperature = thermalImage.getValues(new Rectangle(0, 0, 480, 640));
//                    String videoPath = getImageName("video");

//                    thermalImage.saveAs(videoPath);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                String videoPath = getImageName("video");
//                        thermalImage.setTemperatureUnit(TemperatureUnit.SIGNAL);
//                        thermalImage.saveAs(videoPath);
//                                FileWriter file = new FileWriter(videoPath);
                                FileOutputStream file=new FileOutputStream(new File(videoPath));

//                        for (int i = 0; i < 640; i++) {
//                            double[] temperature = thermalImage.getValues(new Rectangle(0, i, 480, 1));
                                file.write((handleDataForCSV(temperature) ).getBytes());
//                                    file.write(handleDataForCSV(temperature) + "\n");
                                file.flush();
//                        }
                                file.close();
                            } catch (Exception e) {
                                Log.d("error", "Save video failed! " + e);
                                runOnUiThread(() -> {
                                    showMessage.show("Save video failed! " + e);
                                });

                            }
                        }
                    }).start();
                } else {
                    Log.d("error", "Save video failed! Media is not mounted.");
                    runOnUiThread(() -> {
                        showMessage.show("Save video failed! Media is not mounted.");
                    });

                }

            }
        }
    };

    private String handleDataForCSV(double[] temperature) {
//        for (int n = 0; n < temperature.length; n++) {
//            BigDecimal b = new BigDecimal(temperature[n]);
//            temperature[n] = b.setScale(3, BigDecimal.ROUND_HALF_UP).doubleValue();
//        }
        return Arrays.toString(temperature).replaceAll(" ", "").replace("[", "").replace("]", "");
    }

    /**
     * Camera Discovery thermalImageStreamListener, is notified if a new camera was found during a active discovery phase
     * <p>
     * Note that callbacks are received on a non-ui thread so have to eg use {@link #runOnUiThread(Runnable)} to interact view UI components
     */
//    private DiscoveryEventListener cameraDiscoveryListener = new DiscoveryEventListener() {
//        @Override
//        public void onCameraFound(Identity identity) {
//            Log.d(TAG, "onCameraFound identity:" + identity);
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    cameraHandler.add(identity);
//                }
//            });
//        }
//
//        @Override
//        public void onDiscoveryError(CommunicationInterface communicationInterface, ErrorCode errorCode) {
//            Log.d(TAG, "onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);
//
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    stopDiscovery();
//                    RecordProcess.this.showMessage.show("onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);
//                }
//            });
//        }
//    };

    private ShowMessage showMessage = new ShowMessage() {
        @Override
        public void show(String message) {
            Toast.makeText(RecordProcess.this, message, Toast.LENGTH_SHORT).show();
        }
    };


    private void setupViews() {
        itimer = findViewById(R.id.itimer);
//        captureButton = findViewById(R.id.imageButton);
        msxImage = findViewById(R.id.msx_image);
//        photoImage = findViewById(R.id.photo_image);
//        videoRecord = findViewById(R.id.videoRecord);
        fab = findViewById(R.id.floatingActionButton4);
    }

    protected String getImageName(String model) {
        Calendar now = new GregorianCalendar();
        SimpleDateFormat simpleDate = new SimpleDateFormat("yyyyMMddHHmmssSS", Locale.getDefault());
        String fileName = simpleDate.format(now.getTime());

        String dirPath;
        if (model.equals("photo")) {
            dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/flirapp/image/";
        } else {
            dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/flirapp/image/temp/";
        }
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dirPath + fileName + ".csv";
    }

    public void capture(View view) {
        photoCapture = true;
    }


//    public void capture(View view) {
//        new Thread(new Runnable() {
//            @SuppressLint("SetTextI18n")
//            @Override
//            public void run() {
//                String state = Environment.getExternalStorageState();
//                if (state.equals(Environment.MEDIA_MOUNTED)) {
//                    String imagePath = getImageName("photo");
//                    try {
//                        currentThermalImage.saveAs(imagePath);
//                        runOnUiThread(() -> {
//                            showMessage.show("Saved");
//                        });
//                    } catch (Exception e) {
//                        runOnUiThread(() -> {
//                            showMessage.show("Save failed! " + e);
//                        });
//                    }
//                } else {
//                    runOnUiThread(() -> {
//                        showMessage.show("Save failed! Media is not mounted.");
//                    });
//                }
//            }
//        }).start();
//
//    }

//    private void captureForVideo() {
//        new Thread(new Runnable() {
//            @SuppressLint("SetTextI18n")
//            @Override
//            public void run() {
//                String state = Environment.getExternalStorageState();
//                if (state.equals(Environment.MEDIA_MOUNTED)) {
//                    String videoPath = getImageName("video");
//                    try {
////                        File msxfile = new File(dirPath + fileName + ".png");
////                        File dcfile=new File(dirPath + fileName + ".tiff");
////                        FileOutputStream msxout = new FileOutputStream(msxfile);
////                        FileOutputStream dcout = new FileOutputStream(dcfile);
////                        currentMsxBitmap.compress(Bitmap.CompressFormat.PNG, 100, msxout);
////                        currentDcBitmap.compress(Bitmap.CompressFormat.JPEG, 100, dcout);
////                        msxout.flush();
////                        msxout.close();
////                        dcout.flush();
////                        dcout.close();
//
////                        TiffSaver.SaveOptions options = new TiffSaver.SaveOptions();
////                        boolean saved = TiffSaver.saveBitmap(dirPath + fileName + ".tif", currentMsxBitmap, options);
//
//                    } catch (Exception e) {
//                        Log.d("error", "Save failed! " + e);
//                    }
//                } else {
//                    Log.d("error", "Save failed! Media is not mounted.");
//                }
//            }
//        }).start();
//
//    }


    @SuppressLint("SetTextI18n")
    public void video2(View view) {
//        if (videoRecord.getText().equals("VIDEO RECORD")) { // record
        if (!isVideoRecord) {
//            videoRecord.setText("STOP");
            isVideoRecord = true;
            videoRecordFinished = false;
            timerStart();
//        } else if (videoRecord.getText().equals("STOP")) {
        } else {
//            videoRecord.setText("VIDEO RECORD");
            timerStop();
            videoRecordFinished = true;
            isVideoRecord = false;
            runOnUiThread(() -> {
                showMessage.show("Video saving...");
            });

//            videoHandler2();
            // following: folder model
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        runOnUiThread(() -> {
                            showMessage.show("Video Save failed. Error code: 99050");
                        });
                    }
                    Calendar now = new GregorianCalendar();
                    SimpleDateFormat simpleDate = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
                    String fileName = simpleDate.format(now.getTime());
                    String oldDirName = Environment.getExternalStorageDirectory().getAbsolutePath() + "/flirapp/image/temp/";
                    String newDirName = Environment.getExternalStorageDirectory().getAbsolutePath() + "/flirapp/image/" + fileName + "/";
                    File oldDir = new File(oldDirName);
                    File newDir = new File(newDirName);
                    boolean success = oldDir.renameTo(newDir);
                    if (success) {
//                        try {
//                            FileWriter countfile=new FileWriter(getImageName("photo")+"count");
//                            countfile.write(String.valueOf(count));
//                            countfile.close();
//                            count=0;
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }

                        runOnUiThread(() -> {
                            showMessage.show("Video Saved. "+count);
                            count=0;
                        });
                    } else {
                        runOnUiThread(() -> {
                            showMessage.show("Save video faild! Error code: 90074"); // 90074: cannot rename folder
                        });
                    }
                }
            }).start();

        }

    }

//    @SuppressLint("SetTextI18n")
//    public void video(View view) {
//        if (videoRecord.getText().equals("VIDEO RECORD")) { // record
//            videoRecord.setText("STOP");
//            new Thread(new Runnable() {
//                @Override
//                public void run() {
//                    String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/flirapp/image/";
//                    File dir = new File(dirPath);
//                    if (!dir.exists()) {
//                        dir.mkdirs();
//                    }
//                    String state = Environment.getExternalStorageState();
//                    if (state.equals(Environment.MEDIA_MOUNTED)) {
//                        Calendar now = new GregorianCalendar();
//                        SimpleDateFormat simpleDate = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
//                        String fileName = simpleDate.format(now.getTime());
//                        videoRecordFinished = false;
//                        timerStart();
//                        try {
//                            int result = videoHandler(dirPath + fileName + ".mp4");
//                            runOnUiThread(() -> {
//                                showMessage.show("Saved.");
//                            });
//                        } catch (InterruptedException e) {
//                            runOnUiThread(() -> {
//                                showMessage.show("Save failed. " + e);
//                            });
//                        }
//                    } else {
//                        runOnUiThread(() -> {
//                            showMessage.show("Save failed! Media is not mounted.");
//                        });
//                    }
//                }
//            }).start();
//        } else if (videoRecord.getText().equals("STOP")){
//            videoRecord.setText("VIDEO RECORD");
//            timerStop();
//            videoRecordFinished = true;
//            videoHandlerIns.finished();
//
//        }
//    }

    private void videoHandler2() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/flirapp/image/";
                String state = Environment.getExternalStorageState();
                if (state.equals(Environment.MEDIA_MOUNTED)) {
                    Calendar now = new GregorianCalendar();
                    SimpleDateFormat simpleDate = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
                    String fileName = simpleDate.format(now.getTime());
                    ////
                    videoHandlerIns = new VideoHandler(dirPath + fileName + ".mp4");
                    try {
                        videoHandlerIns.pass(dirPath + "temp/");
                    } catch (IOException e) {
                        runOnUiThread(() -> {
                            showMessage.show("Save failed! " + e);
                        });
                    }
                    ////
                    runOnUiThread(() -> {
                        showMessage.show("Saved.");
                    });
                } else {
                    runOnUiThread(() -> {
                        showMessage.show("Save failed! Media is not mounted.");
                    });
                }
            }
        }).start();
    }

    // do not be used
//    private int videoHandler(String filePathName) throws InterruptedException {
//        videoHandlerIns = new VideoHandler(filePathName);
//        int count = 0,inited=0;
//        Bitmap tmp=null;
//        while (!videoRecordFinished) {
//            if (!currentMsxBitmap.isRecycled() && !currentMsxBitmap.equals(tmp)) {
//                tmp = currentMsxBitmap.copy(currentMsxBitmap.getConfig(), true);
//                videoHandlerIns.pass(tmp);
//                count++;
//                if (count > 180 && inited==0) {
//                    inited=1;
//                    videoHandlerIns.init();
//                }
//            }
//        }
////        runOnUiThread(() -> {
////            showMessage.show("Preparing file...");
////        });
//        return 1;
//    }

    public void timerStop() {
        itimer.stop();
    }

    public void timerStart() {
        itimer.setBase(SystemClock.elapsedRealtime());
        itimer.start();
    }

}

