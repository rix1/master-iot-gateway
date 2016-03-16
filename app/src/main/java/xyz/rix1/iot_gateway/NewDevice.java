package xyz.rix1.iot_gateway;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;
import xyz.rix1.iot_gateway.BLE.SampleGattAttributes;
import xyz.rix1.iot_gateway.helpers.DeviceAddedListener;
import xyz.rix1.iot_gateway.helpers.DisplayDeviceDialog;
import xyz.rix1.iot_gateway.helpers.Endpoint;

import java.util.ArrayList;
import java.util.List;

public class NewDevice extends AppCompatActivity implements OnItemSelectedListener, DeviceAddedListener{

    private static final boolean DEBUG = true;

    private final static String TAG = NewDevice.class.getSimpleName();
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final int REQUEST_ENABLE_BT = 1;

    private boolean deviceAdded, endpointAdded;
    private boolean mScanning;
    private boolean mConnected = false;

    private String mDeviceName;
    private String mDeviceAddress;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    private BluetoothLeService mBluetoothLeService;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private BluetoothAdapter mBluetoothAdapter;

    private ArrayList<BluetoothGattCharacteristic> mGattCharacteristics = new ArrayList<BluetoothGattCharacteristic>();
    private ArrayList<Endpoint> endpoints;
    private ArrayList<BluetoothDevice> deviceList;

    private Handler mHandler;
    private BluetoothDevice selectedDevice;
    private Endpoint selectedEndpoint;

    private final DisplayDeviceDialog deviceDialog = new DisplayDeviceDialog();
    private Spinner endpointSpinner;
    private Button activate, addDevice;
    private TextView helper;
    private TextView test;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_device);

        mHandler = new Handler();
        deviceAdded = endpointAdded = false;

        deviceList = new ArrayList<BluetoothDevice>();
        helper = (TextView) findViewById(R.id.txt_helper);

        initializeEndpoints();
        setupButtons();
        testForPermissions();

        Intent gattServiceIntent = new Intent(getActivity(), BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    private void initializeEndpoints(){
        endpoints = new ArrayList<Endpoint>();

        if(DEBUG){
            endpoints.add(new Endpoint("123.32.12.12", 1233, "St. Olavs"));
            endpoints.add(new Endpoint("123.32.12.12", 1233, "GE Moneybank"));
            endpoints.add(new Endpoint("123.32.12.12", 1233, "Google Health"));
            endpoints.add(new Endpoint("123.32.12.12", 1233, "Home Server"));
        }
        ArrayAdapter<Endpoint> adapter = new ArrayAdapter<Endpoint>(this, android.R.layout.simple_spinner_dropdown_item, endpoints);
        endpointSpinner = (Spinner) findViewById(R.id.endpoint_spinner);
        endpointSpinner.setAdapter(adapter);
        endpointSpinner.setOnItemSelectedListener(this);
    }

    private void setupButtons(){
        activate = (Button) findViewById(R.id.btn_activate);
        addDevice = (Button) findViewById(R.id.btn_addDevice);

        activate.setEnabled(false);
        activate.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                // TODO: 16/03/16 Finalize connection and return
            }
        });


        addDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mScanning){
                    stopSearch();
                }else{
                    startSearch();
                }
            }
        });
    }

    /**
     * Check if we have the appropriate permissions
     *
     * */

    public void testForPermissions(){

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("This app needs location access");
                    builder.setMessage("Please grant location access so this app can detect beacons.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        public void onDismiss(DialogInterface dialog) {
                            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                        }
                    });
                    builder.show();
                }
            }
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
    }


    public void startSearch(){
        scanLeDevice(true);
        launchDevicePicker();

        addDevice.setText("Stop scan...");
        helper.setVisibility(View.GONE);
    }

    public void stopSearch(){
        scanLeDevice(false);

        addDevice.setText("Scan");
        if(!connectToSelectedDevice()){
            helper.setVisibility(View.VISIBLE);
        }
    }

    private void scanFinished(){
    }

    public void resetSearch(){
        deviceList.clear();
    }

    public void launchDevicePicker(){
        FragmentManager manager = getFragmentManager();
        Fragment frag = manager.findFragmentByTag("test");

        Bundle bundle = new Bundle();
        bundle.putParcelableArrayList("devices", deviceList);
        deviceDialog.setArguments(bundle);

        if (frag != null) {
            manager.beginTransaction().remove(frag).commit();
        }
        deviceDialog.show(manager, "test");
        deviceAdded = true;
        checkCompleted();
    }

    public void checkCompleted(){
        activate.setEnabled(deviceAdded && endpointAdded);
    }

    public void setEndpoint(int pos) {
        this.selectedEndpoint = endpoints.get(pos);
        endpointAdded = (pos > 0);
        checkCompleted();
    }

    public void clearUI(){
        helper.setText("Disconnected...");
    }

    private void displayData(String stringExtra) {
        Log.d(TAG, "Received data: " + stringExtra);
        if(stringExtra!= null){
            test.setText(stringExtra);
            test.setVisibility(View.VISIBLE);
        }
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            scanFinished();
            Log.d(TAG, "Scan for devices stopped. Here is your results: " + deviceList.toString());
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    public boolean connectToSelectedDevice(){
        mDeviceName = selectedDevice.getName();
        mDeviceAddress = selectedDevice.getAddress();

        boolean found = false;
        boolean success = false;

        if (mGattCharacteristics != null) {
            if(mGattCharacteristics.size() > 0){
                BluetoothGattCharacteristic characteristic = null;        // TODO: 15/03/16 It only supports one characteristic
                for (BluetoothGattCharacteristic chara : mGattCharacteristics) {
                    if (chara.getUuid().toString().equals(SampleGattAttributes.HEART_RATE_MEASUREMENT)) {
                        characteristic = chara;
                        found = true;
                        Log.d(TAG, "Registering heart rate measurement samples");
                    }
                }
                if (!found) {
                    Log.d(TAG, "HR NOT found. Defaulting instead" );
                    characteristic = mGattCharacteristics.get(0);
                }
                final int charaProp = characteristic.getProperties();
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {

                    // If there is an active notification on a characteristic, clear
                    // it first so it doesn't update the data field on the user interface.
                    if (mNotifyCharacteristic != null) {
                        mBluetoothLeService.setCharacteristicNotification(mNotifyCharacteristic, false);
                        mNotifyCharacteristic = null;
                    }
                    mBluetoothLeService.readCharacteristic(characteristic);
                }
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    mNotifyCharacteristic = characteristic;
//                            Log.d(TAG, "Setting notification on: " + characteristic.getUuid().toString());
                    mBluetoothLeService.setCharacteristicNotification(characteristic, true);
                    success = true;
                }
            }else{

                Log.d(TAG, "WOPS, YOU CANNOT TEST FORDDI VI HAKK FUNNET NOEN CHARACTERISTICS");
            }
        }
        return (found && success); // TODO: 16/03/16 HUSK!
    }






