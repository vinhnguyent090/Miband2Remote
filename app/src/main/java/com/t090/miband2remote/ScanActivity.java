package com.t090.miband2remote;


        import android.app.Activity;
        import android.bluetooth.BluetoothDevice;
        import android.bluetooth.le.ScanCallback;
        import android.bluetooth.le.ScanResult;
        import android.content.Intent;
        import android.os.Bundle;
        import android.util.Log;
        import android.view.View;
        import android.widget.AdapterView;
        import android.widget.AdapterView.OnItemClickListener;
        import android.widget.ArrayAdapter;
        import android.widget.Button;
        import android.widget.ListView;
        import android.widget.TextView;

        import com.zhaoxiaodan.miband.MiBand;

        import java.util.ArrayList;
        import java.util.HashMap;

public class ScanActivity extends Activity {
    private static final String TAG = "==[mibandtest]==";
    private MiBand miband;
    private TextView logView;


    HashMap<String, BluetoothDevice> devices = new HashMap<String, BluetoothDevice>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scan);

        this.logView = (TextView) findViewById(R.id.textView1);

        miband = new MiBand(this);

        final ArrayAdapter adapter = new ArrayAdapter<String>(this, R.layout.item, new ArrayList<String>());

        final ScanCallback scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();

                logView.setText("scanCallback");

                String item = device.getName() + "|" + device.getAddress();
                if (!devices.containsKey(item)) {
                    devices.put(item, device);
                    adapter.add(item);
                }

            }
        };


        ((Button) findViewById(R.id.starScanButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logView.setText("start");
                MiBand.startScan(scanCallback);
            }
        });

        ((Button) findViewById(R.id.stopScanButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logView.setText("stop");
                MiBand.stopScan(scanCallback);
            }
        });


        ListView lv = (ListView) findViewById(R.id.listView);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String item = ((TextView) view).getText().toString();
                if (devices.containsKey(item)) {

                    MiBand.stopScan(scanCallback);

                    BluetoothDevice device = devices.get(item);
                    Intent intent = new Intent();
                    intent.putExtra("device", device);
                    intent.setClass(ScanActivity.this, MainActivity.class);
                    ScanActivity.this.startActivity(intent);
                    ScanActivity.this.finish();
                }
            }
        });

    }
}
