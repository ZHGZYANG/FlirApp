package com.example.flirapp;
// CameraDetected is main activity

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.icu.text.DecimalFormat;
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
import com.flir.thermalsdk.image.JavaImageBuffer;
import com.flir.thermalsdk.image.Point;
import com.flir.thermalsdk.image.Rectangle;
import com.flir.thermalsdk.image.TemperatureUnit;
import com.flir.thermalsdk.image.ThermalImage;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

import us.hebi.matlab.mat.format.Mat5;
import us.hebi.matlab.mat.types.Cell;
import us.hebi.matlab.mat.types.MatFile;
import us.hebi.matlab.mat.types.MatlabType;
import us.hebi.matlab.mat.types.Matrix;

public class RecordProcess extends AppCompatActivity {
    private static final String TAG = "RecordProcess";
    private static int count = 0;
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
    private TextView templeft;
    private TextView tempright;
    private TextView battery;
    private LinkedBlockingQueue<Bitmap> currentFramesBuffer = new LinkedBlockingQueue<>(100); //for video
    //    private boolean videoRecordFinished;
    private boolean isVideoRecord = false;
    private boolean photoCapture=false;
    protected boolean batteryperWait = false;
    protected boolean validationPassed = false;
    private LinkedBlockingQueue<FrameDataHolder> framesBuffer = new LinkedBlockingQueue<>(21);
    private UsbPermissionHandler usbPermissionHandler = new UsbPermissionHandler();
    static protected ArrayList<Matrix> matrices = new ArrayList<>();
//    private VideoHandler videoHandlerIns;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_process);
        cameraHandler = CameraDetected.cameraHandler;
        permissionHandler = new PermissionHandler(showMessage, RecordProcess.this);

        setupViews();

