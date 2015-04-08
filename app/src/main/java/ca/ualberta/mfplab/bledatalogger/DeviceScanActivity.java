package ca.ualberta.mfplab.bledatalogger;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class DeviceScanActivity extends BaseActivity {
    private static final int REQUEST_ENABLE_BT = 1;

    private Handler handler = new Handler();

    private boolean scanning = false;

    public List<BluetoothDevice> bluetoothDevices;
    public HashMap<BluetoothDevice, Integer> deviceRSSIs;
    private DeviceScanResultsAdapter adapter;
    private BluetoothAdapter bluetoothAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // first make sure they can use BLE
        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE is not supported on your phone, sorry!", Toast.LENGTH_SHORT).show();
            finish();
        }

        // initialize the bluetooth adapter
        final BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // see if bluetooth is supported?
        if(bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on your phone, sorry!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // initialize the device list
        bluetoothDevices = new ArrayList<BluetoothDevice>();
        deviceRSSIs = new HashMap<BluetoothDevice, Integer>();
    }

    @Override protected int getLayoutResource() {
        return R.layout.activity_device_scan;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // make sure bluetooth is still enabled
        if(!bluetoothAdapter.isEnabled()) {
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBTIntent, REQUEST_ENABLE_BT);
        }

        // get the listview
        final ListView lv = (ListView)findViewById(R.id.scanResultsListView);
        // create an adapter for it
        adapter = new DeviceScanResultsAdapter(this);
        lv.setAdapter(adapter);

        // start scanning
        scanBTLEDevices(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanBTLEDevices(false);
        bluetoothDevices.clear();
        deviceRSSIs.clear();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void scanBTLEDevices(final boolean enable) {
        if(enable) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanning = false;

                    // filter explicitly for the UART
                    List<ScanFilter> filters = new ArrayList<ScanFilter>();
                    filters.add(new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")).build());

                    // setup the scan settings
                    ScanSettings settings = new ScanSettings.Builder().setReportDelay(0).setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();

                    bluetoothAdapter.getBluetoothLeScanner().startScan(filters, settings, scanCallback);
                }
            }, 5000);

            scanning = true;
            bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
        }
        else {
            scanning = false;
            bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
        }
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            deviceRSSIs.put(device, result.getRssi());
            if(!bluetoothDevices.contains(device)) {
                bluetoothDevices.add(device);
            }
            adapter.notifyDataSetChanged();
        }

        @Override
        public void onScanFailed(final int errorCode) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(DeviceScanActivity.this, "BTLE scan failed!", Toast.LENGTH_SHORT).show();
                    Log.d("BTLE", "Scan failed (" + errorCode + ")");
                    finish();
                    return;
                }
            });
        }
    };
}