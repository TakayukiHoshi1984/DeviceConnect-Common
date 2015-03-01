/*
DataLayerListenerService.java
Copyright (c) 2014 NTT DOCOMO,INC.
Released under the MIT license
http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Vibrator;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * DataLayerListenerService.
 *
 * @author NTT DOCOMO, INC.
 */
public class DataLayerListenerService extends WearableListenerService implements SensorEventListener {
    /** Google API Client. */
    private GoogleApiClient mGoogleApiClient;

    /** SensorManager. */
    private SensorManager mSensorManager;

    /** Gyro x. */
    private float mGyroX;

    /** Gyro y. */
    private float mGyroY;

    /** Gyro z. */
    private float mGyroZ;

    /** Device NodeID . */
    private String mId;

    /** GyroSensor. */
    private Sensor mGyroSensor;

    /** AcceleratorSensor. */
    private Sensor mAccelerometer;

    /** The start time for measuring the interval. */
    private long mStartTime;

    /** Broadcast receiver. */
    MyBroadcastReceiver mReceiver = null;

    @Override
    public void onCreate() {
        super.onCreate();

        // Define google play service
        mGoogleApiClient = new GoogleApiClient.Builder(this).addApi(Wearable.API).build();
        // Connect google play service
        mGoogleApiClient.connect();

        // set BroadcastReceiver
        mReceiver = new MyBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter(WearConst.PARAM_DC_WEAR_KEYEVENT_ACT_TO_SVC);
        intentFilter.addAction(WearConst.PARAM_DC_WEAR_TOUCH_ACT_TO_SVC);
        registerReceiver(mReceiver, intentFilter);
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mGoogleApiClient != null) {
            // Disconnect google play service.
            mGoogleApiClient.disconnect();
        }

        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this, mAccelerometer);
            mSensorManager.unregisterListener(this, mGyroSensor);
            mSensorManager.unregisterListener(this);
            mSensorManager = null;
        }

        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
    }

    @Override
    public void onMessageReceived(final MessageEvent messageEvent) {
        String action = messageEvent.getPath();

        if  ((action.equals(WearConst.DEVICE_TO_WEAR_DEIVCEORIENTATION_REGISTER))
          || (action.equals(WearConst.DEVICE_TO_WEAR_KEYEVENT_ONDOWN_REGISTER))
          || (action.equals(WearConst.DEVICE_TO_WEAR_KEYEVENT_ONUP_REGISTER))
          || (action.equals(WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCH_REGISTER))
          || (action.equals(WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCHSTART_REGISTER))
          || (action.equals(WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCHEND_REGISTER))
          || (action.equals(WearConst.DEVICE_TO_WEAR_TOUCH_ONDOUBLETAP_REGISTER))
          || (action.equals(WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCHMOVE_REGISTER))
          || (action.equals(WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCHCANCEL_REGISTER))) {
            if (mGoogleApiClient == null || !mGoogleApiClient.isConnected()) {
                mGoogleApiClient = new GoogleApiClient.Builder(this).addApi(Wearable.API).build();
                mGoogleApiClient.connect();
                ConnectionResult connectionResult = mGoogleApiClient.blockingConnect(30, TimeUnit.SECONDS);
                if (!connectionResult.isSuccess()) {
                    if (BuildConfig.DEBUG) {
                        Log.e("WEAR", "Failed to connect google play service.");
                    }
                }
            }
        }

        // get id of wear device
        mId = messageEvent.getSourceNodeId();
        if (action.equals(WearConst.DEVICE_TO_WEAR_VIBRATION_RUN)) {
            // get vibration pattern
            String mPattern = new String(messageEvent.getData());

            // Make array of pattern
            String[] mPatternArray = mPattern.split(",", 0);
            long[] mPatternLong = new long[mPatternArray.length + 1];
            mPatternLong[0] = 0;
            for (int i = 1; i < mPatternLong.length; i++) {
                mPatternLong[i] = Integer.parseInt(mPatternArray[i - 1]);
            }

            // vibrate
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(mPatternLong, -1);
        } else if (action.equals(WearConst.DEVICE_TO_WEAR_VIBRATION_DEL)) {
            // stop vibrate
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.cancel();
        } else if (action.equals(WearConst.DEVICE_TO_WEAR_DEIVCEORIENTATION_REGISTER)) {
            mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            List<Sensor> accelSensors = mSensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
            if (accelSensors.size() > 0) {
                mAccelerometer = accelSensors.get(0);
                mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            }

            List<Sensor> gyroSensors = mSensorManager.getSensorList(Sensor.TYPE_GYROSCOPE);
            if (gyroSensors.size() > 0) {
                mGyroSensor = gyroSensors.get(0);
                mSensorManager.registerListener(this, mGyroSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }

            mStartTime = System.currentTimeMillis();

            // For service destruction suppression.
            Intent i = new Intent(WearConst.ACTION_WEAR_PING_SERVICE);
            startService(i);
        } else if (action.equals(WearConst.DEVICE_TO_WEAR_KEYEVENT_ONDOWN_REGISTER)) {
            execKeyEventActivity(WearConst.DEVICE_TO_WEAR_KEYEVENT_ONDOWN_REGISTER);
        } else if (action.equals(WearConst.DEVICE_TO_WEAR_KEYEVENT_ONUP_REGISTER)) {
            execKeyEventActivity(WearConst.DEVICE_TO_WEAR_KEYEVENT_ONUP_REGISTER);
        } else if (action.equals(WearConst.DEVICE_TO_WEAR_DEIVCEORIENTATION_UNREGISTER)) {
            if (mSensorManager != null) {
                mSensorManager.unregisterListener(this, mAccelerometer);
                mSensorManager.unregisterListener(this, mGyroSensor);
                mSensorManager.unregisterListener(this);
                mSensorManager = null;
            }
        } else if (action.equals(WearConst.DEVICE_TO_WEAR_KEYEVENT_ONDOWN_UNREGISTER)) {
            // Broadcast to Activity.
            Intent i = new Intent(WearConst.PARAM_DC_WEAR_KEYEVENT_SVC_TO_ACT);
            i.putExtra(WearConst.PARAM_KEYEVENT_REGIST, WearConst.DEVICE_TO_WEAR_KEYEVENT_ONDOWN_UNREGISTER);
            sendBroadcast(i);
        } else if (action.equals(WearConst.DEVICE_TO_WEAR_KEYEVENT_ONUP_UNREGISTER)) {
            // Broadcast to Activity.
            Intent i = new Intent(WearConst.PARAM_DC_WEAR_KEYEVENT_SVC_TO_ACT);
            i.putExtra(WearConst.PARAM_KEYEVENT_REGIST, WearConst.DEVICE_TO_WEAR_KEYEVENT_ONUP_UNREGISTER);
            sendBroadcast(i);
        } else if (action.equals(WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCH_REGISTER)) {
            execTouchActivity(WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCH_REGISTER);
        } else if (action.equals(WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCHSTART_REGISTER)) {
            execTouchActivity(WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCHSTART_REGISTER);
        } else if (action.equals(WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCHEND_REGISTER)) {
            execTouchActivity(WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCHEND_REGISTER);
        } else if (action.equals(WearConst.DEVICE_TO_WEAR_TOUCH_ONDOUBLETAP_REGISTER)) {
            execTouchActivity(WearConst.DEVICE_TO_WEAR_TOUCH_ONDOUBLETAP_REGISTER);
        } else if (action.equals(WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCHMOVE_REGISTER)) {
            execTouchActivity(WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCHMOVE_REGISTER);
        } else if (action.equals(WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCHCANCEL_REGISTER)) {
            execTouchActivity(WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCHCANCEL_REGISTER);
        } else if (action.equals(WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCH_UNREGISTER)) {
            // Broadcast to Activity.
            Intent i = new Intent(WearConst.PARAM_DC_WEAR_TOUCH_SVC_TO_ACT);
            i.putExtra(WearConst.PARAM_TOUCH_REGIST, WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCH_UNREGISTER);
            sendBroadcast(i);
        } else if (action.equals(WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCHSTART_UNREGISTER)) {
            // Broadcast to Activity.
            Intent i = new Intent(WearConst.PARAM_DC_WEAR_TOUCH_SVC_TO_ACT);
            i.putExtra(WearConst.PARAM_TOUCH_REGIST, WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCHSTART_UNREGISTER);
            sendBroadcast(i);
        } else if (action.equals(WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCHEND_UNREGISTER)) {
            // Broadcast to Activity.
            Intent i = new Intent(WearConst.PARAM_DC_WEAR_TOUCH_SVC_TO_ACT);
            i.putExtra(WearConst.PARAM_TOUCH_REGIST, WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCHEND_UNREGISTER);
            sendBroadcast(i);
        } else if (action.equals(WearConst.DEVICE_TO_WEAR_TOUCH_ONDOUBLETAP_UNREGISTER)) {
            // Broadcast to Activity.
            Intent i = new Intent(WearConst.PARAM_DC_WEAR_TOUCH_SVC_TO_ACT);
            i.putExtra(WearConst.PARAM_TOUCH_REGIST, WearConst.DEVICE_TO_WEAR_TOUCH_ONDOUBLETAP_UNREGISTER);
            sendBroadcast(i);
        } else if (action.equals(WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCHMOVE_UNREGISTER)) {
            // Broadcast to Activity.
            Intent i = new Intent(WearConst.PARAM_DC_WEAR_TOUCH_SVC_TO_ACT);
            i.putExtra(WearConst.PARAM_TOUCH_REGIST, WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCHMOVE_UNREGISTER);
            sendBroadcast(i);
        } else if (action.equals(WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCHCANCEL_UNREGISTER)) {
            // Broadcast to Activity.
            Intent i = new Intent(WearConst.PARAM_DC_WEAR_TOUCH_SVC_TO_ACT);
            i.putExtra(WearConst.PARAM_TOUCH_REGIST, WearConst.DEVICE_TO_WEAR_TOUCH_ONTOUCHCANCEL_UNREGISTER);
            sendBroadcast(i);
        }
    }

    @Override
    public void onPeerConnected(final Node peer) {
    }

    @Override
    public void onPeerDisconnected(final Node peer) {
    }

    @Override
    public void onSensorChanged(final SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            long time = System.currentTimeMillis();
            long interval = time - mStartTime;
            mStartTime = time;
            /* Acceleration x. */
            float mAccellX = sensorEvent.values[0];
            /** Acceleration y. */
            float mAccellY = sensorEvent.values[1];
            /** Acceleration z. */
            float mAccellZ = sensorEvent.values[2];
            final String data = mAccellX + "," + mAccellY + "," + mAccellZ
                    + "," + mGyroX + "," + mGyroY + "," + mGyroZ + "," + interval;

            // Send message data.
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(final Void... params) {
                    if (!mGoogleApiClient.isConnected()) {
                        mGoogleApiClient.connect();
                    } else {
                        MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(mGoogleApiClient, mId,
                                WearConst.WEAR_TO_DEVICE_DEIVCEORIENTATION_DATA, data.getBytes()).await();
                        if (!result.getStatus().isSuccess()) {
                            if (BuildConfig.DEBUG) {
                                Log.e("WEAR", "Failed to send a sensor event.");
                            }
                        }
                    }
                    return null;
                }
            }.execute();

        } else if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            mGyroX = sensorEvent.values[0];
            mGyroY = sensorEvent.values[1];
            mGyroZ = sensorEvent.values[2];
        }
    }

    @Override
    public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
    }

    /**
     * Execute Key Event Activity.
     *
     * @param regist Register string.
     */
    private void execKeyEventActivity(final String regist) {
        // Start Activity.
        Intent i = new Intent(this, WearKeyEventProfileActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra(WearConst.PARAM_KEYEVENT_REGIST, regist);
        this.startActivity(i);

        // Send event regist to Activity.
        i = new Intent(WearConst.PARAM_DC_WEAR_KEYEVENT_SVC_TO_ACT);
        i.putExtra(WearConst.PARAM_KEYEVENT_REGIST, regist);
        sendBroadcast(i);
    }

    /**
     * Execute Touch Activity.
     *
     * @param regist Register string.
     */
    private void execTouchActivity(final String regist) {
        // Start Activity.
        Intent i = new Intent(this, WearTouchProfileActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra(WearConst.PARAM_TOUCH_REGIST, regist);
        this.startActivity(i);

        // Send event regist to Activity.
        i = new Intent(WearConst.PARAM_DC_WEAR_TOUCH_SVC_TO_ACT);
        i.putExtra(WearConst.PARAM_TOUCH_REGIST, regist);
        sendBroadcast(i);
    }

    /**
     * Broadcast Receiver.
     */
    public class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent i) {
            String action = i.getAction();
            final String data;
            final String profile;

            if (action.equals(WearConst.PARAM_DC_WEAR_KEYEVENT_ACT_TO_SVC)) {
                data = i.getStringExtra(WearConst.PARAM_KEYEVENT_DATA);
                profile = WearConst.WEAR_TO_DEVICE_KEYEVENT_DATA;
            } else if (action.equals(WearConst.PARAM_DC_WEAR_TOUCH_ACT_TO_SVC)) {
                data = i.getStringExtra(WearConst.PARAM_TOUCH_DATA);
                profile = WearConst.WEAR_TO_DEVICE_TOUCH_DATA;
            } else {
                return;
            }

            // Send message data.
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(final Void... params) {
                    if (!mGoogleApiClient.isConnected()) {
                        mGoogleApiClient.connect();
                    } else {
                        ConnectionResult connectionResult = mGoogleApiClient.blockingConnect(30, TimeUnit.SECONDS);
                        if (!connectionResult.isSuccess()) {
                            if (BuildConfig.DEBUG) {
                                Log.e("WEAR", "Failed to connect google play service.");
                            }
                        }

                        MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(mGoogleApiClient, mId,
                                profile, data.getBytes()).await();
                        if (!result.getStatus().isSuccess()) {
                            if (BuildConfig.DEBUG) {
                                Log.e("WEAR", "Failed to send a key event.");
                            }
                        }
                    }
                    return null;
                }
            }.execute();
        }
    }
}
