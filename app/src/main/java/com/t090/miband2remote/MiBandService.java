package com.t090.miband2remote;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;


import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import com.zhaoxiaodan.miband.ActionCallback;
import com.zhaoxiaodan.miband.MiBand;
import com.zhaoxiaodan.miband.listeners.HeartRateNotifyListener;
import com.zhaoxiaodan.miband.listeners.NotifyListener;
import com.zhaoxiaodan.miband.listeners.RealtimeStepsNotifyListener;
import com.zhaoxiaodan.miband.model.BatteryInfo;
import com.zhaoxiaodan.miband.model.LedColor;
import com.zhaoxiaodan.miband.model.UserInfo;
import com.zhaoxiaodan.miband.model.VibrationMode;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;


/**
 * Created by betomaluje on 6/26/15.
 */
public class MiBandService extends Service {
    private final String TAG = getClass().getSimpleName();
    String DEVICE_MAC = "";
    BluetoothDevice device = null;
    MiBand miband;
    String hDate;
    int hRate;
    Calendar glbTime;
    int glbTryHeartRateScan = 0;
    int glbMaxTryHeartRateScan = 5;


    // run on another Thread to avoid crash
    private Handler mHandler = new Handler();

    // timer handling
    private Timer mTimer = null;
    Intent intent1;

    //database
    DatabaseReference mData;

    //sharedPreferences
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;
    Integer INTERVAL_HEART;

    PowerManager pm;
    PowerManager.WakeLock wl;

