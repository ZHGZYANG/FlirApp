package com.example.flirapp;
// CameraDetected is main activity

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
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
import android.widget.TextView;
import android.widget.Toast;

import com.flir.thermalsdk.ErrorCode;
import com.flir.thermalsdk.androidsdk.live.connectivity.UsbPermissionHandler;
import com.flir.thermalsdk.image.Rectangle;
import com.flir.thermalsdk.image.TemperatureUnit;
import com.flir.thermalsdk.image.ThermalImage;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

public class RecordProcess extends AppCompatActivity {
    private static final String TAG = "RecordProcess";
    private static int count = 0;
    //Handles Android permission for eg Network
    private PermissionHandler permissionHandler;

    //Handles network camera operations
    private CameraHandler cameraHandler;

    private Identity connectedIdentity = null;
    private Chronometer itimer;
    private ImageView msxImage;
    private FloatingActionButton fab;
    private TextView battery;
    //    private FrameDataHolder currentDataHolder;
    private LinkedBlockingQueue<Bitmap> currentFramesBuffer = new LinkedBlockingQueue<>(100); //for video
    private boolean videoRecordFinished;
    private boolean isVideoRecord = false;
    private boolean photoCapture;

    private LinkedBlockingQueue<FrameDataHolder> framesBuffer = new LinkedBlockingQueue<>(21);
    private UsbPermissionHandler usbPermissionHandler = new UsbPermissionHandler();

//    private VideoHandler videoHandlerIns;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_process);

        permissionHandler = new PermissionHandler(showMessage, RecordProcess.this);
        cameraHandler = CameraDetected.cameraHandler;
        setupViews();

