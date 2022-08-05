package org.techtown.bluetooth;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private final static int REQUEST_ENABLED_BT = 101;
    BluetoothAdapter btAdapter;
    Set<BluetoothDevice> pairedDevices;
    ArrayList<String> devicePairedArrayList;
    ArrayList<String> devicePairedNameL;
    ArrayList<String> deviceLocalArrayList;
    ArrayList<String> deviceLocalNameL;
    BluetoothSocket btSocket;
    BluetoothDevice device;
    ConnectedThread connectedThread;
    TextView btStatus;
    TextView numPaired;
    TextView numLocal;
    TextView deviceList;
    Button btLED;
    boolean flag;

    private static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //권한 요청
        String[] permissionList = {
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
        };
        ActivityCompat.requestPermissions(this, permissionList, 101);

        btStatus = findViewById(R.id.btStatus);
        numLocal = findViewById(R.id.numLocal);
        numPaired = findViewById(R.id.numPaired);
        deviceList = findViewById(R.id.deviceList);
        deviceList.append("\n");
        btLED = findViewById(R.id.btLED);
        btLED.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //블루투스 활성화
                btAdapter = BluetoothAdapter.getDefaultAdapter();
                if (!btAdapter.isEnabled()) {
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT)
                            != PackageManager.PERMISSION_GRANTED) {
                        btStatus.setText("상태: 블루투스 권한 없음.");
                        return;
                    }
                    startActivityForResult(enableIntent, REQUEST_ENABLED_BT);
                }

                //페어링된 디바이스 목록
                devicePairedArrayList = new ArrayList<>();
                devicePairedNameL = new ArrayList<>();
                if (devicePairedArrayList != null && devicePairedArrayList.isEmpty()) {
                    devicePairedArrayList.clear();
                }
                pairedDevices = btAdapter.getBondedDevices();
                if (pairedDevices.size() > 0) {
                    for (BluetoothDevice device : pairedDevices) {
                        String deviceName = device.getName();
                        String deviceHardwareAddress = device.getAddress();
                        devicePairedArrayList.add(deviceHardwareAddress);
                        devicePairedNameL.add(deviceName);
                    }
                }

                //주변 디바이스 목록
                deviceLocalArrayList = new ArrayList<>();
                deviceLocalNameL = new ArrayList<>();
                if (btAdapter.isDiscovering()) {
                    btAdapter.cancelDiscovery();
                } else {
                    if (btAdapter.isEnabled()) {
                        btAdapter.startDiscovery();
                        if (deviceLocalArrayList != null && !deviceLocalArrayList.isEmpty()) {
                            deviceLocalArrayList.clear();
                        }
                        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                        registerReceiver(receiver, filter);
                    } else {
                        Toast.makeText(getApplicationContext(), "블루투스가 켜지지 않았습니다.",
                                Toast.LENGTH_SHORT).show();
                    }
                }

                //블루투스 통신
                //기기 주소 가져오기
                String faceDeviceAddress = "";
                int pairedLength = devicePairedArrayList.size();
                int pL = devicePairedNameL.size();
                numPaired.setText("페어링 개수: " + Integer.toString(pL));
                for (int i = 0; i < pairedLength; i++) {
                    deviceList.append(devicePairedNameL.get(i)+"\n");
                    if (devicePairedNameL.get(i).equals(" faceArduino")) {
                        faceDeviceAddress = devicePairedArrayList.get(i);
                        break;
                    }
                }
                int localLength = deviceLocalArrayList.size();
                numLocal.setText("로컬 개수: " + Integer.toString(localLength));
                for (int i = 0; i < localLength; i++) {
                    if (deviceLocalNameL.get(i).equals(" faceArduino")) {
                        faceDeviceAddress = deviceLocalArrayList.get(i);
                        break;
                    }
                }
                try {
                    device = btAdapter.getRemoteDevice(faceDeviceAddress);
                    flag = true;
                } catch (Exception e) {
                    flag = false;
                    btStatus.setText("상태: 기기 없음.");
                }


                //소켓 생성 및 연결
                try {
                    btSocket = createBluetoothSocket(device);
                    if (btSocket != null) {
                        btSocket.connect();
                    }
                } catch (IOException e) {
                    flag = false;
                    btStatus.setText("상태: 연결 실패");
                    e.printStackTrace();
                }

                if (flag) {
                    btStatus.setText("상태: 연결되었음.");
                    connectedThread = new ConnectedThread(btSocket);
                    connectedThread.start();
                }

                if (connectedThread != null) {
                    connectedThread.write("1");
                }

            }
        });


    }

    //ACTION_FOUND를 위한 브로드캐스트 리시버
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress();
                deviceLocalArrayList.add(deviceHardwareAddress);
                deviceLocalNameL.add(deviceName);
            }
        }
    };

    //receiver 등록 해제
    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(receiver);
    }

    //createBluetoothSocket 메서드
    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        if (Build.VERSION.SDK_INT >= 10) {
            try {
                final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[]{UUID.class});
                return (BluetoothSocket) m.invoke(device, uuid);
            } catch (Exception e) {

            }
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        try {
            return device.createRfcommSocketToServiceRecord(uuid);
        } catch (Exception e) {
            return null;
        }
    }
}