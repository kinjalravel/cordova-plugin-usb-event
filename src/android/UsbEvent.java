package net.kyosho.usb.event;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Log;


import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileInputStream;
import com.github.mjdev.libaums.fs.UsbFileOutputStream;

/**
 * This class echoes a string called from JavaScript.
 */
public class UsbEvent extends CordovaPlugin {

  /**
   * TAG
   */
  private final String TAG = UsbEvent.class.getSimpleName();

  /**
   * Action key for list device.
   */
  private static final String ACTION_LIST_DEVICES = "listDevices";

  /**
   * Action key for checking register status.
   */
  private static final String ACTION_EVENT_EXISTS_CALLBACK = "existsRegisteredCallback";

  /**
   * Action key for registering event callback.
   */
  private static final String ACTION_EVENT_REGISTER_CALLBACK = "registerEventCallback";

  /**
   * Action key for unregistering event callback.
   */
  private static final String ACTION_EVENT_UNREGISTER_CALLBACK = "unregisterEventCallback";

  /**
   * Registered event callback.
   */
  private CallbackContext eventCallback;

  /**
   * filter
   */
  private IncludeFilter filter;

  /**
   * USB Manager.
   */
  private UsbManager usbManager;



  private static Activity appActivity = null;
  private static Context appContext = null;

  private UsbManager mUsbManager;
  private List<UsbDevice> mDetectedDevices;
  private PendingIntent mPermissionIntent;

  private UsbMassStorageDevice mUsbMSDevice;
  private static final String ACTION_USB_PERMISSION = "net.kyosho.usb.event.USB_PERMISSION";




