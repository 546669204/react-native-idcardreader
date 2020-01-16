package com.xiaoming.rn.idcardreader;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.ArrayUtils;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.zkteco.android.IDReader.IDPhotoHelper;
import com.zkteco.android.IDReader.WLTService;
import com.zkteco.id3xx.IDCardReader;
import com.zkteco.id3xx.meta.IDCardInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class RNIDCardReader3xxModule extends ReactContextBaseJavaModule implements LifecycleEventListener {

    private final ReactApplicationContext reactContext;
    private IDCardReader idCardReader = null;

    private boolean bStoped = true;

    private ArrayList list = new ArrayList();
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothSocket mBluetoothSocket;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            // TODO Auto-generated method stub
            String action = arg1.getAction();
            if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                BluetoothDevice device = arg1.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.e("Name:" + device.getName().toString(), "MAC:" + device.getAddress());
                String te = device.getName() + "|" + device.getAddress();

                if (!list.contains(te)) {
                    list.add(te);
                }
            } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                // setProgressBarIndeterminateVisibility(false);
                WritableMap params = Arguments.createMap();
                params.putInt("code",0);
                params.putArray("data",Arguments.fromArray(list));
                sendEvent(reactContext, "callbackBySearch", params);

            }
        }
    };

    public RNIDCardReader3xxModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        reactContext.addLifecycleEventListener(this);

        init();
    }

    @Override
    public void onHostResume() {
        // Activity `onResume`
    }

    @Override
    public void onHostPause() {
        // Activity `onPause`
    }

    @Override
    public void onHostDestroy() {
        // Activity `onDestroy`
        reactContext.unregisterReceiver(mReceiver);
    }

    private void sendEvent(ReactApplicationContext reactContext,
                           String eventName,
                           @Nullable WritableMap params) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }


    private void init() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // if (!mBluetoothAdapter.isEnabled()) {
        //     mBluetoothAdapter.enable();
        // }
        IntentFilter mFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        reactContext.registerReceiver(mReceiver, mFilter);
        // 注册搜索完时的receiver
        mFilter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        reactContext.registerReceiver(mReceiver, mFilter);
    }

    @ReactMethod
    public void getDevices(Promise promise) {
        if (!mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
        }
        Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();
        WritableArray array = Arguments.createArray();
        for (BluetoothDevice bluetoothDevice : bondedDevices)
        {
            WritableMap map = Arguments.createMap();
            map.putString("name",bluetoothDevice.getName());
            map.putString("mac",bluetoothDevice.getAddress());
            map.putInt("state",bluetoothDevice.getBondState());
//            map.putString("uuid",bluetoothDevice.getUuids()[0].toString());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                map.putInt("type",bluetoothDevice.getType());
            }
            array.pushMap(map);
        }
        promise.resolve(array);
    }

    @ReactMethod
    public void search() {
        if (!mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
        }
        list = new ArrayList<>();
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        mBluetoothAdapter.startDiscovery();
    }

    @ReactMethod
    public void connect(String mac, Promise promise) {
        if (!mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
        }
        stop();
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        if (idCardReader != null) {
            idCardReader.closeDevice();
        }

        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mac);
        try {
            if(mBluetoothSocket!=null ){
                mBluetoothSocket.close();
                mBluetoothSocket = null;
                Thread.sleep(500);
            }
            idCardReader = new IDCardReader();

            mBluetoothSocket = device.createRfcommSocketToServiceRecord(IDCardReader.myuuid);
            mBluetoothSocket.connect();
            InputStream mInputStream = mBluetoothSocket.getInputStream();
            OutputStream mOutputStream = mBluetoothSocket.getOutputStream();
            idCardReader.init(mInputStream, mOutputStream);
            promise.resolve(true);
        } catch (Exception e) {
            // TODO: handle exception
            Log.e("xm 3xxx",e.toString());
            e.printStackTrace();
            idCardReader = null;
            try {
                mBluetoothSocket.close();
            } catch (IOException e2) {}
            promise.reject(e);
        }
    }

    @ReactMethod
    public void start() {
        if(bStoped == false)return;
        bStoped = false;
        new Thread(new Runnable() {
            public void run() {
                while (!bStoped) {
                    try {
                        Thread.sleep(500);
                        Log.d("xm 3xxx","starting");
                        if (bStoped||!idCardReader.sdtFindCard()) {
                            continue;
                        } else {
                            if (!idCardReader.sdtSelectCard()) {
                                continue;
                            }
                        }
                        final IDCardInfo idCardInfo = new IDCardInfo();
                        if (!idCardReader.sdtReadCard(1, idCardInfo)) {
                            continue;
                        }

                        WritableMap params = Arguments.createMap();
                        params.putDouble("timer", 0);
                        params.putInt("retType", 0);

                        params.putString("name", idCardInfo.getName());//姓名
                        params.putString("nation", idCardInfo.getNation());//民族,港澳台不支持该项
                        params.putString("born", idCardInfo.getBirth());//姓名
                        params.putString("addr", idCardInfo.getAddress());//姓名
                        params.putString("id", idCardInfo.getId());//姓名
                        params.putString("effext", idCardInfo.getValidityTime());//姓名
                        params.putString("issueAt", idCardInfo.getDepart());//姓名
                        params.putString("passNum", idCardInfo.getPassNum());//姓名
                        params.putString("name", idCardInfo.getName());//姓名
                        params.putInt("visaTimes", idCardInfo.getVisaTimes());//姓名
                        if (idCardInfo.getPhotolength() > 0) {
                            byte[] buf = new byte[WLTService.imgLength];
                            if (1 == WLTService.wlt2Bmp(idCardInfo.getPhoto(), buf)) {
                                params.putString("img", Utils.bitmapToBase64(IDPhotoHelper.Bgr2Bitmap(buf)));
                            }
                        }
                        sendEvent(reactContext, "callback", params);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        WritableMap params = Arguments.createMap();
                        params.putInt("code",-1);
                        params.putString("msg",e.getMessage());
                        sendEvent(reactContext, "callback", params);
                        bStoped = true;
                    }


                }
            }
        }).start();
    }

    @ReactMethod
    public void stop() {
        bStoped = true;
    }

    @ReactMethod
    public void close(Promise promise) {
        if (idCardReader == null) {
            promise.reject(new Throwable("not connect"));
            return;
        }
        bStoped = true;
        idCardReader.closeDevice();
        idCardReader = null;
        promise.resolve(true);
    }

    @ReactMethod
    public void deviceVersion(Promise promise) {
        if (idCardReader == null) {
            promise.reject("");
        } else {
            promise.resolve(idCardReader.getFirmwareVersion());
        }
    }

    @Override
    public String getName() {
        return "RNIDCardReader3xx";
    }
}