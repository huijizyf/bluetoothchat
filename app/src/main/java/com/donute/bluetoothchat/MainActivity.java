package com.donute.bluetoothchat;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import butterknife.Bind;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemClickListener {
    /**
     * ******************************************控件定义******************************************
     */
    @Bind(R.id.btn_open_bluetooth_intent)
    Button btnOpenBluetoothIntent;
    @Bind(R.id.btn_open_bluetooth_silence)
    Button getBtnOpenBluetoothSilence;
    @Bind(R.id.lv_blue_devices_list)
    ListView lvDevicesList;
    @Bind(R.id.tv_data_received)
    TextView tvDataReceived;
    @Bind(R.id.et_data_to_send)
    EditText etDataToSend;
    @Bind(R.id.btn_send_data)
    Button btnSendData;
    @Bind(R.id.btn_close_bluetooth)
    Button btnCloseBluetooth;
    @Bind(R.id.btn_search_devices)
    Button btnSearchDevices;
    @Bind(R.id.btn_open_bluetooth_service)
    Button btnOpenService;
    @Bind(R.id.btn_bluetooth_disconnect)
    Button btnDisconnect;

    /**
     ******************************************变量声明*********************************************
     */
    private BluetoothAdapter mAdapter;               //蓝牙适配器
    private static final int REQUEST_CODE=1001;      //请求码，自定义整数即可
    private BluetoothServerSocket serverSocket;       //服务端连接套接字
    private BluetoothSocket clienSocket;               //客户端套接字
    private List<BluetoothDevice> deviceList;       //扫描接收到的蓝牙设备
    private DeviceViewAdapter viewAdapter;           //ListView的数据适配器
    private BluetoothSocket socket;                 //服务端套接字
    private static final UUID uuid=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private int type=0;               //当前连接类型   1为服务端    2为客户端
    private DateFormat format;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        init();
    }

    /**
     * 初始化
     */
    private void init() {
        //设置TextView允许滚动
        tvDataReceived.setMovementMethod(ScrollingMovementMethod.getInstance());
        //获取蓝牙适配器
        mAdapter= BluetoothAdapter.getDefaultAdapter();
        if (mAdapter==null){
            TipManager.showDialog(this,"提示","当前设备不支持蓝牙");
        }
        //日期字符串格式化
        format=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        //将已经绑定的设备加入列表中
        deviceList=new ArrayList<>(mAdapter.getBondedDevices());
        viewAdapter=new DeviceViewAdapter(this,deviceList);
        //给ListView设置数据适配器
        lvDevicesList.setAdapter(viewAdapter);
        //设置ListView点击事件处理
        lvDevicesList.setOnItemClickListener(this);

        //注册广播接收，会接收到开始扫描，扫描到设备，介绍扫描 三个事件，事件交由mReceiver处理

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mReceiver, filter);

        //等待进度Dialog
        progressDialog=new ProgressDialog(this);
        progressDialog.setTitle("请稍等");
        progressDialog.setMessage("正在扫描蓝牙设备，请稍等...");
        progressDialog.setCancelable(false);

        //设置点击事件处理
        btnOpenBluetoothIntent.setOnClickListener(this);
        getBtnOpenBluetoothSilence.setOnClickListener(this);
        btnSendData.setOnClickListener(this);
        btnCloseBluetooth.setOnClickListener(this);
        btnSearchDevices.setOnClickListener(this);
        btnOpenService.setOnClickListener(this);
        btnDisconnect.setOnClickListener(this);
    }

    /**
     * 点击事件处理
     * @param v
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_open_bluetooth_intent:
                Intent enabler = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enabler, REQUEST_CODE);      //调用系统弹框开启蓝牙
                break;
            case R.id.btn_open_bluetooth_silence:
                mAdapter.enable();//静默方式开启蓝牙
                break;
            case R.id.btn_send_data:
                sendData();     //发送数据
                break;
            case R.id.btn_close_bluetooth:
                mAdapter.disable(); //关闭蓝牙
                break;
            case R.id.btn_search_devices:
                if (mAdapter.isDiscovering()){  //开始扫描蓝牙，如果已经正在扫描了，则不继续执行
                    return;
                }
                deviceList.clear();
                mAdapter.startDiscovery();
                break;
            case R.id.btn_open_bluetooth_service:
                startServer("my-service");      //开启服务端
                break;
            case R.id.btn_bluetooth_disconnect:
                disconnect();                   //断开连接
                break;
        }
    }

    private void disconnect() {
        //根据当前类型断开连接
        switch (type){
            case 1:     //作为服务端
                try {
                    if (socket.isConnected())
                        socket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case 2:     //作为客户端
                try {
                    if (clienSocket.isConnected())
                        clienSocket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
        }
    }

    /**
     * 发送数据
     */
    private void sendData(){
        Thread sendThread=new Thread(){
            @Override
            public void run() {
                super.run();
                final String msg=etDataToSend.getText().toString();
                if (TextUtils.isEmpty(msg)){
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TipManager.showDialog(MainActivity.this,"提示","内容为空，不能发送数据");
                        }
                    });
                    return;
                }
                OutputStream os = null;
                try {
                    switch (type){
                        case 1:
                            if (socket!=null)
                                os=socket.getOutputStream();
                            break;
                        case 2:
                            if (clienSocket!=null)
                                 os=clienSocket.getOutputStream();
                            break;
                        default:
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    TipManager.showDialog(MainActivity.this,"提示","还未建立连接，或者连接错误，不能发送数据");
                                }
                            });
                            break;
                    }
                    if (os != null) {
                        //向输出流写入数据
                        os.write(msg.getBytes("utf-8"));
                        tvDataReceived.post(new Runnable() {
                            @Override
                            public void run() {
                                //在UI线程操作界面，将发送的消息显示到界面，同时清空消息输入框
                                String m=tvDataReceived.getText().toString();
                                m=m+format.format(new Date())+"\t"+"发送："+msg+"\n";
                                tvDataReceived.setText(m);
                                etDataToSend.setText("");
                            }
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        sendThread.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //界面销毁时解除广播接收器的注册
        unregisterReceiver(mReceiver);
    }

    //定义广播接收器，接收与蓝牙相关的系统广播
    BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //找到设备
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState()==BluetoothDevice.BOND_NONE){
                    deviceList.add(device);
                    progressDialog.setMessage("正在扫描，已经扫描到设备数量："+deviceList.size());
                    viewAdapter.notifyDataSetChanged();
                }
            }
            //扫描完成
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                progressDialog.dismiss();
                TipManager.showDialog(MainActivity.this,"提示","蓝牙设备扫描完成，总共扫描到蓝牙设备数量："+deviceList.size());
                //开始扫描
            }else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)){
                TipManager.showToast(MainActivity.this,"开始扫描设备");
                progressDialog.show();
            }
        }
    };

    /**
     * 开启服务端
     */
    private void startServer(final String name){
        Thread serverThread=new Thread(){
            @Override
            public void run() {
                super.run();
                try {
                    type=1;
                    serverSocket = mAdapter. listenUsingRfcommWithServiceRecord(name,uuid);
                    socket=serverSocket.accept();      //等待服务端连接，accept函数为阻塞函数，会等待到有客户端连接成功后才会继续执行
                    InputStream is = socket.getInputStream();//客户端连接成功，获取输入流
                    while(true) {           //从输入流读取数据
                        byte[] buffer =new byte[1024];
                        int count = is.read(buffer);
                        final String s= new String(buffer, 0, count, "utf-8");
                        tvDataReceived.post(new Runnable() {
                            @Override
                            public void run() {
                                //在UI线程操作界面，将接收到的消息显示到界面
                                String msg=tvDataReceived.getText().toString();
                                msg=msg+format.format(new Date())+"\t"+"接收："+s+"\n";
                                tvDataReceived.setText(msg);
                            }
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        serverThread.start();
    }
    /**
     * 连接到服务端
     */
    private void connectServer(final BluetoothDevice device){
        final ProgressDialog dialog=new ProgressDialog(this);
        dialog.setTitle("请稍等");
        dialog.setMessage("正在连接");
        Thread connectThread=new Thread(){
            public void run(){
                super.run();
                try {
                    type=2;
                    clienSocket=device.createRfcommSocketToServiceRecord(uuid);
                    clienSocket.connect();
                    lvDevicesList.post(new Runnable() {
                        @Override
                        public void run() {
                            dialog.dismiss();
                            TipManager.showDialog(MainActivity.this,"提示","连接成功，现在可以发送数据了");
                            startReceive();//连接到服务端成功，开启接收线程
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        dialog.show();
        connectThread.start();
    }
    private void startReceive(){
        Thread receiveThread=new Thread(){
            @Override
            public void run() {
                super.run();
                try {
                    InputStream is=clienSocket.getInputStream();
                    if (is!=null){
                        while(true) {
                            byte[] buffer =new byte[1024];
                            int count = is.read(buffer);
                            final String s= new String(buffer, 0, count, "utf-8");
                            tvDataReceived.post(new Runnable() {
                                @Override
                                public void run() {
                                    //在UI线程操作界面，将接收到的消息显示到界面
                                    String msg=tvDataReceived.getText().toString();
                                    msg=msg+format.format(new Date())+"\t"+"接收："+s+"\n";
                                    tvDataReceived.setText(msg);
                                }
                            });
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        receiveThread.start();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        BluetoothDevice device= (BluetoothDevice) viewAdapter.getItem(position);
        connectServer(device);
    }
}
