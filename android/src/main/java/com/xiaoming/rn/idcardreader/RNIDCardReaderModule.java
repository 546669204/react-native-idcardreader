package com.xiaoming.rn.idcardreader;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.Nullable;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.zkteco.android.IDReader.IDPhotoHelper;
import com.zkteco.android.IDReader.WLTService;
import com.zkteco.android.biometric.core.device.ParameterHelper;
import com.zkteco.android.biometric.core.device.TransportType;
import com.zkteco.android.biometric.module.idcard.IDCardReader;
import com.zkteco.android.biometric.module.idcard.IDCardReaderFactory;
import com.zkteco.android.biometric.module.idcard.exception.IDCardReaderException;
import com.zkteco.android.biometric.module.idcard.meta.IDCardInfo;
import com.zkteco.android.biometric.module.idcard.meta.IDPRPCardInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.facebook.react.bridge.UiThreadUtil.runOnUiThread;

public class RNIDCardReaderModule extends ReactContextBaseJavaModule  implements  LifecycleEventListener{

  private final ReactApplicationContext reactContext;
  private static final int VID = 1024;    //IDR VID
  private static final int PID = 50010;     //IDR PID
  private IDCardReader idCardReader = null;

  private boolean bopen = false;
  private boolean bStoped = false;
  private int mReadCount = 0;
  private CountDownLatch countdownLatch = null;

  private UsbManager musbManager = null;
  private final String ACTION_USB_PERMISSION = "com.xiaoming.rn.idcardreader.USB_PERMISSION";

  private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();

