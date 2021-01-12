package com.example.flirapp;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.flir.thermalsdk.ErrorCode;
import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid;
import com.flir.thermalsdk.androidsdk.live.connectivity.UsbPermissionHandler;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.flir.thermalsdk.log.ThermalLog;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

public class CameraDetected extends AppCompatActivity {
    private static final String TAG = "CameraDetected";

    //Handles Android permission for eg Network
    private PermissionHandler permissionHandler;

    //Handles network camera operations
    static protected CameraHandler cameraHandler;

    private Identity connectedIdentity = null;
    private TextView connectionStatus;
    private TextView status;

    private ImageView msxImage;
    private ImageView photoImage;

    private LinkedBlockingQueue<FrameDataHolder> framesBuffer = new LinkedBlockingQueue(21);
    private UsbPermissionHandler usbPermissionHandler = new UsbPermissionHandler();
    private int connectReturnValue;



    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_detected);

        ThermalLog.LogLevel enableLoggingInDebug = BuildConfig.DEBUG ? ThermalLog.LogLevel.DEBUG : ThermalLog.LogLevel.NONE;

        ThermalSdkAndroid.init(getApplicationContext(), enableLoggingInDebug);

        permissionHandler = new PermissionHandler(showMessage, CameraDetected.this);

        cameraHandler = new CameraHandler();
        setupViews();


//        connectReturnValue=-10;
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                startDiscovery();
//
//                Timer timer = new Timer();
//                TimerTask timerTask = new TimerTask() {
//                    @Override
//                    public void run() {
////                        connect(cameraHandler.getFlirOne());
//                        connect(cameraHandler.getFlirOneEmulator());
//
//                        connectReturnValue=-1;
//                        runOnUiThread(()->{
//                            if (connectReturnValue==0){
//                                Intent intent1 = new Intent(CameraDetected.this, RecordProcess.class);
//                                intent1.putExtra("camerahandler", (Parcelable) cameraHandler);
//                                startActivity(intent1);
//                            }else if (connectReturnValue==-1){ // low battery
//                                Intent intent2 = new Intent(CameraDetected.this, ChargeCamera.class);
//                                startActivity(intent2);
//                                CameraDetected.this.finish();
//                            }else if (connectReturnValue==-2){ //IO exception
//                                Intent intent3 = new Intent(CameraDetected.this, WelcomeActivity.class);
//                                startActivity(intent3);
//                                CameraDetected.this.finish();
//                            }
//                        });
//
//                    }
//                };
//                timer.schedule(timerTask, 1000 * 8);
//
//            }
//        }).start();





    }

//    @Override
//    protected void onResume() {
//
//        super.onResume();
//        connectReturnValue=-10;
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                startDiscovery();
//
//                Timer timer = new Timer();
//                TimerTask timerTask = new TimerTask() {
//                    @Override
//                    public void run() {
//                        connect(cameraHandler.getFlirOne());
//                    }
//                };
//                timer.schedule(timerTask, 1000 * 8);
//
//            }
//        }).start();
//
//    }


    @Override
    protected void onStart() {
        super.onStart();
        connectReturnValue=-10;
        new Thread(new Runnable() {
            @Override
            public void run() {
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



                Timer intimer = new Timer();
                TimerTask intimerTask = new TimerTask() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void run() {

                        runOnUiThread(()->{
                            if (connectReturnValue==0){
                                Intent intent1 = new Intent(CameraDetected.this, RecordProcess.class);
                                startActivity(intent1);
                            }else if (connectReturnValue==-1){ // low battery
                                Intent intent2 = new Intent(CameraDetected.this, ChargeCamera.class);
                                startActivity(intent2);
//                                CameraDetected.this.finish();
                            }else if (connectReturnValue==-2){ //IO exception
                                Intent intent3 = new Intent(CameraDetected.this, WelcomeActivity.class);
                                startActivity(intent3);
//                                CameraDetected.this.finish();
                            }
                        });


                    }
                };
                intimer.schedule(intimerTask, 1000 * 12);

            }
        }).start();

    }



//    public void startDiscovery(View view) {
//        startDiscovery();
//    }

//    public void stopDiscovery(View view) {
//        stopDiscovery();
//    }


//    public void connectFlirOne(View view) {
//        connect(cameraHandler.getFlirOne());
//    }

//    public void connectSimulatorOne(View view) {
//        connect(cameraHandler.getCppEmulator());
//    }