  /**
   * Javascript entry point.
   *
   * @param action          The action to execute.
   * @param args            The exec() arguments.
   * @param callbackContext The callback context used when calling back into JavaScript.
   * @return result.
   */
  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
    switch (action) {
      case ACTION_LIST_DEVICES:
        this.listDevices(callbackContext, args);
        return true;
      case ACTION_EVENT_EXISTS_CALLBACK:
        this.existsRegisteredCallback(callbackContext);
        return true;
      case ACTION_EVENT_REGISTER_CALLBACK:
        this.registerEventCallback(callbackContext, args);
        return true;
      case ACTION_EVENT_UNREGISTER_CALLBACK:
        this.unregisterEventCallback(callbackContext);
        return true;
      default:
        callbackContext.error(String.format("Unsupported action. (action=%s)", action));
    }
    return false;
  }


  @Override
  public void pluginInitialize() {
    appActivity = cordova.getActivity();
    appContext = appActivity.getBaseContext();

/*    IntentFilter filter = new IntentFilter();
    filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
    filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
    filter.addAction(ACTION_USB_PERMISSION);
    appContext.registerReceiver(mUsbReceiver, filter);*/


    mPermissionIntent = PendingIntent.getBroadcast(appContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
    mUsbManager = (UsbManager) appContext.getSystemService(Context.USB_SERVICE);
    mDetectedDevices = new ArrayList<UsbDevice>();
  }


  /**
   * List USB devices.
   *
   * @param callbackContext The callback context used when calling back into JavaScript.
   * @param args            The exec() arguments.
   */
  private void listDevices(final CallbackContext callbackContext, final JSONArray args) {
    try {
      if (null == this.usbManager) {
        // Caching USBManager
        this.usbManager = (UsbManager) this.cordova.getActivity().getSystemService(Context.USB_SERVICE);
      }

      // Filter settings
      // TIPS: throw if essencial object does not exist.
      JSONObject option = args.optJSONObject(0);
      IncludeFilter filter = option == null ? null : IncludeFilter.create(option);

      // Get USB devices
      HashMap<String, UsbDevice> deviceMap = this.usbManager.getDeviceList();

      // create output JSON object
      JSONObject jsonObject = new UsbEventModel(UsbEventId.List, deviceMap).toJSONObject(filter);

      // Callback with result.
      PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, jsonObject);
      pluginResult.setKeepCallback(false);
      callbackContext.sendPluginResult(pluginResult);
    } catch (JSONException e) {
      if (null == callbackContext) {
        Log.e(TAG, "callbackContext is null.");
      } else {
        callbackContext.error(e.getMessage());
      }
    }
  }

  /**
   * Check callback is already exists.
   *
   * @param callbackContext The callback context used when calling back into JavaScript.
   */
  private void existsRegisteredCallback(final CallbackContext callbackContext) {
    cordova.getThreadPool().execute(() -> {
      boolean exists = (null != eventCallback);

      // Callback with result.
      PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, exists);
      pluginResult.setKeepCallback(false);
      callbackContext.sendPluginResult(pluginResult);
    });
  }

  /**
   * Register event callback.
   * Callback emit device information at attaching and detaching USB after this method call.
   *
   * @param callbackContext The callback context used when calling back into JavaScript.
   * @param args            The exec() arguments.
   */
  private void registerEventCallback(final CallbackContext callbackContext, final JSONArray args) {
    // Start monitoring
    this.registerUsbAttached();
    this.registerUsbDetached();

    cordova.getThreadPool().execute(() -> {
      try {
        // Update callback
        eventCallback = callbackContext;

        // Filter settings
        // TIPS: throw if essencial object does not exist.
        JSONObject option = args.optJSONObject(0);
        this.filter = option == null ? null : IncludeFilter.create(option);

        // create output JSON object
        JSONObject jsonObject = new UsbEventModel(UsbEventId.Registered).toJSONObject();

        // Callback with result.
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, jsonObject);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);
      } catch (JSONException e) {
        if (null == callbackContext) {
          Log.e(TAG, "eventCallback is null.");
        } else {
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  /**
   * Unregister event callback.
   *
   * @param callbackContext The callback context used when calling back into JavaScript.
   */
  private void unregisterEventCallback(final CallbackContext callbackContext) {
    try {
      // Stop monitoring
      this.unregisterUsbDetached();
      this.unregisterUsbAttached();
    } catch (Exception e) {
      Log.w(TAG, "Receiver is already unregistered.");
    }

    cordova.getThreadPool().execute(() -> {
      try {
        // Update callback
        eventCallback = null;

        // create output JSON object
        JSONObject jsonObject = new UsbEventModel(UsbEventId.Unregistered).toJSONObject();

        // Callback with result.
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, jsonObject);
        pluginResult.setKeepCallback(false);
        callbackContext.sendPluginResult(pluginResult);
      } catch (JSONException e) {
        if (null == callbackContext) {
          Log.e(TAG, "eventCallback is null.");
        } else {
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  /**
   * Start monitoring USB attached.
   */
  private void registerUsbAttached() {
    IntentFilter filter = new IntentFilter();
    filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
    filter.addAction(ACTION_USB_PERMISSION);



   appContext.registerReceiver(this.usbAttachReceiver, filter);
  }

  /**
   * Start monitoring USB detached.
   */
  private void registerUsbDetached() {
    IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
    appContext.registerReceiver(this.usbDetachReceiver, filter);
  }

  /**
   * Stop monitoring USB attached and detached.
   */
  @Override
  public void onDestroy() {
    super.onDestroy();
    try {
      this.unregisterUsbDetached();
      this.unregisterUsbAttached();
    } catch (Exception e) {
      Log.w(TAG, "Receiver is already unregistered.");
    }
  }

  /**
   * Stop monitoring USB attached.
   */
  private void unregisterUsbAttached() {
    appContext.unregisterReceiver(this.usbAttachReceiver);
  }

  /**
   * Stop monitoring USB detached.
   */
  private void unregisterUsbDetached() {
    appContext.unregisterReceiver(this.usbDetachReceiver);
  }

  /**
   * USB attaching monitor.
   */
  private BroadcastReceiver usbAttachReceiver = new BroadcastReceiver() {
    public void onReceive(Context context, Intent intent) {
      try {
        String action = intent.getAction();

        UsbDevice device =(UsbDevice)  intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action) || ACTION_USB_PERMISSION.equals(action) &&
                UsbEvent.this.eventCallback != null && device != null) {

          checkUSBStatus();
          if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            connectDevice();
          }

          // create output JSON object
          JSONObject jsonObject = new UsbEventModel(UsbEventId.Attached, device).toJSONObject(
                  UsbEvent.this.filter);

          // Callback with result.
          PluginResult result = new PluginResult(PluginResult.Status.OK, jsonObject);
          result.setKeepCallback(true);

            eventCallback.sendPluginResult(result);


          if (ACTION_USB_PERMISSION.equals(action)) {
            synchronized (this) {
              if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                if (device != null) {
                  openDevice();
                  String fileData = openRootFile("index.txt");
            Log.e("FileData==>>",fileData);
                  jsonObject.put("fileData",fileData);
                  PluginResult fileResult = new PluginResult(PluginResult.Status.OK, jsonObject);
                  fileResult.setKeepCallback(true);
                  eventCallback.sendPluginResult(fileResult);
                }
              }
            }
          }


        }
      } catch (JSONException e) {
        if (null == eventCallback) {
          Log.e(TAG, "eventCallback is null.");
        } else {
          eventCallback.error(e.getMessage());
        }
      }

    }
  };

  /**
   * USB detaching monitor.
   */
  private BroadcastReceiver usbDetachReceiver = new BroadcastReceiver() {
    public void onReceive(Context context, Intent intent) {
      try {
        String action = intent.getAction();
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

        if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action) &&
                UsbEvent.this.eventCallback != null && device != null) {

          // create output JSON object
          JSONObject jsonObject = new UsbEventModel(UsbEventId.Detached, device).toJSONObject(
                  UsbEvent.this.filter);

          // Callback with result.
          PluginResult result = new PluginResult(PluginResult.Status.OK, jsonObject);
          result.setKeepCallback(true);
          eventCallback.sendPluginResult(result);
        }
      } catch (JSONException e) {
        if (null == eventCallback) {
          Log.e(TAG, "eventCallback is null.");
        } else {
          eventCallback.error(e.getMessage());
        }
      }
    }
  };


  public void checkUSBStatus() {

    try {
      mDetectedDevices.clear();
      mUsbManager = (UsbManager) appContext.getSystemService(Context.USB_SERVICE);

      if (mUsbManager != null) {
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();

        if (!deviceList.isEmpty()) {
          Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
          while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            mDetectedDevices.add(device);
          }
        }

        if (mDetectedDevices.size() > 0) {
          String deviceName;
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            deviceName = (mDetectedDevices.get(0).getProductName());
          } else {
            deviceName = (mDetectedDevices.get(0).getDeviceName());
          }

        }

      }
    } catch (Exception e) {
    }

  }

  public void openDevice() {

    if (mDetectedDevices.size() > 0) {

      UsbMassStorageDevice[] devices = UsbMassStorageDevice.getMassStorageDevices(appContext);
      if (devices.length > 0) {
        mUsbMSDevice = devices[0];
      }
      try {

        mUsbMSDevice.init();

        FileSystem fs = mUsbMSDevice.getPartitions().get(0).getFileSystem();
        UsbFile root = fs.getRootDirectory();
        UsbFile[] files = root.listFiles();
        for (UsbFile file : files) {
          if (file.isDirectory()) {
            //Log.e("File=>>",file.getName());
            //Log.e("File Path=>>",file.getAbsolutePath());
          }
        }
      } catch (Exception e) {
      }
    }
  }


  private void connectDevice() {
    if (mDetectedDevices.size() > 0) {
      mUsbManager.requestPermission(mDetectedDevices.get(0), mPermissionIntent);
    }
  }

  private String convertStreamToString(InputStream is) {
    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
    StringBuilder sb = new StringBuilder();

    String line = null;
    try {
      while ((line = reader.readLine()) != null) {
        sb.append(line).append('\n');
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try {
        is.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return sb.toString();
  }



  String openRootFile( String fileName){
    String fileData = "";
    try {

      FileSystem fs = mUsbMSDevice.getPartitions().get(0).getFileSystem();
      UsbFile root = fs.getRootDirectory();
      UsbFile[] files = root.listFiles();
      Boolean worked = false;

      for (UsbFile file : files) {
        Log.e("File=>>",file.getAbsolutePath());
        if (!file.isDirectory()) {
          if (file.getName().equals(fileName)) {

            InputStream is = new UsbFileInputStream(file);
            byte[] buffer = new byte[fs.getChunkSize()];
            String response = convertStreamToString(is);
           // Log.e("File=>>",response.toString());
            fileData = response;
            //callback.invoke("success@" + response);
            worked = true;

          }
        }
      }

      if (worked.equals(false)) {
        //callback.invoke("error@ File Not Found");
      }


    } catch (Exception e) {
      Log.e("error@",  e.toString());
    }
    return fileData;
  }

}