        permissionHandler.checkForStoragePermission();
    }

    Thread batteryper = new Thread(new Runnable() {
        @SuppressLint("SetTextI18n")
        @Override
        public void run() {
            try {
                while (true) {
                    if (!batteryperWait) {
                        Integer batteryResult = cameraHandler.batteryPercent();
                        runOnUiThread(() -> {
                            battery.setText(batteryResult.toString() + "%");
                        });
                        if (batteryResult < 25) {
                            runOnUiThread(() -> {
                                battery.setTextColor(Color.RED);
                                showMessage.show("Low battery! Please charge the camera!");
                            });
                        }
                    }
                    Thread.sleep(30000);
                }
            } catch (InterruptedException ignored) {
            }
        }
    });

    @Override
    public void onPause() {
        super.onPause();
        if (cameraHandler.getCamera() != null) {
            batteryperWait = true;
            cameraHandler.stop();
        }
    }

    public void onResume() {
        super.onResume();

        if (cameraHandler.getCamera() != null) {
            runOnUiThread(() -> {
                cameraHandler.startStream(streamDataListener);
            });
            batteryperWait = false;
            try {
                batteryper.start();
            } catch (Exception ignored) {
            }
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
                        connect(cameraHandler.getFlirOne());
//                            connect(cameraHandler.getFlirOneEmulator());
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
            runOnUiThread(() -> {
                showMessage.show("connect(), can't connect, no camera available");
            });
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
            runOnUiThread(() -> {
                showMessage.show("Permission was denied for identity ");
            });
        }

        @Override
        public void error(UsbPermissionHandler.UsbPermissionListener.ErrorType errorType, final Identity identity) {
            runOnUiThread(() -> {
                showMessage.show("Error when asking for permission for FLIR ONE, error:" + errorType + " identity:" + identity);
            });
        }
    };

    private void doConnect(Identity identity) {
        new Thread(() -> {
            try {
                cameraHandler.connect(identity, connectionStatusListener);
                runOnUiThread(() -> {
                    cameraHandler.startStream(streamDataListener);
                });
                batteryperWait = false;
                try {
                    batteryper.start();
                } catch (Exception ignored) {
                }
            } catch (IOException e) {
//                runOnUiThread(() -> {
                Log.d(TAG, "Could not connect: " + e);
//                    updateConnectionText(identity, "DISCONNECTED");
//                });
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
            double[] temperature = thermalImage.getValues(new Rectangle(0, 0, 480, 640));

            //Following: image validation
            /*
                LEFT   Difference celsius 2
                60,360 INNER   30，360 OUTSIDE
                165,300      230,310
                RIGHT
                295,340     230,310
                395,340     450,340
                TOP   Difference celsius 1
                LEFT
                130,220    240,150
                RIGHT
                340,220    240,150
             */

//            double tempLeftInner1=thermalImage.getValueAt(new Point(60, 360));
//            double tempLeftInner2=thermalImage.getValueAt(new Point(165, 300));
//            double tempLeftOutside1=thermalImage.getValueAt(new Point(30, 360));
//            double tempLeftOutside2=thermalImage.getValueAt(new Point(230, 310));
//            double tempRightInner1=thermalImage.getValueAt(new Point(295, 340));
//            double tempRightInner2=thermalImage.getValueAt(new Point(395, 340));
//            double tempRightOutside1=thermalImage.getValueAt(new Point(230, 310));
//            double tempRightOutside2=thermalImage.getValueAt(new Point(450, 340));
//            double tempTopLeftInner=thermalImage.getValueAt(new Point(130, 220));
//            double tempTopLeftOut=thermalImage.getValueAt(new Point(240, 150));
//            double tempTopRightInner=thermalImage.getValueAt(new Point(340, 220));
//            double tempTopRightOut=thermalImage.getValueAt(new Point(240, 150));
//            double leftLeft=tempLeftInner1-tempLeftOutside1;
//            double leftRight=tempLeftInner2-tempLeftOutside2;
//            double leftTop=tempTopLeftInner-tempTopLeftOut;
//            double rightLeft=tempRightInner1-tempRightOutside1;
//            double rightRight=tempRightInner2-tempRightOutside2;
//            double rightTop=tempTopRightInner-tempTopRightOut;
            double tempOutside = thermalImage.getValueAt(new Point(240, 120));
            double tempLeftP1 = thermalImage.getValueAt(new Point(126, 244)) - tempOutside;
            double tempLeftP2 = thermalImage.getValueAt(new Point(92, 345)) - tempOutside;
            double tempLeftP3 = thermalImage.getValueAt(new Point(154, 345)) - tempOutside;
            double tempRightP1 = thermalImage.getValueAt(new Point(350, 240)) - tempOutside;
            double tempRightP2 = thermalImage.getValueAt(new Point(320, 330)) - tempOutside;
            double tempRightP3 = thermalImage.getValueAt(new Point(377, 330)) - tempOutside;

            if (tempLeftP1 > 2 && tempLeftP2 > 2 && tempLeftP3 > 2 && tempRightP1 > 2 && tempRightP2 > 2 && tempRightP3 > 2) {
                validationPassed = true;
                runOnUiThread(() -> {
                    fab.setVisibility(View.VISIBLE);
                });
            } else {
                validationPassed = false;
                runOnUiThread(() -> {
                    if (isVideoRecord)
                        fab.performClick();
                    fab.setVisibility(View.INVISIBLE);
                });
            }

            //Following: average temperature calculation
            double[] tempLeft1 = thermalImage.getValues(new Rectangle(87, 215, 91, 50));
            double[] tempLeft2 = thermalImage.getValues(new Rectangle(66, 253, 118, 210));
            double[] tempRight1 = thermalImage.getValues(new Rectangle(292, 221, 91, 42));
            double[] tempRight2 = thermalImage.getValues(new Rectangle(288, 264, 113, 210));
            averageCalculation(tempLeft1, tempLeft2, tempRight1, tempRight2);

            //Following: save images
            if (photoCapture) { // photo capture
                photoCapture = false;
                String state = Environment.getExternalStorageState();
                if (state.equals(Environment.MEDIA_MOUNTED)) {

                    String imagePath = getImageName("photo");
//                    thermalImage.saveAs(imagePath+".jpg");
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {

//                                {
//                                    FileOutputStream file = new FileOutputStream(new File(imagePath));
//                                    StringBuilder result = new StringBuilder();
//                                    for (int k = 0; k < 640; k++) {
//                                        double[] temp = Arrays.copyOfRange(temperature, k * 480, (k * 480) + 479);
//                                        result.append(Arrays.toString(temp).replaceAll(" ", "").replace("[", "").replace("]", "")).append("\n");
//                                    }
//                                    file.write((result.toString()).getBytes());
//                                    file.flush();
//                                    file.close();
//                                }


                                {
                                    MatFile matFile = Mat5.newMatFile();
                                    Matrix data = Mat5.newMatrix(640, 480, MatlabType.Double);
                                    int k = 0;
                                    for (int i = 0; i < 640; i++) {
                                        for (int j = 0; j < 480; j++) {
                                            data.setDouble(i, j, temperature[k++]);
                                        }
                                    }
                                    matFile.addArray("temperature_Metrix", data);
                                    Mat5.writeToFile(matFile, imagePath);
                                }

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

            if (isVideoRecord) { //video record
                count++;

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Matrix data = Mat5.newMatrix(640, 480, MatlabType.Double);
                        int k = 0;
                        for (int i = 0; i < 640; i++) {
                            for (int j = 0; j < 480; j++) {
                                data.setDouble(i, j, temperature[k++]);
                            }
                        }
                        matrices.add(data);
                    }
                }).start();
            }
        }
    };

    protected void averageCalculation(double[] left1, double[] left2, double[] right1, double[] right2) {
        new Thread(new Runnable() {
            @Override
            @SuppressLint("SetTextI18n")
            public void run() {
                double leftave1 = 0, leftave2 = 0, rightave1 = 0, rightave2 = 0;
                for (double v : left1) {
                    leftave1 += v / left1.length;
                }
                for (double v : left2) {
                    leftave2 += v / left2.length;
                }
                for (double v : right1) {
                    rightave1 += v / right1.length;
                }
                for (double v : right2) {
                    rightave2 += v / right2.length;
                }
                String left = String.valueOf((leftave1 + leftave2) / 2).substring(0, 5);
                String right = String.valueOf((rightave1 + rightave2) / 2).substring(0, 5);
                runOnUiThread(() -> {
                    templeft.setText(left + "°C");
                    tempright.setText(right + "°C");
                });
            }
        }).start();

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
        runOnUiThread(()->{
            fab.setVisibility(View.INVISIBLE);
        });
        battery = findViewById(R.id.battery);
        templeft = findViewById(R.id.templeft);
        tempright = findViewById(R.id.tempright);
    }

    protected String getImageName(String model) {
        Calendar now = new GregorianCalendar();
        SimpleDateFormat simpleDate = new SimpleDateFormat("yyyyMMddHHmmssSS", Locale.getDefault());
        String fileName = simpleDate.format(now.getTime());

        String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/flirapp/image/";

        if (!model.equals("photo"))
            fileName += "_video";
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dirPath + fileName + ".mat";
    }

    public void capture(View view) {
        photoCapture = true;
    }

    @SuppressLint("SetTextI18n")
    public void video2(View view) {
        if (!isVideoRecord) {
            isVideoRecord = true;
            timerStart();
        } else {
            timerStop();
            isVideoRecord = false;
            if (!validationPassed) {
                runOnUiThread(() -> {
                    showMessage.show("Bad video, please put your feet on the right place and record again.");
                });
                matrices.clear();
                return;
            }
            runOnUiThread(() -> {
                showMessage.show("Video saving...");
            });
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String videoPath = getImageName("VIDEO");
                    try {
                        MatFile matFile = Mat5.newMatFile();
                        Cell cell = Mat5.newCell(1, matrices.size());
                        for (int a = 0; a < matrices.size(); a++) {
                            cell.set(a, matrices.get(a));
                        }
                        matFile.addArray("temperature_Metrices", cell);
                        Mat5.writeToFile(matFile, videoPath);
                        matrices.clear();
                        runOnUiThread(() -> {
                            showMessage.show("Video Saved!" + count);
                            count = 0;
                        });


//                        Intent intent1 = new Intent(RecordProcess.this, EndPage.class);
//                        disconnect();
//                        startActivity(intent1);

                    } catch (IOException e) {
                        runOnUiThread(() -> {
                            showMessage.show("Save video faild! " + e);
                        });
                    }
                }
            }).start();
        }
    }

    public void timerStop() {
        itimer.stop();
    }

    public void timerStart() {
        itimer.setBase(SystemClock.elapsedRealtime());
        itimer.start();
    }

}