//    public void connectSimulatorTwo(View view) {
//        connect(cameraHandler.getFlirOneEmulator());
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
    @SuppressLint("SetTextI18n")
    private void connect(Identity identity) {
        //We don't have to stop a discovery but it's nice to do if we have found the camera that we are looking for
        cameraHandler.stopDiscovery(discoveryStatusListener);
        if (connectedIdentity != null) {
            Log.d(TAG, "connect(), in *this* code sample we only support one camera connection at the time");
//            CameraDetected.this.showMessage.show("connect(), in *this* code sample we only support one camera connection at the time");
            status.setText("connect(), in *this* code sample we only support one camera connection at the time");
            return;
        }

        if (identity == null) {
            Log.d(TAG, "connect(), can't connect, no camera available");
//            Log.d(TAG, "");
//            CameraDetected.this.showMessage.show("connect(), can't connect, no camera available");
            status.setText("connect(), can't connect, no camera available");
            //            showMessage.show("");
//            Intent intent1 = new Intent(CameraDetected.this, MainActivity.class);
//            startActivity(intent1);
            return;
        }

        connectedIdentity = identity;

//        updateConnectionText(identity, "CONNECTING");
        //IF your using "USB_DEVICE_ATTACHED" and "usb-device vendor-id" in the Android Manifest
        // you don't need to request permission, see documentation for more information
        if (UsbPermissionHandler.isFlirOne(identity)) {
            usbPermissionHandler.requestFlirOnePermisson(identity, this, permissionListener);
        } else {
            //the device is not flir one, replug-in
//            CameraDetected.this.showMessage.show("the device is not flir one");
            status.setText("the device is not flir one");
//            Intent intent1 = new Intent(CameraDetected.this, MainActivity.class);
//            startActivity(intent1);

//            doConnect(identity);
        }
    }

    private UsbPermissionHandler.UsbPermissionListener permissionListener = new UsbPermissionHandler.UsbPermissionListener() {
        @SuppressLint("SetTextI18n")
        @Override
        public void permissionGranted(Identity identity) {
//            CameraDetected.this.showMessage.show("usb permissionGranted");
            status.setText("usb permissionGranted");
            doConnect(identity);
        }

        @Override
        public void permissionDenied(Identity identity) {
            CameraDetected.this.showMessage.show("Permission was denied for identity ");
        }

        @Override
        public void error(UsbPermissionHandler.UsbPermissionListener.ErrorType errorType, final Identity identity) {
            CameraDetected.this.showMessage.show("Error when asking for permission for FLIR ONE, error:" + errorType + " identity:" + identity);
        }
    };

    @SuppressLint("SetTextI18n")
    private void doConnect(Identity identity) {
//        CameraDetected.this.showMessage.show("connecting");
        status.setText("connecting");
//        CameraDetected.this.showMessage.show(identity.toString());
//        new Thread(() -> {
            try {
//                CameraDetected.this.showMessage.show("connecting 2");
                status.setText("connecting 2");

                cameraHandler.connect(identity, connectionStatusListener);

                runOnUiThread(() -> {
//                    updateConnectionText(identity, "CONNECTED");
//                    CameraDetected.this.showMessage.show("connected");
                    status.setText("connected");

//                    Intent intent2 = new Intent(CameraDetected.this, RecordProcess.class);
//                    intent2.putExtra("camerahandler", (Parcelable) cameraHandler);
//                    startActivity(intent2);

//                    cameraHandler.startStream(streamDataListener);
                });
                // judging battery
                if (!cameraHandler.battery()) { //low battery and no charging
                    connectReturnValue=-1;
                    return;
                }
                connectReturnValue=0;

            } catch (IOException e) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Could not connect: " + e);
//                    updateConnectionText(identity, "DISCONNECTED");
                    CameraDetected.this.showMessage.show("connecting failed. " + e);
                    String strtemp="connecting failed. " + e;
                    status.setText(strtemp);

//                    Intent intent1 = new Intent(CameraDetected.this, MainActivity.class);
//                    startActivity(intent1);

                });
                connectReturnValue=-2;
            }
//        }).start();
    }

    /**
     * Disconnect to a camera
     */