//    ===================================== OVERRIDE-HELVETE FOLLOWS ======================================================






    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (mBluetoothAdapter!= null && !mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        // Initializes list view adapter.
//        mLeDeviceListAdapter = new LeDeviceListAdapter();
//        setListAdapter(mLeDeviceListAdapter);
//        scanLeDevice(true);

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        unregisterReceiver(mGattUpdateReceiver);
//        mLeDeviceListAdapter.clear();
    }

    @Override
    public void handleDialogClose(String msg) {
//        Log.d(TAG, "Dialog dismissed.....!");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_new_device, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        setEndpoint(position);
    }

    public void setDevice(int pos){
        this.selectedDevice = deviceList.get(pos);
        deviceAdded = true;
        Log.d(TAG, "Trying to connect to. " + selectedDevice.getAddress() + " : " + selectedDevice.getName());
        boolean connected = mBluetoothLeService.connect(selectedDevice.getAddress());
        Log.d(TAG, "Connection completed: " + connected);
        checkCompleted();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }


    public Context getActivity() {
        return getApplicationContext();
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if(!deviceList.contains(device)){
                                deviceList.add(device);
//                                deviceSpinner.setVisibility(View.VISIBLE);
//                                deviceAdapter.notifyDataSetChanged();
                                deviceDialog.update(deviceList);
                            }
                        }
                    });
                }
            };


    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
//                updateConnectionState(R.string.connected);
                Log.d(TAG, "CONNECTED TO GATT SERVICE");
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
//                updateConnectionState(R.string.disconnected);
                Log.d(TAG, "DISCONNECTED FROM GATT SERVICE");
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
//                mBluetoothLeService.getSupportedGattServices();
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    private void displayGattServices(List<BluetoothGattService> supportedGattServices) {

        if (supportedGattServices== null) return;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);

        mGattCharacteristics = new ArrayList<BluetoothGattCharacteristic>();

        List<BluetoothGattCharacteristic> gattCharacteristics;
        boolean found = false;

        for (BluetoothGattService gattService : supportedGattServices) {
//            Log.d("TEST", "looking up some shit");
            if (SampleGattAttributes.lookup(gattService.getUuid().toString())) {
                found = true;
//                Log.d(TAG, "Device compatible! Found service: (" + gattService.getUuid().toString() + ") " + SampleGattAttributes.lookup(gattService.getUuid().toString(), unknownServiceString));
                gattCharacteristics = gattService.getCharacteristics();
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
//                    Log.d("TEST", "looking up some OTHER shit");
                    if (SampleGattAttributes.lookup(gattCharacteristic.getUuid().toString())) {
//                        Log.d(TAG, "Found the correct characteristic: " + SampleGattAttributes.lookup(gattCharacteristic.getUuid().toString(), unknownCharaString));
                        mGattCharacteristics.add(gattCharacteristic);
                    }
                }
            }
        }
        if(!found){
            Log.d(TAG, "NO SUPPORTED SERVICES...");
        }
    }
}
