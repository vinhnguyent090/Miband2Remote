package com.t090.miband2remote;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import com.google.firebase.database.ValueEventListener;
import com.zhaoxiaodan.miband.ActionCallback;
import com.zhaoxiaodan.miband.MiBand;
import com.zhaoxiaodan.miband.listeners.HeartRateNotifyListener;
import com.zhaoxiaodan.miband.model.UserInfo;
import com.zhaoxiaodan.miband.model.VibrationMode;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class MainActivity extends AppCompatActivity {

    private final static int REQUEST_ENABLE_BT = 1;
    private TextView textView1;
    private TextView textView2;
    private TextView logView;
    private ListView lv;
    Button btnStartService;
    Button btnStopService;
    Button btnSignOut;
    Button btnSettings;

    SQLiteDatabase database;
    DatabaseReference mData;
    DatabaseReference mDataUser;
    AdapterHeartRate adapter;
    ArrayList<HeartRate> arrayHeartRate;

    RefreshBroadcastReciver mBroadCastReciver;

    private static final String TAG = "==[mibandtest]==";
    private static final int Message_What_ShowLog = 1;
    private String address;
    private MiBand miband;
    BluetoothDevice device;

    //login
    private FirebaseAuth.AuthStateListener authListener;
    private FirebaseAuth auth;
    public  String userId;

    //call

    //sharedPreferences
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;
    String INTERVAL_HEART = "INTERVAL_HEART";


    private Handler handler = new Handler(Looper.getMainLooper()) {
        public void handleMessage(Message m) {
            switch (m.what) {
                case Message_What_ShowLog:
                    String text = (String) m.obj;
                    logView.setText(text);
                    break;
            }
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        //get firebase auth instance
        auth = FirebaseAuth.getInstance();
        userId = "";

        //get current user
        final FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        // user auth state is changed - user is null
        // launch login activity
        if (user == null) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        }else{
            userId = user.getUid();
        }

        authListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user == null) {
                    // user auth state is changed - user is null
                    // launch login activity
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    finish();
                }
            }
        };

        lv = (ListView) findViewById(R.id.listView);
        logView = (TextView) findViewById(R.id.textView1);
        textView1 = (TextView) findViewById(R.id.textView1);
        textView2 = (TextView) findViewById(R.id.textView2);
        btnStartService = (Button) findViewById(R.id.btnStartService);
        btnStopService = (Button) findViewById(R.id.btnStopService);
        btnSignOut = (Button) findViewById(R.id.btnSignOut);
        btnSettings = (Button) findViewById(R.id.btnSettings);

        //Store setting
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        editor = sharedPreferences.edit();
        /*
        editTextInerval.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {
               if(s.toString()!=""){
                  // editor.putString(INTERVAL_HEART, s.toString());
                 //  editor.commit();
                //   showMsg(s.toString());
               }
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        //Load Setting
        editTextDevice.setText(sharedPreferences.getString("DEVICE_MAC",""));
        editTextInerval.setText(sharedPreferences.getString("INTERVAL_HEART",""));
        editTextCall.setText(sharedPreferences.getString("ALERT_CALL",""));
        editTextMsg.setText(sharedPreferences.getString("ALERT_SMS",""));
        */

        //database
        arrayHeartRate = new ArrayList<HeartRate>();
        adapter = new AdapterHeartRate(this, arrayHeartRate);
        lv.setAdapter(adapter);
        arrayHeartRate.clear();

        logView.setText("Welcome");

        // Write a message to the database
        mData = FirebaseDatabase.getInstance().getReference();
        mDataUser = mData.child("USER").child(userId);


        //Miband
        address = sharedPreferences.getString("DEVICE_MAC","");

        if(address==""){
            mData.child("USER").child(userId).child("DEVICE").addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    address = dataSnapshot.getValue().toString();
                    if(address!=""){
                        editor.putString("DEVICE_MAC", address);
                        editor.commit();
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    System.out.println("The read failed: " + databaseError.getCode());
                }
            });

        }

        if(address==""){
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            finish();
        }else{
            //Write user info
            mData.child("USER").child(userId).child("DEVICE").setValue(address);


            mData.child("DEVICE").child(address).child("HEART_RATE").limitToLast(1).addChildEventListener(new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    String hDate = dataSnapshot.getKey();
                    int hRate = Integer.parseInt(dataSnapshot.getValue().toString());
                    showHeartRate(hRate, hDate);

                    // show detail
                    //HeartRate heartRate = dataSnapshot.getValue(HeartRate.class);
                    //showHeartRate(heartRate.HR, heartRate.TIME);
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

            mData.child("DEVICE").child(address).child("HEART_RATE").orderByKey().limitToLast(10).addChildEventListener(new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    String hDate = dataSnapshot.getKey();
                    int hRate = Integer.parseInt(dataSnapshot.getValue().toString());
                    arrayHeartRate.add(new HeartRate(hRate, hDate));
                    adapter.notifyDataSetChanged();

                    /*vinh
                    HeartRate heartRate = dataSnapshot.getValue(HeartRate.class);
                    arrayHeartRate.add(new HeartRate(heartRate.HR, heartRate.TIME));
                    //Collections.reverse(arrayHeartRate);
                    adapter.notifyDataSetChanged();
                    */

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

        //check can call
        checkPermissionCall();
        checkPermissionSms();


        btnStartService.setOnClickListener( new View.OnClickListener() {

            @Override
            public void onClick(View v) {
            if(device!=null){
                miband.connect(device, new ActionCallback() {

                    @Override
                    public void onSuccess(Object data) {

                    }
                    @Override
                    public void onFail(int errorCode, String msg) {
                        logView.setText("connection fail!!!");
                    }
                });
            }

            Intent intentService = new Intent(MainActivity.this,MiBandService.class);
            startService(intentService);
            }
        });

        btnStopService.setOnClickListener( new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent intentService = new Intent(MainActivity.this,MiBandService.class);
                stopService(intentService);
            }
        });


        btnSignOut.setOnClickListener( new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                signOut();
            }
        });

        btnSettings.setOnClickListener( new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

    }

    private static final int PERMISSIONS_REQUEST_PHONE_CALL = 0x2;
    private static String[] PERMISSIONS_PHONECALL = {android.Manifest.permission.CALL_PHONE};
    private void checkPermissionCall() {
        // Check the SDK version and whether the permission is already granted or not.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CALL_PHONE}, PERMISSIONS_REQUEST_PHONE_CALL);
        } else {
            //Open call function
            Toast.makeText(this, "App can call your family", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_PHONE_CALL) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission is granted
                checkPermissionCall();
            } else {
                Toast.makeText(this, "Sorry!!! Call Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == PERMISSION_SEND_SMS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission is granted
                checkPermissionSms();
            } else {
                Toast.makeText(this, "Sorry!!! Send Msg Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static final int PERMISSION_SEND_SMS = 123;
    private void checkPermissionSms() {

        // check permission is given
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.SEND_SMS}, PERMISSION_SEND_SMS);
        } else {
            // permission already granted run sms send
            Toast.makeText(this, "App can sms to  your family", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    public void onStart() {
        super.onStart();
        auth.addAuthStateListener(authListener);

        //get again configure
        address = sharedPreferences.getString("DEVICE_MAC","");
        showMsg(address);

        //try to connect miband2
        miband = new MiBand(MainActivity.this);

        if(address!=""){

            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            device = null;

            if (mBluetoothAdapter == null) {
                showMsg("Device does not support Bluetooth");
            } else {
                if (!mBluetoothAdapter.isEnabled()) {
                    showMsg("Bluetooth is not enable");
                }else{
                    try{
                        device = mBluetoothAdapter.getRemoteDevice(address);

                    } catch (IllegalArgumentException e) {
                        showMsg("Miband2 not connect your mobile.");
                    }
                }
            }

        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (authListener != null) {
            auth.removeAuthStateListener(authListener);
        }

        if(mBroadCastReciver!=null)
            unregisterReceiver(mBroadCastReciver);

    }

    protected void onResume() {
        super.onResume();
        mBroadCastReciver = new RefreshBroadcastReciver();
        registerReceiver(mBroadCastReciver, new IntentFilter("sendData"));

        //load again Service
    }
    private class RefreshBroadcastReciver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            //textView1.setText(intent.getIntExtra("hRate",0) + "");
            //textView2.setText(intent.getStringExtra("hDate"));
        }
    }

    public void showHeartRate(int hRate, String hDate){
        textView1.setText(hRate+"");
        textView2.setText(hDate);
    }

    private void showMsg(String msg) {

        Toast.makeText(MainActivity.this,msg, Toast.LENGTH_SHORT).show();
    }

    public void signOut() {
        auth.signOut();
    }

}