//    private void disconnect() {
//        updateConnectionText(connectedIdentity, "DISCONNECTING");
//        connectedIdentity = null;
//        Log.d(TAG, "disconnect() called with: connectedIdentity = [" + connectedIdentity + "]");
//        new Thread(() -> {
//            cameraHandler.disconnect();
//            runOnUiThread(() -> {
//                updateConnectionText(null, "DISCONNECTED");
//            });
//        }).start();
//    }

    /**
     * Update the UI text for connection status
     */
//    private void updateConnectionText(Identity identity, String status) {
//        String deviceId = identity != null ? identity.deviceId : "";
////        connectionStatus.setText(getString(R.string.connection_status_text, deviceId + " " + status));
//    }

    /**
     * Start camera discovery
     */
    @SuppressLint("SetTextI18n")
    private void startDiscovery() {
        cameraHandler.startDiscovery(cameraDiscoveryListener, discoveryStatusListener);
        runOnUiThread(()->{
//            CameraDetected.this.showMessage.show("startDiscovery");
            status.setText("startDiscovery");
        });
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
//            discoveryStatus.setText(getString(R.string.connection_status_text, "discovering"));

        }

        @Override
        public void stopped() {
//            discoveryStatus.setText(getString(R.string.connection_status_text, "not discovering"));
//            Intent intent = new Intent();
//            intent.setClass(MainActivity.this, failure.class);
//            startActivity(intent);
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

//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
////                    updateConnectionText(connectedIdentity, "DISCONNECTED");
//                }
//            });
        }
    };

//    private final CameraHandler.StreamDataListener streamDataListener = new CameraHandler.StreamDataListener() {
//
//        @Override
//        public void images(FrameDataHolder dataHolder) {
//
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
////                    msxImage.setImageBitmap(dataHolder.msxBitmap);
////                    photoImage.setImageBitmap(dataHolder.dcBitmap);
//                }
//            });
//        }
//
//        @Override
//        public void images(Bitmap msxBitmap, Bitmap dcBitmap) {
//
//            try {
//                framesBuffer.put(new FrameDataHolder(msxBitmap,dcBitmap));
//            } catch (InterruptedException e) {
//                //if interrupted while waiting for adding a new item in the queue
//                Log.e(TAG,"images(), unable to add incoming images to frames buffer, exception:"+e);
//            }
//
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    Log.d(TAG,"framebuffer size:"+framesBuffer.size());
//                    FrameDataHolder poll = framesBuffer.poll();
//                    msxImage.setImageBitmap(poll.msxBitmap);
//                    photoImage.setImageBitmap(poll.dcBitmap);
//                }
//            });
//
//        }
//    };

    /**
     * Camera Discovery thermalImageStreamListener, is notified if a new camera was found during a active discovery phase
     * <p>
     * Note that callbacks are received on a non-ui thread so have to eg use {@link #runOnUiThread(Runnable)} to interact view UI components
     */
    private DiscoveryEventListener cameraDiscoveryListener = new DiscoveryEventListener() {
        @Override
        public void onCameraFound(Identity identity) {
            Log.d(TAG, "onCameraFound identity:" + identity);
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    cameraHandler.add(identity);
//                }
//            });
            cameraHandler.add(identity);
        }

        @Override
        public void onDiscoveryError(CommunicationInterface communicationInterface, ErrorCode errorCode) {
            Log.d(TAG, "onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);
            stopDiscovery();
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    stopDiscovery();
////                    MainActivity.this.showMessage.show("onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);
//                }
//            });
        }
    };

    private ShowMessage showMessage = new ShowMessage() {
        @Override
        public void show(String message) {
//            Toast.makeText(CameraDetected.this, message, Toast.LENGTH_LONG).show();
            final Toast toast =  Toast.makeText(CameraDetected.this, message, Toast.LENGTH_LONG);
            toast.show();
        }

    };


//    private void showSDKversion(String version) {
//        TextView sdkVersionTextView = findViewById(R.id.sdk_version);
//        String sdkVersionText = getString(R.string.sdk_version_text, version);
//        sdkVersionTextView.setText(sdkVersionText);
//    }

    private void setupViews() {
//        connectionStatus = findViewById(R.id.connection_status_text);
        status = findViewById(R.id.textView11);

//        msxImage = findViewById(R.id.msx_image);
//        photoImage = findViewById(R.id.photo_image);
    }


//    public void goNext(){
//        Intent intent2 = new Intent(CameraDetected.this, RecordProcess.class);
//        intent2.putExtra("camerahandler", (Parcelable) cameraHandler);
//        startActivity(intent2);
//    }
}