    private String getDateTime() {
        // get date time in custom format
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date());
    }

    class TimeDisplayTimerTask extends TimerTask {
        @Override
        public void run() {
            // run on another thread
            mHandler.post(new Runnable() {

                @Override
                public void run() {
                    heartRateScan();
                }

            });
        }
    }

    public void heartRateScan() {
        miband.startHeartRateScan();

        if (DEVICE_MAC != "") {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            device = mBluetoothAdapter.getRemoteDevice(DEVICE_MAC);
        }
        if (device != null) {
            miband = new MiBand(MiBandService.this);
            miband.connect(device, new ActionCallback() {

                @Override
                public void onSuccess(Object data) {
                    UserInfo userInfo = new UserInfo(1673255957, 1, 37, 163, 77, "Vinh Nguyen", 0);
                    miband.setUserInfo(userInfo);
                    miband.setHeartRateScanListener(new HeartRateNotifyListener() {
                        @Override
                        public void onNotify(int heartRate) {
                            hDate = getDateTime();
                            hRate = heartRate;
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    //store database
                                    insertDataHR(hDate, hRate);
                                }
                            });
                        }
                    });

                }
                @Override
                public void onFail(int errorCode, String msg) {
                }
            });
        }
    }


    @Override
    public void onCreate() {
        super.onCreate();

        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakeLock");
        //Acquire the lock
        wl.acquire();

        intent1 = new Intent("sendData");
        hDate = getDateTime();
        hRate = 0;

        glbTime = Calendar.getInstance();

        // cancel if already existed
        if (mTimer != null) {
            mTimer.cancel();
        } else {
            // recreate new
            mTimer = new Timer();
        }

        //get Setting
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        INTERVAL_HEART = Integer.parseInt(sharedPreferences.getString("INTERVAL_HEART", "15"));
        DEVICE_MAC = sharedPreferences.getString("DEVICE_MAC", "");

        // schedule task
        mTimer.scheduleAtFixedRate(new TimeDisplayTimerTask(), 10000, INTERVAL_HEART * 60000);

        mData = FirebaseDatabase.getInstance().getReference();
        mData.child("DEVICE").child(DEVICE_MAC).child("HEART_RATE").limitToLast(1).addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                String hDate = dataSnapshot.getKey();
                int hRate = Integer.parseInt(dataSnapshot.getValue().toString());

                boolean ENABLE_ALERT = sharedPreferences.getBoolean("ENABLE_ALERT", true);
                Integer HEART_RATE_MIN = Integer.parseInt(sharedPreferences.getString("HEART_RATE_MIN", "0"));
                Integer HEART_RATE_MAX = Integer.parseInt(sharedPreferences.getString("HEART_RATE_MAX", "0"));
                boolean alert = false;

                if(hRate!=0 && ENABLE_ALERT){
                    if( hRate < HEART_RATE_MIN &&  HEART_RATE_MIN != 0){
                        alert = true;
                    }
                    if( hRate > HEART_RATE_MAX &&  HEART_RATE_MAX != 0){
                        alert = true;
                    }
                }

                //Define Notification Manager
                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

                //Define sound URI
                Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

                NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext())
                        .setSmallIcon(R.drawable.ic_heart)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText("Heart Rate = " + hRate);

                if(alert){
                    mBuilder.setSound(soundUri);
                }

                //Display notification
                notificationManager.notify(0, mBuilder.build());

            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        connectMiBand();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mTimer.cancel();
        //Release the lock
        wl.release();
        showMsg("Stop Heart Rate Scan");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private void connectMiBand() {
        showMsg("Start Heart Rate Scan");

        showMsg(DEVICE_MAC);

        if (DEVICE_MAC != "") {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            device = mBluetoothAdapter.getRemoteDevice(DEVICE_MAC);
        }

        if (device != null) {
            miband = new MiBand(MiBandService.this);
            miband.connect(device, new ActionCallback() {

                @Override
                public void onSuccess(Object data) {
                    miband.startVibration(VibrationMode.VIBRATION_WITH_LED);
                    UserInfo userInfo = new UserInfo(1673255957, 1, 37, 163, 77, "Vinh Nguyen", 0);
                    miband.setUserInfo(userInfo);
                    miband.setHeartRateScanListener(new HeartRateNotifyListener() {
                        @Override
                        public void onNotify(int heartRate) {
                            hDate = getDateTime();
                            hRate = heartRate;
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    //store database
                                    insertDataHR(hDate, hRate);
                                }
                            });
                        }
                    });

                }

                @Override
                public void onFail(int errorCode, String msg) {
                    showMsg("connection fail!!!");
                }
            });
        }
    }

    public void insertDataHR(String hDate, int hRate) {
        //show heart rate
        /*
        intent1.putExtra("hDate", hDate);
        intent1.putExtra("hRate", hRate);
        sendBroadcast(intent1);
        */

        //insert Heart Rate
        mData = FirebaseDatabase.getInstance().getReference();
        HeartRate heartRate = new HeartRate(hRate, hDate);
        //mData.child("DEVICE").child(DEVICE_MAC).child("HEART_RATE").push().setValue(hRate);
        mData.child("DEVICE").child(DEVICE_MAC).child("HEART_RATE").child(hDate).setValue(hRate);

        //-------notification----------

        Calendar now = Calendar.getInstance();
        long diff = now.getTimeInMillis() - glbTime.getTimeInMillis();

        if (hRate<=0) {
            glbTryHeartRateScan = glbTryHeartRateScan +1;
            if(glbTryHeartRateScan < glbMaxTryHeartRateScan){
                heartRateScan();
            }
        }

        if (diff > 9000 && hRate>0) {
            glbTryHeartRateScan = 0;
            glbTime = now;

            //call Phone
            boolean ENABLE_ALERT = sharedPreferences.getBoolean("ENABLE_ALERT", true);
            Integer HEART_RATE_MIN = Integer.parseInt(sharedPreferences.getString("HEART_RATE_MIN", "0"));
            Integer HEART_RATE_MAX = Integer.parseInt(sharedPreferences.getString("HEART_RATE_MAX", "0"));
            String  ALERT_CALL = sharedPreferences.getString("ALERT_CALL", "");
            String  ALERT_CALL2 = sharedPreferences.getString("ALERT_CALL2", "");
            String  ALERT_CALL3 = sharedPreferences.getString("ALERT_CALL3", "");
            String  ALERT_CALL4 = sharedPreferences.getString("ALERT_CALL4", "");
            String  ALERT_CALL5 = sharedPreferences.getString("ALERT_CALL5", "");

            boolean alert = false;
            String msg = "Heart Rate = " + hRate;
            if(hRate!=0 && ENABLE_ALERT){
                if( hRate < HEART_RATE_MIN &&  HEART_RATE_MIN != 0){
                    alert = true;
                }
                if( hRate > HEART_RATE_MAX &&  HEART_RATE_MAX != 0){
                    alert = true;
                }
            }


            //Define Notification Manager
            NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

            //Define sound URI
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext())
                    .setSmallIcon(R.drawable.ic_heart)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText("Heart Rate = " + hRate);

            if(alert){
                mBuilder.setSound(soundUri);
            }

            //Display notification
            notificationManager.notify(0, mBuilder.build());

            //alert call and msg
            if(alert){
                String ALERT_CALL_ALL =  ALERT_CALL +","+ ALERT_CALL2 +","+ ALERT_CALL3 +","+ ALERT_CALL4 +","+ ALERT_CALL5;
                mData.child("DEVICE").child(DEVICE_MAC).child("ALERT_CALL").child(hDate).setValue(ALERT_CALL_ALL);

                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    if(ALERT_CALL!=""){
                        mData.child("DEVICE").child(DEVICE_MAC).child("ALERT_MSG").child(hDate).setValue(ALERT_CALL);
                        sendSMS(ALERT_CALL,msg);
                    }
                    if(ALERT_CALL2!=""){
                        mData.child("DEVICE").child(DEVICE_MAC).child("ALERT_MSG").child(hDate).setValue(ALERT_CALL2);
                        SystemClock.sleep(10000);
                        sendSMS(ALERT_CALL2,msg);

                    }
                    if(ALERT_CALL3!=""){
                        mData.child("DEVICE").child(DEVICE_MAC).child("ALERT_MSG").child(hDate).setValue(ALERT_CALL3);
                        SystemClock.sleep(10000);
                        sendSMS(ALERT_CALL3,msg);
                    }
                    if(ALERT_CALL4!=""){
                        mData.child("DEVICE").child(DEVICE_MAC).child("ALERT_MSG").child(hDate).setValue(ALERT_CALL4);
                        SystemClock.sleep(10000);
                        sendSMS(ALERT_CALL4,msg);
                    }
                    if(ALERT_CALL5!=""){
                        mData.child("DEVICE").child(DEVICE_MAC).child("ALERT_MSG").child(hDate).setValue(ALERT_CALL5);
                        SystemClock.sleep(10000);
                        sendSMS(ALERT_CALL5,msg);
                    }
                }


                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    if (ALERT_CALL != "") {
                        mData.child("DEVICE").child(DEVICE_MAC).child("ALERT_CALL").child(hDate).setValue(ALERT_CALL);
                        SystemClock.sleep(10000);
                        call(ALERT_CALL);
                    }
                    if (ALERT_CALL2 != "") {
                        mData.child("DEVICE").child(DEVICE_MAC).child("ALERT_CALL").child(hDate).setValue(ALERT_CALL2);
                        SystemClock.sleep(90000);
                        call(ALERT_CALL2);

                    }
                    if (ALERT_CALL3 != "") {
                        mData.child("DEVICE").child(DEVICE_MAC).child("ALERT_CALL").child(hDate).setValue(ALERT_CALL3);
                        SystemClock.sleep(90000);
                        call(ALERT_CALL3);
                    }
                    if (ALERT_CALL4 != "") {
                        mData.child("DEVICE").child(DEVICE_MAC).child("ALERT_CALL").child(hDate).setValue(ALERT_CALL4);
                        SystemClock.sleep(90000);
                        call(ALERT_CALL4);
                    }
                    if (ALERT_CALL5 != "") {
                        mData.child("DEVICE").child(DEVICE_MAC).child("ALERT_CALL").child(hDate).setValue(ALERT_CALL5);
                        SystemClock.sleep(90000);
                        call(ALERT_CALL5);
                    }
                }

            }

        }
    }

    private void showMsg(String msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
    }

    private void call(String number) {
        if(number!=""){
            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse("tel:" + number));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            startActivity(intent);
        }
    }

    private void sendSMS(String phoneNo, String message) {
        if(phoneNo!="") {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            try {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(phoneNo, null, message, null, null);
                showMsg("SMS sent to " + phoneNo);
                Toast.makeText(getApplicationContext(), "", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                showMsg("SMS faild to " + phoneNo);
            }
        }
    }

}
