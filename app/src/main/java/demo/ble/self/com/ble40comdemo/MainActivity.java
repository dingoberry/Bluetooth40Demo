package demo.ble.self.com.ble40comdemo;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends Activity implements Handler.Callback, BlePairedAdapter.UnboundCallback {

    private static final int REQ_CODE = 0x111;

    private static final int MSG_LOAD_PAIRED = 0x1;
    private static final int MSG_DISCOVER_DEVICES = 0x2;
    private static final int MSG_SCAN_DEVICES = 0x3;
    private static final int MSG_DEVICE_LIST_UPDATE = 0x4;
    private static final int MSG_STOP_DISCOVERY = 0x5;

    private ListView mScannedList;
    private BleListAdapter mScannedAdapter;
    private BluetoothManager mBleManager;
    private BluetoothAdapter mBleAdapter;
    private ThreadHandler mTh;

    private ListView mPairedList;
    private BlePairedAdapter mPairedAdapter;

    private ArrayList<BluetoothDevice> mDeviceList;

    private Object mScanCallback;

    private BroadcastReceiver mReceiver;

    @SuppressWarnings("NullableProblems")
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (REQ_CODE == requestCode && null != grantResults) {
            for (int grantResult : grantResults) {
                if (PackageManager.PERMISSION_GRANTED != grantResult) {
                    ToastUtils.show(R.string.no_permission);
                    finish();
                    return;
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (REQ_CODE == requestCode && RESULT_OK != resultCode) {
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 开启设备服务
        startService(new Intent(this, DeviceService.class));

        checkPermissionIfNeed();
        mBleManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        if (null == mBleManager) {
            ToastUtils.show(R.string.not_support_ble);
            finish();
            return;
        }

        mBleAdapter = mBleManager.getAdapter();
        if (null == mBleAdapter) {
            ToastUtils.show(R.string.not_support_ble);
            finish();
            return;
        }

        if (!mBleAdapter.isEnabled()) {
            L.i("Enable bluetooth");

            if (!mBleAdapter.enable()) {
                Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(i, REQ_CODE);
            }
        }
        mDeviceList = new ArrayList<>();
        mScannedAdapter = new BleListAdapter();
        mScannedList = findViewById(R.id.ble_list);
        mScannedList.setAdapter(mScannedAdapter);
        mScannedList.setOnItemClickListener(mScannedAdapter);

        mTh = new ThreadHandler(this);
        mPairedList = findViewById(R.id.ble_paired_list);
        mPairedAdapter = new BlePairedAdapter(this, mTh);
        mPairedAdapter.setUnboundCallback(this);
        mPairedList.setAdapter(mPairedAdapter);

        mTh.getSubHandler().sendEmptyMessage(MSG_SCAN_DEVICES);
        mTh.getSubHandler().sendEmptyMessage(MSG_DISCOVER_DEVICES);
        mTh.getUiHandler().sendEmptyMessageDelayed(MSG_LOAD_PAIRED, 100);
        mTh.getUiHandler().sendEmptyMessageDelayed(MSG_DEVICE_LIST_UPDATE, 1000);
        mTh.getUiHandler().sendEmptyMessageDelayed(MSG_STOP_DISCOVERY, 5000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != mTh) {
            mTh.destroy();
        }

        stopDiscovery();
    }

    private void stopDiscovery() {
        if (null != mBleAdapter) {
            if (mBleAdapter.isDiscovering()) {
                mBleAdapter.cancelDiscovery();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mBleAdapter.getBluetoothLeScanner().stopScan((ScanCallback) mScanCallback);
            } else {
                mBleAdapter.stopLeScan((LeScanCallback) mScanCallback);
            }
        }
        if (null != mReceiver) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
    }

    private void checkPermissionIfNeed() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            ToastUtils.show(R.string.not_supported_to_4_0);
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        ArrayList<String> permissions = new ArrayList<>();
        if (PackageManager.PERMISSION_GRANTED != checkSelfPermission(Manifest.permission.BLUETOOTH)) {
            permissions.add(Manifest.permission.BLUETOOTH);
        }
        if (PackageManager.PERMISSION_GRANTED != checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN)) {
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        }
        if (PackageManager.PERMISSION_GRANTED != checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (PackageManager.PERMISSION_GRANTED != checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (permissions.isEmpty()) {
            return;
        }
        L.i("Request permission");
        requestPermissions(permissions.toArray(new String[permissions.size()]),
                REQ_CODE);
    }

    private void onBounded(BluetoothDevice device) {
        mTh.getUiHandler().sendEmptyMessage(MSG_LOAD_PAIRED);
        ArrayList<BluetoothDevice> devices = mDeviceList;
        if (null != devices) {
            for (BluetoothDevice d : devices) {
                if (d.getAddress().equals(device.getAddress())) {
                    devices.remove(d);
                    mTh.getUiHandler().sendEmptyMessage(MSG_DEVICE_LIST_UPDATE);
                    break;
                }
            }
        }
    }

    private void onFound(BluetoothDevice device) {
        L.i("onFound=" + device.getName());
        synchronized (mDeviceList) {
            for (BluetoothDevice d : mDeviceList) {
                if (d.getAddress().equals(device.getAddress())) {
                    return;
                }
            }
            mDeviceList.add(device);
        }
    }

    private void scanDevices() {
        L.i("scanDevices");

        // Android 5.0以上采用低功效耗蓝牙
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ScanCallback scanCallback = new ScanCallback() {
                @Override
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                public void onScanResult(int callbackType, ScanResult result) {
                    L.i("Call From Scanner");
                    onFound(result.getDevice());
                }

                @Override
                public void onScanFailed(int errorCode) {
                    L.i("onScanFailed errorCode=" + errorCode);
                }
            };
            mBleAdapter.getBluetoothLeScanner().startScan(scanCallback);
            L.i("scan with new API");
            mScanCallback = scanCallback;
        } else {
            LeScanCallback scanCallback = new LeScanCallback() {
                @Override
                public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
                    L.i("Call From Scanner");
                    onFound(device);
                }
            };
            mBleAdapter.startLeScan(scanCallback);
            L.i("scan with old API");
            mScanCallback = scanCallback;
        }
    }

    private void discoverDevice() {
        L.i("discoverDevice");
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                L.i(intent.toString());
                String action = intent.getAction();
                if (!TextUtils.isEmpty(action)) {
                    switch (action) {
                        case BluetoothDevice.ACTION_FOUND:
                            onFound((BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE));
                            break;
                        case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                            if (BluetoothDevice.BOND_BONDED
                                    == intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)) {
                                ToastUtils.show(R.string.bound_success);
                                onBounded((BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE));
                            }
                            for (String s : intent.getExtras().keySet()) {
                                L.i(s + ":" + intent.getExtras().get(s));
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filter.addAction(BluetoothDevice.ACTION_CLASS_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_UUID);
        filter.addAction(BluetoothDevice.ACTION_UUID);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
            filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        }
        registerReceiver(mReceiver, filter);
        mBleAdapter.startDiscovery();
    }

    private void print(Set<BluetoothDevice> list) {
        if (null == list) {
            L.i("empty");
            return;
        }
        for (BluetoothDevice b : list) {
            L.i("Print:" + mScannedAdapter.resolveDevice(b));
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_LOAD_PAIRED:
//                L.i("GATT");
//                print(mBleManager.getConnectedDevices(BluetoothProfile.GATT));
//                L.i("GATT_SERVER");
                Set<BluetoothDevice> pairedDevices = mBleAdapter.getBondedDevices();
                print(pairedDevices);
                mPairedAdapter.update(new ArrayList<>(pairedDevices));
                break;
            case MSG_DISCOVER_DEVICES:
                discoverDevice();
                break;
            case MSG_SCAN_DEVICES:
                scanDevices();
                break;
            case MSG_DEVICE_LIST_UPDATE:
                ArrayList<BluetoothDevice> deviceList;
                synchronized (mDeviceList) {
                    deviceList = new ArrayList<>(mDeviceList);
                }
                mScannedAdapter.update(deviceList);
                mScannedAdapter.notifyDataSetChanged();
                if (isFinishing()) {
                    break;
                }
                mTh.getUiHandler().sendEmptyMessageDelayed(MSG_DEVICE_LIST_UPDATE, 1000);
                break;
            case MSG_STOP_DISCOVERY:
                stopDiscovery();
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public void unBound(boolean success) {
        if (success) {
            mTh.getUiHandler().sendEmptyMessageDelayed(MSG_LOAD_PAIRED, 200);
        }
    }

    private class BleListAdapter extends BaseAdapter implements AdapterView.OnItemClickListener {

        private ArrayList<BluetoothDevice> mDeviceList = new ArrayList<>();

        void update(ArrayList<BluetoothDevice> deviceList) {
            mDeviceList = deviceList;
        }

        @Override
        public int getCount() {
            return mDeviceList.size();
        }

        @Override
        public Object getItem(int position) {
            return mDeviceList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (null == convertView) {
                convertView = getLayoutInflater().inflate(R.layout.ble_item, parent, false);
            }
            TextView info = convertView.findViewById(R.id.ble_item_info);
            info.setText(resolveDevice(mDeviceList.get(position)));
            return convertView;
        }

        private String resolveDevice(BluetoothDevice device) {
            String name = device.getName();
            return TextUtils.isEmpty(name) ? device.getAddress() : name;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
                BluetoothDevice device = (BluetoothDevice) getItem(position);
                if (device.createBond()) {
                    ToastUtils.show(R.string.start_to_bound);
                }
            } else {
                ToastUtils.show(R.string.unsupported_pairing);
            }
        }
    }
}