      if (ACTION_USB_PERMISSION.equals(action))
      {
        synchronized (this)
        {
          UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
          Log.d("xm","BroadcastReceiver");
          if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
          {
            OpenDevice();
            Toast.makeText(reactContext, "OpenDevice", Toast.LENGTH_SHORT).show();
          }
          else
          {
            Toast.makeText(reactContext, "USB未授权", Toast.LENGTH_SHORT).show();
            //mTxtReport.setText("USB未授权");
          }
        }
      }
    }
  };

  public RNIDCardReaderModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
    reactContext.addLifecycleEventListener(this);
  }


  private void startIDCardReader(){
    Map idrparams = new HashMap();
    idrparams.put(ParameterHelper.PARAM_KEY_VID, VID);
    idrparams.put(ParameterHelper.PARAM_KEY_PID, PID);
    idCardReader = IDCardReaderFactory.createIDCardReader(this.reactContext, TransportType.USB, idrparams);
  }
  private void RequestDevicePermission()
  {
    Toast.makeText(reactContext, "RequestDevicePermission", Toast.LENGTH_SHORT).show();
    musbManager = (UsbManager)this.reactContext.getSystemService(Context.USB_SERVICE);
    IntentFilter filter = new IntentFilter();
    filter.addAction(ACTION_USB_PERMISSION);
    filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
    reactContext.registerReceiver(mUsbReceiver, filter);
    Log.d("xm",""+musbManager.getDeviceList().values().size());
    for (UsbDevice device : musbManager.getDeviceList().values())
    {
      Toast.makeText(reactContext, ""+device.getVendorId()+device.getProductId(), Toast.LENGTH_SHORT).show();
      Log.d("xm",""+device.getVendorId()+device.getProductId());
      if (device.getVendorId() == VID && device.getProductId() == PID)
      {
        Intent intent = new Intent(ACTION_USB_PERMISSION);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(reactContext, 0, intent, 0);
        musbManager.requestPermission(device, pendingIntent);
      }
    }
  }


  public void OpenDevice()
  {
    if (bopen)
    {
      WritableMap params = Arguments.createMap();
      params.putInt("code",-1);
      params.putString("msg","设备已连接");
      sendEvent(reactContext,"callback",params);
      return;
    }
    try {
      startIDCardReader();
      idCardReader.open(0);
      bStoped = false;
      mReadCount = 0;
//      writeLogToFile("连接设备成功");
//      textView.setText("连接成功");
      bopen = true;
      countdownLatch = new CountDownLatch(1);
      new Thread(new Runnable() {
        public void run() {
          while (!bStoped) {
            try {
              Thread.sleep(500);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }

            boolean ret = false;
            final long nTickstart = System.currentTimeMillis();
            try {
              idCardReader.findCard(0);
              idCardReader.selectCard(0);
            }catch (IDCardReaderException e)
            {
              //continue;
            }
            try {
              Thread.sleep(50);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
            int retType = 0;
            try {
              retType = idCardReader.readCardEx(0, 0);
            }
            catch (IDCardReaderException e)
            {
              WritableMap params = Arguments.createMap();
              params.putInt("code",-1);
              params.putString("msg","读卡失败，错误信息：" + e.getMessage());
              sendEvent(reactContext,"callback",params);
            }
            if (retType == 1 || retType == 2 || retType == 3)
            {
              final long nTickUsed = (System.currentTimeMillis()-nTickstart);
              final int final_retType = retType;
//              writeLogToFile("读卡成功：" + (++mReadCount) + "次" + "，耗时：" + nTickUsed + "毫秒");
              runOnUiThread(new Runnable() {
                public void run() {
                  WritableMap params = Arguments.createMap();
                  params.putDouble("timer",nTickUsed);
                  params.putInt("retType",final_retType);
                  if (final_retType == 1)
                  {
                    final IDCardInfo idCardInfo = idCardReader.getLastIDCardInfo();
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
                        params.putString("img", IDPhotoHelper.bitmapToBase64(IDPhotoHelper.Bgr2Bitmap(buf)));
                      }
                    }
                  }
                  else if (final_retType == 2)
                  {
                    final IDPRPCardInfo idprpCardInfo = idCardReader.getLastPRPIDCardInfo();

                    params.putString("name", idprpCardInfo.getCnName());//姓名
                    params.putString("enname", idprpCardInfo.getEnName());//姓名
                    params.putString("country", idprpCardInfo.getCountry() + "/" + idprpCardInfo.getCountryCode());///国家/国家地区代码
                    params.putString("born", idprpCardInfo.getBirth());//姓名
                    params.putString("id", idprpCardInfo.getId());//姓名
                    params.putString("effext", idprpCardInfo.getValidityTime());//姓名
                    params.putString("issueAt", "公安部");//姓名
                    if (idprpCardInfo.getPhotolength() > 0) {
                      byte[] buf = new byte[WLTService.imgLength];
                      if (1 == WLTService.wlt2Bmp(idprpCardInfo.getPhoto(), buf)) {
                        params.putString("img", IDPhotoHelper.bitmapToBase64(IDPhotoHelper.Bgr2Bitmap(buf)));
                      }
                    }
                  }
                  else
                  {
                    final IDCardInfo idCardInfo = idCardReader.getLastIDCardInfo();
                    params.putString("name", idCardInfo.getName());//姓名
                    params.putString("nation", "");//民族,港澳台不支持该项
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
                        params.putString("img", IDPhotoHelper.bitmapToBase64(IDPhotoHelper.Bgr2Bitmap(buf)));
                      }
                    }
                  }
                  sendEvent(reactContext,"callback",params);
                }
              });
            }
          }
          countdownLatch.countDown();
        }
      }).start();
    }catch (IDCardReaderException e)
    {
      WritableMap params = Arguments.createMap();
      params.putInt("code",-1);
      params.putString("msg","开始读卡失败，错误码：" + e.getErrorCode() + "\n错误信息：" + e.getMessage() + "\n内部代码=" + e.getInternalErrorCode());
      sendEvent(reactContext,"callback",params);
    }


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
    IDCardReaderFactory.destroy(idCardReader);
  }

  private void sendEvent(ReactApplicationContext reactContext,
                         String eventName,
                         @Nullable WritableMap params) {
    reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
  }

  @ReactMethod
  public void start() {
    if (bopen)
    {
      WritableMap params = Arguments.createMap();
      params.putInt("code",-1);
      params.putString("msg","设备已连接");
      sendEvent(reactContext,"callback",params);
      return;
    }
    RequestDevicePermission();
  }

  @ReactMethod
  public void stop() {
    if (!bopen)
    {
      return;
    }
    bStoped = true;
    mReadCount = 0;
    if (null != countdownLatch) {
      try {
        countdownLatch.await(2, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    try {
      idCardReader.close(0);
    } catch (IDCardReaderException e) {
      e.printStackTrace();
    }
//    textView.setText("设备断开连接");
    bopen = false;
  }

  @Override
  public String getName() {
    return "RNIDCardReader";
  }
}
