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
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileInputStream;


import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

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


    private static final String ACTION_EVENT_CREATEFILE = "createFileEventCallback";

    private static final String ACTION_EVENT_DELETEFILE = "deleteFileEventCallback";

    private static final String ACTION_EVENT_READFILE = "readFileEventCallback";

    private static final String ACTION_EVENT_WRITEFILE = "writeFileEventCallback";

    private static final String ACTION_EVENT_FILEEXIST = "fileExistFileEventCallback";


    static final String PROPERTY_EVENT_KEY_FILE_PATH = "path";
    static final String PROPERTY_EVENT_KEY_DATA = "data";
    static final String PROPERTY_EVENT_KEY_FILE_NAME = "fileName";

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

    FileSystem fileSystem;

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
            case ACTION_EVENT_CREATEFILE:
                if(fileSystem != null){
                    try {
                        JSONObject option = args.optJSONObject(0);
                        String fileName = option.getString(PROPERTY_EVENT_KEY_FILE_NAME);
                        String filePath = option.getString(PROPERTY_EVENT_KEY_FILE_PATH);
                        String fileData = option.getString(PROPERTY_EVENT_KEY_DATA);

                        if(filePath.startsWith("/")){
                            filePath.replaceFirst("/","");
                        }
                        ArrayList<String> pathList = new ArrayList<String>();
                        pathList.addAll(Arrays.asList(filePath.trim().split("/")));
                        searchCreateFile(pathList,fileName,fileData, fileSystem.getRootDirectory(), callbackContext);
                    }catch (Exception ignore){
                        sendResponse(getResultJson(false),callbackContext);
                    }
                }else {
                    sendResponse(getResultJson(false),callbackContext);
                }
                return true;
            case ACTION_EVENT_DELETEFILE:
                if(fileSystem != null){
                    //     searchDeleteFile();
                }else {
                    sendResponse(getResultJson(false),callbackContext);
                }
                return true;
            case ACTION_EVENT_READFILE:
                if(fileSystem != null){
                    //searchReadFile();
                }else {
                    sendResponse(getResultJson(false),callbackContext);
                }
                return true;
            case ACTION_EVENT_WRITEFILE:
                if(fileSystem != null){
                    //searchWriteFile();
                }else {
                    sendResponse(getResultJson(false),callbackContext);
                }
                return true;
            case ACTION_EVENT_FILEEXIST:
                if(fileSystem != null){
                    //    searchFileExist();
                }else {
                    sendResponse(getResultJson(false),callbackContext);
                }
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

                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action) || ACTION_USB_PERMISSION.equals(action) &&
                        UsbEvent.this.eventCallback != null && device != null) {

                    checkUSBStatus();
                    if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
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
                                    String fileData = openRootFile("index.txt", jsonObject);
                                    PluginResult fileResult = new PluginResult(PluginResult.Status.OK, jsonObject);
                                    fileResult.setKeepCallback(true);
                                    eventCallback.sendPluginResult(fileResult);
                                }
                            }
                        }
                    } else {
                        Log.e("Permission####>>", "Permission deny");
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
                fileSystem = mUsbMSDevice.getPartitions().get(0).getFileSystem();
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


    String openRootFile(String fileName, JSONObject jsonObject) {
        String fileData = "";
        String rootPath = "";
        try {


            UsbFile rootUsbFile = fileSystem.getRootDirectory();

            rootPath = rootUsbFile.getAbsolutePath();
            UsbFile[] files = rootUsbFile.listFiles();

            Boolean worked = false;

            for (UsbFile file : files) {
                Log.e("File=>>", file.getAbsolutePath());
                // fileData += file.getAbsolutePath();
                if (!file.isDirectory()) {
                    if (file.getName().equals(fileName)) {

                        InputStream is = new UsbFileInputStream(file);
                        byte[] buffer = new byte[fileSystem.getChunkSize()];
                        String response = convertStreamToString(is);
                        // Log.e("File=>>",response.toString());
                        fileData = response;
                        //callback.invoke("success@" + response);
                        worked = true;

                    }
                }
            }

            if (worked.equals(false)) {
                fileData = "error@ File Not Found";
                //callback.invoke("error@ File Not Found");
            }


        } catch (Exception e) {

            Log.e("error@", e.toString());
        }
        try {
            jsonObject.put("fileData", fileData);
            jsonObject.put("rootPath", rootPath);
        } catch (Exception ignored) {
        }
        return fileData;
    }


    void searchCreateFile(ArrayList<String> filePath, String fileName, String data, UsbFile parentFile,CallbackContext callbackContext) {
        try {
            if(filePath.isEmpty()){
                UsbFile newFile =  createFile(fileSystem.getRootDirectory(),fileName);
                if(newFile != null){
                    if(data != null) {
                        writeFile(newFile, data);
                    }
                    sendResponse(getResultJson(true),callbackContext);
                }else{
                    sendResponse(getResultJson(false),callbackContext);
                }

            }

            for (UsbFile file : parentFile.listFiles()) {
                if(!filePath.isEmpty() && file.isDirectory() && file.getName().equals(filePath.get(0))){
                    if(filePath.size() == 1){
                        UsbFile newFile =  createFile(file,fileName);
                        if(newFile != null){
                            writeFile(newFile,data);
                            sendResponse(getResultJson(true),callbackContext);
                        }else{
                            sendResponse(getResultJson(false),callbackContext);
                        }
                        return;
                    }else if(filePath.size()>1){
                        filePath.remove(0);
                        searchCreateFile(filePath,fileName,data,file,callbackContext);
                        return;
                    }
                    break;
                }
            }
            UsbFile tmpFile = parentFile;
            if(filePath.size()>1){
                for(int i=0;i<filePath.size()-1;i++){

                        tmpFile = tmpFile.createDirectory(filePath.get(i));
                    if(i == filePath.size()-1){
                        ArrayList<String> filePathList = new ArrayList<String>();
                        filePathList.add(filePath.get(i));
                        searchCreateFile(filePathList,fileName,data,tmpFile.getParent(),callbackContext);
                    }
                }
            }


        }catch (Exception ex){}
        sendResponse(getResultJson(false),callbackContext);
    }

    void searchDeleteFile(ArrayList<String> filePath, String fileName, UsbFile parentFile,CallbackContext callbackContext) {
        try {
            for (UsbFile file : parentFile.listFiles()) {
                if(!filePath.isEmpty() && file.getName().equals(filePath.get(0))){
                    if(filePath.size() == 1 && !file.isDirectory()){
                        if(deleteFile(file)){
                            sendResponse(getResultJson(true),callbackContext);
                        }else{
                            sendResponse(getResultJson(false),callbackContext);
                        }
                    }else if(filePath.size()>1){
                        filePath.remove(0);
                        searchDeleteFile(filePath,fileName,file,callbackContext);
                        return;
                    }
                    break;
                }
            }
        }catch (Exception ex){}
        sendResponse(getResultJson(false),callbackContext);
    }

    void searchReadFile(ArrayList<String> filePath, String fileName, UsbFile parentFile,CallbackContext callbackContext) {
        try {
            for (UsbFile file : parentFile.listFiles()) {
                if(!filePath.isEmpty() && file.getName().equals(filePath.get(0))){
                    if(filePath.size() == 1 && !file.isDirectory()){
                        String fileData = readFile(file);
                        if(fileData != null){
                            JSONObject response = getResultJson(true);
                            try {
                                response.put("data", fileData);
                            }catch (Exception ignore){}
                            sendResponse(response,callbackContext);
                        }else{
                            sendResponse(getResultJson(false),callbackContext);
                        }
                    }else if(filePath.size()>1){
                        filePath.remove(0);
                        searchReadFile(filePath,fileName,file,callbackContext);
                        return;
                    }
                    break;
                }
            }
        }catch (Exception ex){}
        sendResponse(getResultJson(false),callbackContext);
    }

    void searchWriteFile(ArrayList<String> filePath, String fileName,String data, UsbFile parentFile,CallbackContext callbackContext) {
        try {
            for (UsbFile file : parentFile.listFiles()) {
                if(!filePath.isEmpty() && file.getName().equals(filePath.get(0))){
                    if(filePath.size() == 1 && !file.isDirectory()){
                        if(writeFile(file,data) != null){
                            sendResponse(getResultJson(true),callbackContext);
                        }else{
                            sendResponse(getResultJson(false),callbackContext);
                        }

                    }else if(filePath.size()>1){
                        filePath.remove(0);
                        searchWriteFile(filePath,fileName,data,file,callbackContext);
                        return;
                    }
                    break;
                }
            }
        }catch (Exception ex){}
        sendResponse(getResultJson(false),callbackContext);
    }

    void searchFileExist(ArrayList<String> filePath, String fileName, UsbFile parentFile,CallbackContext callbackContext) {
        try {
            for (UsbFile file : parentFile.listFiles()) {
                if(!filePath.isEmpty() && file.getName().equals(filePath.get(0))){
                    if(filePath.size() == 1 && !file.isDirectory()){
                        sendResponse(getResultJson(true),callbackContext);
                    }else if(filePath.size()>1){
                        filePath.remove(0);
                        searchFileExist(filePath,fileName,file,callbackContext);
                        return;
                    }
                    break;
                }
            }
        }catch (Exception ex){}
        sendResponse(getResultJson(false),callbackContext);
    }


    String readFile(UsbFile file) {
        try {
            if (!file.isDirectory()) {
                InputStream is = new UsbFileInputStream(file);
                byte[] buffer = new byte[fileSystem.getChunkSize()];
                String response = convertStreamToString(is);
                // Log.e("File=>>",response.toString());
                return response;
            }
        } catch (Exception ex) {
        }
        return null;
    }

    boolean deleteFile(UsbFile file) {
        try {
            if (!file.isDirectory()) {
                file.delete();
                return true;
            }
        } catch (Exception ex) {
        }
        return false;
    }

    JSONObject getResultJson(boolean isSuccess){
        JSONObject jsonData = new JSONObject();
        try {
            jsonData.put("success",isSuccess);
        } catch (Exception e) {
        }
        return jsonData;
    }

    void sendResponse( JSONObject jsonObject,CallbackContext callbackContext){
        PluginResult fileResult = new PluginResult(PluginResult.Status.OK, jsonObject);
        fileResult.setKeepCallback(true);
        callbackContext.sendPluginResult(fileResult);
    }

    UsbFile createFile(UsbFile file, String fileName) {
        try {
            if (file.isDirectory() && !fileName.trim().isEmpty()) {
                return file.createFile(fileName);
            }
        } catch (Exception ex) {
        }
        return null;
    }

    UsbFile writeFile(UsbFile file, String fileData) {
        try {
            if (!file.isDirectory()) {
                file.write(0, ByteBuffer.wrap(fileData.getBytes()));
                file.close();
                return file;
            }
        } catch (Exception ex) {
        }
        return null;
    }


}