        permissionHandler.checkForStoragePermission();

    }

    Thread batteryper = new Thread(new Runnable() {
        @SuppressLint("SetTextI18n")
        @Override
        public void run() {
            while (true) {
                Integer batteryResult = cameraHandler.batteryPercent();
                battery.setText(batteryResult.toString() + "%");
                if (batteryResult < 25) {
                    battery.setTextColor(Color.RED);
                    runOnUiThread(() -> {
                        showMessage.show("Low battery! Please charge the camera!");
                    });
                }
                try {
                    Thread.sleep(300000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    });


    @Override
    public void onPause() {
        super.onPause();
        if (cameraHandler.getCamera() != null)
            cameraHandler.stop();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (cameraHandler.getCamera() != null) {
            runOnUiThread(() -> {
                cameraHandler.startStream(streamDataListener);
            });
            batteryper.start();

        } else {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    runOnUiThread(() -> {
                        showMessage.show("We are reconnecting camera...");
                    });
                    startDiscovery();
                    Timer timer = new Timer();
                    TimerTask timerTask = new TimerTask() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void run() {
//                        connect(cameraHandler.getFlirOne());
                            connect(cameraHandler.getFlirOneEmulator());
                        }
                    };
                    timer.schedule(timerTask, 1000 * 8);

                }
            }).start();
        }

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

    @Override
    public void onDestroy() {
        stopDiscovery();
        if (cameraHandler.getCamera() != null) {
            disconnect();
        }
        super.onDestroy();

    }


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
                batteryper.start();
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
        Log.d(TAG, "disconnect() called with: connectedIdentity = [" + connectedIdentity + "]");
        connectedIdentity = null;
        new Thread(() -> {
            cameraHandler.disconnect();
        }).start();
    }

    /**
     * Start camera discovery
     */
    private void startDiscovery() {
        cameraHandler.startDiscovery(cameraDiscoveryListener, discoveryStatusListener);
    }

    /**
     * Stop camera discovery
     */
    private void stopDiscovery() {
        cameraHandler.stopDiscovery(discoveryStatusListener);
    }

    /**
     * Callback for discovery status, using it to update UI
     */
    private CameraHandler.DiscoveryStatus discoveryStatusListener = new CameraHandler.DiscoveryStatus() {
        @Override
        public void started() {
        }

        @Override
        public void stopped() {
        }
    };

    /**
     * Camera connecting state thermalImageStreamListener, keeps track of if the camera is connected or not
     * <p>
     * Note that callbacks are received on a non-ui thread so have to eg use {@link #runOnUiThread(Runnable)} to interact view UI components
     */
    private ConnectionStatusListener connectionStatusListener = new ConnectionStatusListener() {
        @Override
        public void onDisconnected(@org.jetbrains.annotations.Nullable ErrorCode errorCode) {
            Log.d(TAG, "onDisconnected errorCode:" + errorCode);
        }
    };

    private final CameraHandler.StreamDataListener streamDataListener = new CameraHandler.StreamDataListener() {

        @Override
        public void images(FrameDataHolder dataHolder) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    msxImage.setImageBitmap(dataHolder.msxBitmap);
                }
            });
        }

        @Override
        public void images(Bitmap msxBitmap, Bitmap dcBitmap, ThermalImage thermalImage) throws IOException {
            thermalImage.setTemperatureUnit(TemperatureUnit.CELSIUS);

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
                }
            });

            if (photoCapture) { // photo capture
                photoCapture = false;
                String state = Environment.getExternalStorageState();
                if (state.equals(Environment.MEDIA_MOUNTED)) {

                    String imagePath = getImageName("photo");

//                    {
//                        thermalImage.saveAs(imagePath);
                    ///////END - MODEL 1
//                    }
                    {
                        /////// START - MODEL 2 -- SAVE TO CSV
                        double[] temperature = thermalImage.getValues(new Rectangle(0, 0, 480, 640));
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    FileOutputStream file = new FileOutputStream(new File(imagePath));
                                    file.write((handleDataForCSV(temperature)).getBytes());
                                    file.flush();

                                    file.close();
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
                    }
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
                    String videoPath = getImageName("video");

//                    thermalImage.saveAs(videoPath);
                    {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    FileOutputStream file = new FileOutputStream(new File(videoPath));
                                    StringBuilder result = new StringBuilder();

                                    for (int k = 0; k < 640; k++) {
                                        double[] temp = Arrays.copyOfRange(temperature, k * 480, (k * 480) + 479);
                                        result.append(Arrays.toString(temp).replaceAll(" ", "").replace("[", "").replace("]", "")).append("\n");
                                    }
                                    file.write((result.toString()).getBytes());
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
                    }
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
        return Arrays.toString(temperature).replaceAll(" ", "").replace("[", "").replace("]", "");
    }

    /**
     * Camera Discovery thermalImageStreamListener, is notified if a new camera was found during a active discovery phase
     * <p>
     * Note that callbacks are received on a non-ui thread so have to eg use {@link #runOnUiThread(Runnable)} to interact view UI components
     */
    private DiscoveryEventListener cameraDiscoveryListener = new DiscoveryEventListener() {
        @Override
        public void onCameraFound(Identity identity) {
            Log.d(TAG, "onCameraFound identity:" + identity);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    cameraHandler.add(identity);
                }
            });
        }

        @Override
        public void onDiscoveryError(CommunicationInterface communicationInterface, ErrorCode errorCode) {
            Log.d(TAG, "onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    stopDiscovery();
                    showMessage.show("onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);
                }
            });
        }
    };

    private ShowMessage showMessage = new ShowMessage() {
        @Override
        public void show(String message) {
            Toast.makeText(RecordProcess.this, message, Toast.LENGTH_SHORT).show();
        }
    };


    private void setupViews() {
        itimer = findViewById(R.id.itimer);
        msxImage = findViewById(R.id.msx_image);
        fab = findViewById(R.id.floatingActionButton4);
        battery = findViewById(R.id.battery);
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


    @SuppressLint("SetTextI18n")
    public void video2(View view) {
        if (!isVideoRecord) {
            isVideoRecord = true;
            videoRecordFinished = false;
            timerStart();
        } else {
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
                        runOnUiThread(() -> {
                            showMessage.show("Video Saved. " + count);
                            count = 0;
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


//    private void videoHandler2() {
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/flirapp/image/";
//                String state = Environment.getExternalStorageState();
//                if (state.equals(Environment.MEDIA_MOUNTED)) {
//                    Calendar now = new GregorianCalendar();
//                    SimpleDateFormat simpleDate = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
//                    String fileName = simpleDate.format(now.getTime());
//                    ////
//                    videoHandlerIns = new VideoHandler(dirPath + fileName + ".mp4");
//                    try {
//                        videoHandlerIns.pass(dirPath + "temp/");
//                    } catch (IOException e) {
//                        runOnUiThread(() -> {
//                            showMessage.show("Save failed! " + e);
//                        });
//                    }
//                    ////
//                    runOnUiThread(() -> {
//                        showMessage.show("Saved.");
//                    });
//                } else {
//                    runOnUiThread(() -> {
//                        showMessage.show("Save failed! Media is not mounted.");
//                    });
//                }
//            }
//        }).start();
//    }


    public void timerStop() {
        itimer.stop();
    }

    public void timerStart() {
        itimer.setBase(SystemClock.elapsedRealtime());
        itimer.start();
    }

}

