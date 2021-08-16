package net.kyosho.usb.event;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Base64;
import android.util.Log;


import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.lang.reflect.Array;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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

    private static final String ACTION_EVENT_READFILE_BYTES = "readFileBytesEventCallback";

    private static final String ACTION_EVENT_WRITEFILE = "writeFileEventCallback";

    private static final String ACTION_EVENT_FILEEXIST = "fileExistFileEventCallback";

    private static final String ACTION_EVENT_INSERTINTOFILE = "insertIntoFileEventCallback";


    private static final String SOCKET_EVENT_IGNITION = "ignition";

    private static final String SOCKET_EVENT_CREATEFILE = "createFile";

    private static final String SOCKET_EVENT_DELETEFILE = "deleteFile";

    private static final String SOCKET_EVENT_READFILE = "readFile";

    private static final String SOCKET_EVENT_READFILE_BYTES = "readFileBytes";

    private static final String SOCKET_EVENT_WRITEFILE = "writeFile";

    private static final String SOCKET_EVENT_FILEEXIST = "fileExist";

    private static final String SOCKET_EVENT_INSERTINTOFILE = "insertIntoFile";


    static final String PROPERTY_EVENT_KEY_FILE_PATH = "path";
    static final String PROPERTY_EVENT_KEY_DATA = "data";
    static final String PROPERTY_EVENT_KEY_FILE_NAME = "fileName";
    static final String PROPERTY_EVENT_KEY_ISINTERNAL = "isInternal";
    static final String PROPERTY_EVENT_KEY_START_BYTES = "startBytes";
    static final String PROPERTY_EVENT_KEY_TOTAL_BYTES = "totalBytes";

    static final String PROPERTY_EVENT_KEY_START_POSITION = "startPosition";
    static final String PROPERTY_EVENT_KEY_TOTAL_LENGTH = "totalLength";

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

    private Socket mSocket;

    {
        try {

            mSocket = IO.socket("http://127.0.0.1:3100/");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


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
                if (fileSystem != null) {
                    try {
                        JSONObject option = args.optJSONObject(0);
                        String fileName = "";
                        String filePath = "";
                        String fileData = "";
                        boolean isInternal = false;

                        if (option.has(PROPERTY_EVENT_KEY_FILE_NAME)) {
                            fileName = option.getString(PROPERTY_EVENT_KEY_FILE_NAME).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_FILE_PATH)) {
                            filePath = option.getString(PROPERTY_EVENT_KEY_FILE_PATH).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_DATA)) {
                            fileData = option.getString(PROPERTY_EVENT_KEY_DATA);
                        }

                        if(option.has(PROPERTY_EVENT_KEY_ISINTERNAL)){
                            try{
                                isInternal = option.getBoolean(PROPERTY_EVENT_KEY_ISINTERNAL);
                            }catch (Exception ignore){}
                        }


                        if(isInternal){
                            createFileInInternalStorage(fileName, fileData, callbackContext, filePath, "");
                        }else {

                            if (filePath.startsWith("/")) {
                                filePath = filePath.replaceFirst("/", "");
                            }
                            if (filePath.endsWith("/")) {
                                filePath = filePath.substring(0, filePath.length() - 1);
                            }
                            ArrayList<String> pathList = new ArrayList<String>();
                            if (!filePath.isEmpty())
                                pathList.addAll(Arrays.asList(filePath.trim().split("/")));
                            searchCreateFile(pathList, fileName, fileData, fileSystem.getRootDirectory(), callbackContext, filePath, "");
                        }

                    } catch (Exception ignore) {
                        sendResponse(getResultJson(false), callbackContext,"","","");
                    }
                } else {
                    sendResponse(getResultJson(false), callbackContext,"","","");
                }
                return true;
            case ACTION_EVENT_DELETEFILE:
                if (fileSystem != null) {
                    try {
                        JSONObject option = args.optJSONObject(0);
                        String fileName = "";
                        String filePath = "";
                        boolean isInternal = false;

                        if (option.has(PROPERTY_EVENT_KEY_FILE_NAME)) {
                            fileName = option.getString(PROPERTY_EVENT_KEY_FILE_NAME).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_FILE_PATH)) {
                            filePath = option.getString(PROPERTY_EVENT_KEY_FILE_PATH).trim();
                        }
                        if(option.has(PROPERTY_EVENT_KEY_ISINTERNAL)){
                            try{
                                isInternal = option.getBoolean(PROPERTY_EVENT_KEY_ISINTERNAL);
                            }catch (Exception ignore){}
                        }

                        if(isInternal){
                            deleteFileFromInternalStorage( fileName, callbackContext, filePath, "");
                        }else {
                            if (filePath.startsWith("/")) {
                                filePath = filePath.replaceFirst("/", "");
                            }
                            if (filePath.endsWith("/")) {
                                filePath = filePath.substring(0, filePath.length() - 1);
                            }
                            ArrayList<String> pathList = new ArrayList<String>();
                            if (!filePath.isEmpty())
                                pathList.addAll(Arrays.asList(filePath.trim().split("/")));
                            searchDeleteFile(pathList, fileName, fileSystem.getRootDirectory(), callbackContext, filePath, "");
                        }
                    } catch (Exception ignore) {
                        sendResponse(getResultJson(false), callbackContext,"","","");
                    }
                } else {
                    sendResponse(getResultJson(false), callbackContext,"","","");
                }
                return true;
            case ACTION_EVENT_READFILE:
                if (fileSystem != null) {

                    try {
                        JSONObject option = args.optJSONObject(0);
                        String fileName = "";
                        String filePath = "";
                        boolean isInternal = false;

                        if (option.has(PROPERTY_EVENT_KEY_FILE_NAME)) {
                            fileName = option.getString(PROPERTY_EVENT_KEY_FILE_NAME).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_FILE_PATH)) {
                            filePath = option.getString(PROPERTY_EVENT_KEY_FILE_PATH).trim();
                        }
                        if(option.has(PROPERTY_EVENT_KEY_ISINTERNAL)){
                            try{
                                isInternal = option.getBoolean(PROPERTY_EVENT_KEY_ISINTERNAL);
                            }catch (Exception ignore){}
                        }

                        if(isInternal){
                            readFileFromInternalStorage( fileName, callbackContext, filePath, "");
                        }else {
                            if (filePath.startsWith("/")) {
                                filePath = filePath.replaceFirst("/", "");
                            }
                            if (filePath.endsWith("/")) {
                                filePath = filePath.substring(0, filePath.length() - 1);
                            }
                            ArrayList<String> pathList = new ArrayList<String>();
                            if (!filePath.isEmpty())
                                pathList.addAll(Arrays.asList(filePath.trim().split("/")));
                            searchReadFile(pathList, fileName, fileSystem.getRootDirectory(), callbackContext, filePath, "");
                        }
                    } catch (Exception ignore) {
                        sendResponse(getResultJson(false), callbackContext,"","","");
                    }
                } else {
                    sendResponse(getResultJson(false), callbackContext,"","","");
                }
                return true;

            case ACTION_EVENT_READFILE_BYTES:
                if (fileSystem != null) {
                    try {
                        JSONObject option = args.optJSONObject(0);
                        String fileName = "";
                        String filePath = "";
                        int startBytes = 0;
                        int totalBytes = 0;
                        boolean isInternal = false;

                        if (option.has(PROPERTY_EVENT_KEY_FILE_NAME)) {
                            fileName = option.getString(PROPERTY_EVENT_KEY_FILE_NAME).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_FILE_PATH)) {
                            filePath = option.getString(PROPERTY_EVENT_KEY_FILE_PATH).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_START_BYTES)) {
                            startBytes = option.getInt(PROPERTY_EVENT_KEY_START_BYTES);
                        }
                        if (option.has(PROPERTY_EVENT_KEY_TOTAL_BYTES)) {
                            totalBytes = option.getInt(PROPERTY_EVENT_KEY_TOTAL_BYTES);
                        }

                        if(option.has(PROPERTY_EVENT_KEY_ISINTERNAL)){
                            try{
                                isInternal = option.getBoolean(PROPERTY_EVENT_KEY_ISINTERNAL);
                            }catch (Exception ignore){}
                        }

                        if(isInternal){
                            readFileBytesFromInternalStorage( fileName,startBytes, totalBytes, callbackContext, filePath, "");
                        }else {
                            if (filePath.startsWith("/")) {
                                filePath = filePath.replaceFirst("/", "");
                            }
                            if (filePath.endsWith("/")) {
                                filePath = filePath.substring(0, filePath.length() - 1);
                            }
                            ArrayList<String> pathList = new ArrayList<String>();
                            if (!filePath.isEmpty())
                                pathList.addAll(Arrays.asList(filePath.trim().split("/")));
                            searchReadFileBytes(pathList, fileName, startBytes, totalBytes, fileSystem.getRootDirectory(), callbackContext, filePath, "");
                        }
                    } catch (Exception ignore) {
                        sendResponse(getResultJson(false), callbackContext,"","","");
                    }
                } else {
                    sendResponse(getResultJson(false), callbackContext,"","","");
                }
                return true;
            case ACTION_EVENT_WRITEFILE:
                if (fileSystem != null) {

                    try {
                        JSONObject option = args.optJSONObject(0);
                        String fileName = "";
                        String filePath = "";
                        String fileData = "";
                        boolean isInternal = false;

                        if (option.has(PROPERTY_EVENT_KEY_FILE_NAME)) {
                            fileName = option.getString(PROPERTY_EVENT_KEY_FILE_NAME).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_FILE_PATH)) {
                            filePath = option.getString(PROPERTY_EVENT_KEY_FILE_PATH).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_DATA)) {
                            fileData = option.getString(PROPERTY_EVENT_KEY_DATA);
                        }
                        if(option.has(PROPERTY_EVENT_KEY_ISINTERNAL)){
                            try{
                                isInternal = option.getBoolean(PROPERTY_EVENT_KEY_ISINTERNAL);
                            }catch (Exception ignore){}
                        }
                        if(isInternal){
                            createFileInInternalStorage( fileName, fileData, callbackContext, filePath, "");
                        }else {
                            if (filePath.startsWith("/")) {
                                filePath = filePath.replaceFirst("/", "");
                            }
                            if (filePath.endsWith("/")) {
                                filePath = filePath.substring(0, filePath.length() - 1);
                            }
                            ArrayList<String> pathList = new ArrayList<String>();
                            if (!filePath.isEmpty())
                                pathList.addAll(Arrays.asList(filePath.trim().split("/")));
                            searchWriteFile(pathList, fileName, fileData, fileSystem.getRootDirectory(), callbackContext, filePath, "");
                        }
                    } catch (Exception ignore) {
                        sendResponse(getResultJson(false), callbackContext,"","","");
                    }
                } else {
                    sendResponse(getResultJson(false), callbackContext,"","","");
                }
                return true;
            case ACTION_EVENT_INSERTINTOFILE:
                if (fileSystem != null) {

                    try {
                        JSONObject option = args.optJSONObject(0);
                        String fileName = "";
                        String filePath = "";
                        String fileData = "";
                        int startPosition = 0;
                        int totalLength = 0;

                        boolean isInternal = false;

                        if (option.has(PROPERTY_EVENT_KEY_FILE_NAME)) {
                            fileName = option.getString(PROPERTY_EVENT_KEY_FILE_NAME).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_FILE_PATH)) {
                            filePath = option.getString(PROPERTY_EVENT_KEY_FILE_PATH).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_DATA)) {
                            fileData = option.getString(PROPERTY_EVENT_KEY_DATA);
                        }
                        if(option.has(PROPERTY_EVENT_KEY_ISINTERNAL)){
                            try{
                                isInternal = option.getBoolean(PROPERTY_EVENT_KEY_ISINTERNAL);
                            }catch (Exception ignore){}
                        }


                        if(option.has(PROPERTY_EVENT_KEY_START_POSITION)){
                            try{
                                startPosition = option.getInt(PROPERTY_EVENT_KEY_START_POSITION);
                            }catch (Exception ignore){}
                        }

                        if(option.has(PROPERTY_EVENT_KEY_ISINTERNAL)){
                            try{
                                totalLength = option.getInt(PROPERTY_EVENT_KEY_TOTAL_LENGTH);
                            }catch (Exception ignore){}
                        }
                        if(totalLength <=0){
                            sendResponse(getResultJson(false), callbackContext,"","","");
                        }

                        if(isInternal){
                            insertFileDataInInternalStorage( fileName, fileData,startPosition,totalLength, callbackContext, filePath, "");
                        }else {
                            if (filePath.startsWith("/")) {
                                filePath = filePath.replaceFirst("/", "");
                            }
                            if (filePath.endsWith("/")) {
                                filePath = filePath.substring(0, filePath.length() - 1);
                            }
                            ArrayList<String> pathList = new ArrayList<String>();
                            if (!filePath.isEmpty())
                                pathList.addAll(Arrays.asList(filePath.trim().split("/")));
                            searchInsertDataIntoFile(pathList, fileName, fileData,startPosition,totalLength, fileSystem.getRootDirectory(), callbackContext, filePath, "");
                        }
                    } catch (Exception ignore) {
                        sendResponse(getResultJson(false), callbackContext,"","","");
                    }
                } else {
                    sendResponse(getResultJson(false), callbackContext,"","","");
                }
                return true;
            case ACTION_EVENT_FILEEXIST:
                if (fileSystem != null) {

                    try {
                        JSONObject option = args.optJSONObject(0);
                        String fileName = "";
                        String filePath = "";
                        boolean isInternal = false;

                        if (option.has(PROPERTY_EVENT_KEY_FILE_NAME)) {
                            fileName = option.getString(PROPERTY_EVENT_KEY_FILE_NAME).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_FILE_PATH)) {
                            filePath = option.getString(PROPERTY_EVENT_KEY_FILE_PATH).trim();
                        }
                        if(option.has(PROPERTY_EVENT_KEY_ISINTERNAL)){
                            try{
                                isInternal = option.getBoolean(PROPERTY_EVENT_KEY_ISINTERNAL);
                            }catch (Exception ignore){}
                        }
                        if(isInternal){
                            isFileExistInInternalStorage( fileName, callbackContext, filePath, "");
                        }else {
                            if (filePath.startsWith("/")) {
                                filePath = filePath.replaceFirst("/", "");
                            }
                            if (filePath.endsWith("/")) {
                                filePath = filePath.substring(0, filePath.length() - 1);
                            }
                            ArrayList<String> pathList = new ArrayList<String>();
                            if (!filePath.isEmpty())
                                pathList.addAll(Arrays.asList(filePath.trim().split("/")));
                            searchFileExist(pathList, fileName, fileSystem.getRootDirectory(), callbackContext, filePath, "");
                        }
                    } catch (Exception ignore) {
                        sendResponse(getResultJson(false), callbackContext,"","","");
                    }
                } else {
                    sendResponse(getResultJson(false), callbackContext,"","","");
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

        setSocketEvents();

    }

    private void setSocketEvents() {
        mSocket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.d(TAG, "chatapp call: connected to server");
            }
        });

        mSocket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.d(TAG, " chatapp call: disconnected from the server");
            }
        });

        mSocket.on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.d(TAG, "chatapp call: connection error");
            }
        });



        mSocket.on(SOCKET_EVENT_CREATEFILE, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                if (fileSystem != null) {
                    try {
                        JSONObject option = (JSONObject) args[0];
                        String fileName = "";
                        String filePath = "";
                        String fileData = "";
                        boolean isInternal = false;

                        if (option.has(PROPERTY_EVENT_KEY_FILE_NAME)) {
                            fileName = option.getString(PROPERTY_EVENT_KEY_FILE_NAME).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_FILE_PATH)) {
                            filePath = option.getString(PROPERTY_EVENT_KEY_FILE_PATH).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_DATA)) {
                            fileData = option.getString(PROPERTY_EVENT_KEY_DATA);
                        }

                        if (option.has(PROPERTY_EVENT_KEY_ISINTERNAL)) {
                            try {
                                isInternal = option.getBoolean(PROPERTY_EVENT_KEY_ISINTERNAL);
                            } catch (Exception ignore) {
                            }
                        }

                        if(isInternal){
                            createFileInInternalStorage(fileName, fileData, null, filePath, "");
                        }else{
                        if (filePath.startsWith("/")) {
                            filePath = filePath.replaceFirst("/", "");
                        }
                        if (filePath.endsWith("/")) {
                            filePath = filePath.substring(0, filePath.length() - 1);
                        }
                        ArrayList<String> pathList = new ArrayList<String>();
                        if (!filePath.isEmpty())
                            pathList.addAll(Arrays.asList(filePath.trim().split("/")));
                        searchCreateFile(pathList, fileName, fileData, fileSystem.getRootDirectory(), null, filePath, SOCKET_EVENT_CREATEFILE);
                    }
                    } catch (Exception ignore) {
                        sendResponse(getResultJson(false), null,"","",SOCKET_EVENT_CREATEFILE);
                    }
                } else {
                    sendResponse(getResultJson(false), null,"","",SOCKET_EVENT_CREATEFILE);
                }
            }
        });


        mSocket.on(SOCKET_EVENT_DELETEFILE, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                if (fileSystem != null) {
                    try {
                        JSONObject option = (JSONObject) args[0];
                        String fileName = "";
                        String filePath = "";
                        boolean isInternal = false;

                        if (option.has(PROPERTY_EVENT_KEY_FILE_NAME)) {
                            fileName = option.getString(PROPERTY_EVENT_KEY_FILE_NAME).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_FILE_PATH)) {
                            filePath = option.getString(PROPERTY_EVENT_KEY_FILE_PATH).trim();
                        }
                        if(option.has(PROPERTY_EVENT_KEY_ISINTERNAL)){
                            try{
                                isInternal = option.getBoolean(PROPERTY_EVENT_KEY_ISINTERNAL);
                            }catch (Exception ignore){}
                        }

                        if(isInternal){
                            deleteFileFromInternalStorage( fileName,  null, filePath, SOCKET_EVENT_DELETEFILE);
                        }else {
                            if (filePath.startsWith("/")) {
                                filePath = filePath.replaceFirst("/", "");
                            }
                            if (filePath.endsWith("/")) {
                                filePath = filePath.substring(0, filePath.length() - 1);
                            }
                            ArrayList<String> pathList = new ArrayList<String>();
                            if (!filePath.isEmpty())
                                pathList.addAll(Arrays.asList(filePath.trim().split("/")));
                            searchDeleteFile(pathList, fileName, fileSystem.getRootDirectory(), null, filePath, SOCKET_EVENT_DELETEFILE);
                        }
                    } catch (Exception ignore) {
                        sendResponse(getResultJson(false), null,"","",SOCKET_EVENT_DELETEFILE);
                    }
                } else {
                    sendResponse(getResultJson(false), null,"","",SOCKET_EVENT_DELETEFILE);
                }
            }
        });



        mSocket.on(SOCKET_EVENT_READFILE, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                if (fileSystem != null) {

                    try {
                        JSONObject option = (JSONObject) args[0];
                        String fileName = "";
                        String filePath = "";
                        boolean isInternal = false;

                        if (option.has(PROPERTY_EVENT_KEY_FILE_NAME)) {
                            fileName = option.getString(PROPERTY_EVENT_KEY_FILE_NAME).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_FILE_PATH)) {
                            filePath = option.getString(PROPERTY_EVENT_KEY_FILE_PATH).trim();
                        }
                        if(option.has(PROPERTY_EVENT_KEY_ISINTERNAL)){
                            try{
                                isInternal = option.getBoolean(PROPERTY_EVENT_KEY_ISINTERNAL);
                            }catch (Exception ignore){}
                        }

                        if(isInternal){
                            readFileFromInternalStorage( fileName,null, filePath, SOCKET_EVENT_READFILE);
                        }else {
                            if (filePath.startsWith("/")) {
                                filePath = filePath.replaceFirst("/", "");
                            }
                            if (filePath.endsWith("/")) {
                                filePath = filePath.substring(0, filePath.length() - 1);
                            }
                            ArrayList<String> pathList = new ArrayList<String>();
                            if (!filePath.isEmpty())
                                pathList.addAll(Arrays.asList(filePath.trim().split("/")));
                            searchReadFile(pathList, fileName, fileSystem.getRootDirectory(), null, filePath, SOCKET_EVENT_READFILE);
                        }
                    } catch (Exception ignore) {
                        sendResponse(getResultJson(false), null,"","",SOCKET_EVENT_READFILE);
                    }
                } else {
                    sendResponse(getResultJson(false), null,"","",SOCKET_EVENT_READFILE);
                }
            }
        });


        mSocket.on(SOCKET_EVENT_READFILE_BYTES, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                if (fileSystem != null) {
                    try {
                        JSONObject option = (JSONObject) args[0];
                        String fileName = "";
                        String filePath = "";
                        int startBytes = 0;
                        int totalBytes = 0;
                        boolean isInternal = false;

                        if (option.has(PROPERTY_EVENT_KEY_FILE_NAME)) {
                            fileName = option.getString(PROPERTY_EVENT_KEY_FILE_NAME).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_FILE_PATH)) {
                            filePath = option.getString(PROPERTY_EVENT_KEY_FILE_PATH).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_START_BYTES)) {
                            startBytes = option.getInt(PROPERTY_EVENT_KEY_START_BYTES);
                        }
                        if (option.has(PROPERTY_EVENT_KEY_TOTAL_BYTES)) {
                            totalBytes = option.getInt(PROPERTY_EVENT_KEY_TOTAL_BYTES);
                        }
                        if(option.has(PROPERTY_EVENT_KEY_ISINTERNAL)){
                            try{
                                isInternal = option.getBoolean(PROPERTY_EVENT_KEY_ISINTERNAL);
                            }catch (Exception ignore){}
                        }

                        if(isInternal){
                            readFileBytesFromInternalStorage( fileName,startBytes, totalBytes, null, filePath, SOCKET_EVENT_READFILE_BYTES);
                        }else {
                            if (filePath.startsWith("/")) {
                                filePath = filePath.replaceFirst("/", "");
                            }
                            if (filePath.endsWith("/")) {
                                filePath = filePath.substring(0, filePath.length() - 1);
                            }
                            ArrayList<String> pathList = new ArrayList<String>();
                            if (!filePath.isEmpty())
                                pathList.addAll(Arrays.asList(filePath.trim().split("/")));
                            searchReadFileBytes(pathList, fileName, startBytes, totalBytes, fileSystem.getRootDirectory(), null, filePath, SOCKET_EVENT_READFILE_BYTES);
                        }
                    } catch (Exception ignore) {
                        sendResponse(getResultJson(false), null,"","",SOCKET_EVENT_READFILE_BYTES);
                    }
                } else {
                    sendResponse(getResultJson(false), null,"","",SOCKET_EVENT_READFILE_BYTES);
                }
            }
        });


        mSocket.on(SOCKET_EVENT_WRITEFILE, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                if (fileSystem != null) {

                    try {
                        JSONObject option = (JSONObject) args[0];
                        String fileName = "";
                        String filePath = "";
                        String fileData = "";
                        boolean isInternal = false;

                        if (option.has(PROPERTY_EVENT_KEY_FILE_NAME)) {
                            fileName = option.getString(PROPERTY_EVENT_KEY_FILE_NAME).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_FILE_PATH)) {
                            filePath = option.getString(PROPERTY_EVENT_KEY_FILE_PATH).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_DATA)) {
                            fileData = option.getString(PROPERTY_EVENT_KEY_DATA);
                        }
                        if(option.has(PROPERTY_EVENT_KEY_ISINTERNAL)){
                            try{
                                isInternal = option.getBoolean(PROPERTY_EVENT_KEY_ISINTERNAL);
                            }catch (Exception ignore){}
                        }

                        if(isInternal){
                            createFileInInternalStorage( fileName, fileData,  null, filePath, SOCKET_EVENT_WRITEFILE);
                        }else {
                            if (filePath.startsWith("/")) {
                                filePath = filePath.replaceFirst("/", "");
                            }
                            if (filePath.endsWith("/")) {
                                filePath = filePath.substring(0, filePath.length() - 1);
                            }
                            ArrayList<String> pathList = new ArrayList<String>();
                            if (!filePath.isEmpty())
                                pathList.addAll(Arrays.asList(filePath.trim().split("/")));
                            searchWriteFile(pathList, fileName, fileData, fileSystem.getRootDirectory(), null, filePath, SOCKET_EVENT_WRITEFILE);
                        }
                    } catch (Exception ignore) {
                        sendResponse(getResultJson(false), null,"","",SOCKET_EVENT_WRITEFILE);
                    }
                } else {
                    sendResponse(getResultJson(false), null,"","",SOCKET_EVENT_WRITEFILE);
                }
            }
        });

        mSocket.on(SOCKET_EVENT_INSERTINTOFILE, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                if (fileSystem != null) {

                    try {
                        JSONObject option = (JSONObject) args[0];
                        String fileName = "";
                        String filePath = "";
                        String fileData = "";
                        boolean isInternal = false;
                        int startPosition = 0;
                        int totalLength = 0;

                        if (option.has(PROPERTY_EVENT_KEY_FILE_NAME)) {
                            fileName = option.getString(PROPERTY_EVENT_KEY_FILE_NAME).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_FILE_PATH)) {
                            filePath = option.getString(PROPERTY_EVENT_KEY_FILE_PATH).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_DATA)) {
                            fileData = option.getString(PROPERTY_EVENT_KEY_DATA);
                        }
                        if(option.has(PROPERTY_EVENT_KEY_ISINTERNAL)){
                            try{
                                isInternal = option.getBoolean(PROPERTY_EVENT_KEY_ISINTERNAL);
                            }catch (Exception ignore){}
                        }

                        if(option.has(PROPERTY_EVENT_KEY_START_POSITION)){
                            try{
                                startPosition = option.getInt(PROPERTY_EVENT_KEY_START_POSITION);
                            }catch (Exception ignore){}
                        }

                        if(option.has(PROPERTY_EVENT_KEY_ISINTERNAL)){
                            try{
                                totalLength = option.getInt(PROPERTY_EVENT_KEY_TOTAL_LENGTH);
                            }catch (Exception ignore){}
                        }

                        if(totalLength <=0){
                            sendResponse(getResultJson(false), null,"","",SOCKET_EVENT_WRITEFILE);
                        }

                        if(isInternal){
                            insertFileDataInInternalStorage( fileName, fileData,startPosition,totalLength, null, filePath, SOCKET_EVENT_INSERTINTOFILE);
                        }else {
                            if (filePath.startsWith("/")) {
                                filePath = filePath.replaceFirst("/", "");
                            }
                            if (filePath.endsWith("/")) {
                                filePath = filePath.substring(0, filePath.length() - 1);
                            }
                            ArrayList<String> pathList = new ArrayList<String>();
                            if (!filePath.isEmpty())
                                pathList.addAll(Arrays.asList(filePath.trim().split("/")));
                            searchInsertDataIntoFile(pathList, fileName, fileData,startPosition,totalLength, fileSystem.getRootDirectory(), null, filePath, SOCKET_EVENT_INSERTINTOFILE);

                        }
                    } catch (Exception ignore) {
                        sendResponse(getResultJson(false), null,"","",SOCKET_EVENT_WRITEFILE);
                    }
                } else {
                    sendResponse(getResultJson(false), null,"","",SOCKET_EVENT_WRITEFILE);
                }
            }
        });


        mSocket.on(SOCKET_EVENT_FILEEXIST, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                if (fileSystem != null) {

                    try {
                        JSONObject option = (JSONObject) args[0];
                        String fileName = "";
                        String filePath = "";
                        boolean isInternal = false;

                        if (option.has(PROPERTY_EVENT_KEY_FILE_NAME)) {
                            fileName = option.getString(PROPERTY_EVENT_KEY_FILE_NAME).trim();
                        }
                        if (option.has(PROPERTY_EVENT_KEY_FILE_PATH)) {
                            filePath = option.getString(PROPERTY_EVENT_KEY_FILE_PATH).trim();
                        }
                        if(option.has(PROPERTY_EVENT_KEY_ISINTERNAL)){
                            try{
                                isInternal = option.getBoolean(PROPERTY_EVENT_KEY_ISINTERNAL);
                            }catch (Exception ignore){}
                        }

                        if(isInternal){
                                    isFileExistInInternalStorage( fileName, null, filePath, SOCKET_EVENT_FILEEXIST);
                        }else {
                            if (filePath.startsWith("/")) {
                                filePath = filePath.replaceFirst("/", "");
                            }
                            if (filePath.endsWith("/")) {
                                filePath = filePath.substring(0, filePath.length() - 1);
                            }
                            ArrayList<String> pathList = new ArrayList<String>();
                            if (!filePath.isEmpty())
                                pathList.addAll(Arrays.asList(filePath.trim().split("/")));
                            searchFileExist(pathList, fileName, fileSystem.getRootDirectory(), null, filePath, SOCKET_EVENT_FILEEXIST);
                        }
                    } catch (Exception ignore) {
                        sendResponse(getResultJson(false), null,"","",SOCKET_EVENT_FILEEXIST);
                    }
                } else {
                    sendResponse(getResultJson(false), null,"","",SOCKET_EVENT_FILEEXIST);
                }
            }
        });


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

                    try {
                        if (!mSocket.connected()) {
                            mSocket = mSocket.connect();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (ACTION_USB_PERMISSION.equals(action)) {

                        mSocket.emit(SOCKET_EVENT_IGNITION, jsonObject);

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


    void searchCreateFile(ArrayList<String> filePathList, String fileName, String data, UsbFile parentFile, CallbackContext callbackContext,String filePath,String socketEvent) {
        try {
            if (filePathList.isEmpty()) {
                UsbFile newFile = createFile(fileSystem.getRootDirectory(), fileName);
                if (newFile != null) {
                    if (data != null) {
                        writeFile(newFile, data);
                    }
                    sendResponse(getResultJson(true), callbackContext,filePath,fileName,socketEvent);
                    return;
                } else {
                    sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
                    return;
                }

            }

            for (UsbFile file : parentFile.listFiles()) {
                if (!filePathList.isEmpty() && file.isDirectory() && file.getName().equals(filePathList.get(0))) {
                    if (filePathList.size() == 1) {
                        UsbFile newFile = createFile(file, fileName);
                        if (newFile != null) {
                            writeFile(newFile, data);
                            sendResponse(getResultJson(true), callbackContext,filePath,fileName,socketEvent);
                        } else {
                            sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
                        }
                        return;
                    } else if (filePathList.size() >= 1) {
                        filePathList.remove(0);
                        searchCreateFile(filePathList, fileName, data, file, callbackContext,filePath,socketEvent);
                        return;
                    }
                    break;
                }
            }
            UsbFile tmpFile = parentFile;
            if (filePathList.size() >= 1) {
                for (int i = 0; i < filePathList.size(); i++) {

                    tmpFile = tmpFile.createDirectory(filePathList.get(i));
                    if (i == filePathList.size() - 1) {
                        ArrayList<String> tmpFilePathList = new ArrayList<String>();
                        tmpFilePathList.add(filePathList.get(i));
                        searchCreateFile(tmpFilePathList, fileName, data, tmpFile.getParent(), callbackContext,filePath,socketEvent);
                        return;

                    }
                }
            }


        } catch (Exception ex) {
        }
        sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
    }

    void searchDeleteFile(ArrayList<String> filePathList, String fileName, UsbFile parentFile, CallbackContext callbackContext,String filePath,String socketEvent) {
        try {
            for (UsbFile file : parentFile.listFiles()) {

                if (!file.isDirectory() && filePathList.isEmpty() && file.getName().equals(fileName)) {
                    if (deleteFile(file)) {
                        sendResponse(getResultJson(true), callbackContext,filePath,fileName,socketEvent);
                    } else {
                        sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
                    }
                    return;
                }

                if (!filePathList.isEmpty() && file.getName().equals(filePathList.get(0))) {
                    if (filePathList.size() >= 1) {
                        filePathList.remove(0);
                        searchDeleteFile(filePathList, fileName, file, callbackContext,filePath,socketEvent);
                        return;
                    }
                    break;
                }
            }
        } catch (Exception ex) {
        }
        sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
    }

    void searchReadFile(ArrayList<String> filePathList, String fileName, UsbFile parentFile, CallbackContext callbackContext,String filePath,String socketEvent) {
        try {
            for (UsbFile file : parentFile.listFiles()) {

                if (!file.isDirectory() && filePathList.isEmpty() && file.getName().equals(fileName)) {
                    String fileData = readFile(file);
                    if (fileData != null) {
                        JSONObject response = getResultJson(true);
                        try {
                            response.put("data", fileData);
                        } catch (Exception ignore) {
                        }
                        sendResponse(response, callbackContext,filePath,fileName,socketEvent);
                    } else {
                        sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
                    }
                    return;
                } else if (!filePathList.isEmpty() && file.getName().equals(filePathList.get(0))) {
                    if (filePathList.size() >= 1) {
                        filePathList.remove(0);
                        searchReadFile(filePathList, fileName, file, callbackContext,filePath,socketEvent);
                        return;
                    }
                    break;
                }
            }
        } catch (Exception ex) {
        }
        sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
    }

    //(pathList,fileName, fileSystem.getRootDirectory(), callbackContext);

    void searchReadFileBytes(ArrayList<String> filePathList, String fileName, int startBytes, int totalBytes, UsbFile parentFile, CallbackContext callbackContext,String filePath,String socketEvent) {
        try {
            for (UsbFile file : parentFile.listFiles()) {

                if (!file.isDirectory() && filePathList.isEmpty() && file.getName().equals(fileName)) {
                    byte[] fileData = readFileBytes(file, startBytes, totalBytes);
                    if (fileData != null) {
                        JSONObject response = getResultJson(true);
                        try {

                         //   response.put("data", Base64.encodeToString(fileData, 0));
                            response.put("data",fileData);
                        } catch (Exception ignore) {
                        }
                        sendResponse(response, callbackContext,filePath,fileName,socketEvent);
                    } else {
                        sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
                    }
                    return;
                } else if (!filePathList.isEmpty() && file.getName().equals(filePathList.get(0))) {
                    if (filePathList.size() >= 1) {
                        filePathList.remove(0);
                        searchReadFileBytes(filePathList, fileName, startBytes, totalBytes, file, callbackContext,filePath,socketEvent);
                        return;
                    }
                    break;
                }
            }
        } catch (Exception ex) {
        }
        sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
    }

    void searchWriteFile(ArrayList<String> filePathList, String fileName, String data, UsbFile parentFile, CallbackContext callbackContext,String filePath,String socketEvent) {

        try {
            for (UsbFile file : parentFile.listFiles()) {

                if (!file.isDirectory() && filePathList.isEmpty() && file.getName().equals(fileName)) {
                    if (writeFile(file, data) != null) {
                        sendResponse(getResultJson(true), callbackContext,filePath,fileName,socketEvent);
                    } else {
                        sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
                    }
                    return;
                } else if (!filePathList.isEmpty() && file.getName().equals(filePathList.get(0))) {
                    if (filePathList.size() >= 1) {
                        filePathList.remove(0);
                        searchWriteFile(filePathList, fileName, data, file, callbackContext,filePath,socketEvent);
                        return;
                    }
                    break;
                }
            }
        } catch (Exception ex) {
        }
        sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);

    }


    void searchInsertDataIntoFile(ArrayList<String> filePathList, String fileName, String data,int startPosition,int totalLength, UsbFile parentFile, CallbackContext callbackContext,String filePath,String socketEvent) {
        try {
            for (UsbFile file : parentFile.listFiles()) {

                if (!file.isDirectory() && filePathList.isEmpty() && file.getName().equals(fileName)) {
                    if (insertDataIntoFile(file, data,startPosition,totalLength) != null) {
                        sendResponse(getResultJson(true), callbackContext,filePath,fileName,socketEvent);
                    } else {
                        sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
                    }
                    return;
                } else if (!filePathList.isEmpty() && file.getName().equals(filePathList.get(0))) {
                    if (filePathList.size() >= 1) {
                        filePathList.remove(0);
                        searchInsertDataIntoFile(filePathList, fileName, data,startPosition,totalLength, file, callbackContext,filePath,socketEvent);
                        return;
                    }
                    break;
                }
            }
        } catch (Exception ex) {
        }
        sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);

    }

    void searchFileExist(ArrayList<String> filePathList, String fileName, UsbFile parentFile, CallbackContext callbackContext,String filePath,String socketEvent) {
        try {
            for (UsbFile file : parentFile.listFiles()) {

                if (!file.isDirectory() && filePathList.isEmpty() && file.getName().equals(fileName)) {
                    sendResponse(getResultJson(true), callbackContext,filePath,fileName,socketEvent);
                    return;
                }

                if (!filePathList.isEmpty() && file.getName().equals(filePathList.get(0))) {
                    if (filePathList.size() >= 1) {
                        filePathList.remove(0);
                        searchFileExist(filePathList, fileName, file, callbackContext,filePath,socketEvent);
                        return;
                    }
                    break;
                }
            }
        } catch (Exception ex) {
        }
        sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
    }

    byte[] readFileBytes(UsbFile file, int startBytes, int totalBytes) {
        try {
            if (!file.isDirectory()) {
                InputStream is = new UsbFileInputStream(file);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                int bufferSize = 600;
                int readCycleSize = totalBytes/bufferSize;
                int reminderSize = totalBytes%bufferSize;

                byte[] bufferCycle = new byte[bufferSize];
                byte[] reminderBuffer = new byte[reminderSize];

                is.skip(startBytes);

                 while (readCycleSize>0){
                        is.read(bufferCycle,0,bufferSize);
                        bos.write(bufferCycle);
                        bufferCycle = new byte[bufferSize];
                       readCycleSize--;
                   }
                if(reminderSize>0){
                    is.read(reminderBuffer,0,reminderSize);
                    bos.write(reminderBuffer);
                }

                is.close();
                return bos.toByteArray();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    String readFile(UsbFile file) {
        try {
            if (!file.isDirectory()) {
                InputStream is = new UsbFileInputStream(file);
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

    JSONObject getResultJson(boolean isSuccess) {
        JSONObject jsonData = new JSONObject();
        try {
            jsonData.put("status", isSuccess);
        } catch (Exception e) {
        }
        return jsonData;
    }

    void sendResponse(JSONObject jsonObject, CallbackContext callbackContext, String filePath, String fileName, String socketEvent) {
        try {
            jsonObject.put(PROPERTY_EVENT_KEY_FILE_PATH, filePath);
            jsonObject.put(PROPERTY_EVENT_KEY_FILE_NAME, fileName);
        } catch (Exception ignore) {
        }
        if (callbackContext != null) {
            PluginResult fileResult = new PluginResult(PluginResult.Status.OK, jsonObject);
            fileResult.setKeepCallback(true);
            callbackContext.sendPluginResult(fileResult);
        }
        if (mSocket.connected() && socketEvent != null && !socketEvent.equals("")) {
            mSocket.emit(socketEvent, jsonObject);
        }
    }

    UsbFile createFile(UsbFile file, String fileName) {

        try {
            for (UsbFile item : file.listFiles()) {
                if (item.getName().equals(fileName)) {
                    if (!item.isDirectory()) {
                        deleteFile(item);
                    }
                }
            }

        } catch (Exception ignore) {
        }

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
    
    UsbFile insertDataIntoFile(UsbFile file, String fileData,int startPosition, int totalLength) {
        try {
            if (!file.isDirectory()) {
                
                file.write(startPosition, ByteBuffer.wrap(fileData.getBytes()));
                file.close();
                return file;
            }
        } catch (Exception ex) {

        }
        return null;
    }
    
    void createFileInInternalStorage(String fileName, String data, CallbackContext callbackContext,String filePath,String socketEvent) {
        try {
            if (filePath.startsWith("/")) {
                filePath = filePath.replaceFirst("/", "");
            }
            if (!filePath.endsWith("/")) {
                filePath = filePath + "/";
            }
            File tmpDir = new File(appContext.getCacheDir() + filePath);
            if(!tmpDir.exists()){
                tmpDir.mkdirs();
            }


            File tmpFile = new File(appContext.getCacheDir() + filePath + fileName);
            if (tmpFile.exists()) {
                tmpFile.delete();
            }else{

            }

            tmpFile.createNewFile();
            FileOutputStream fos = new FileOutputStream(tmpFile);

            fos.write(data.getBytes(StandardCharsets.UTF_8));
            sendResponse(getResultJson(true), callbackContext,filePath,fileName,socketEvent);
        }catch (Exception ignore){
            sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
        }

    }


    void readFileFromInternalStorage(String fileName, CallbackContext callbackContext,String filePath,String socketEvent) {
        try {
            if (filePath.startsWith("/")) {
                filePath = filePath.replaceFirst("/", "");
            }
            if (!filePath.endsWith("/")) {
                filePath = filePath + "/";
            }
            File tmpFile = new File(appContext.getCacheDir() + filePath + fileName);
            JSONObject response = getResultJson(true);
            if (tmpFile.exists()) {
                FileInputStream fis = new FileInputStream(tmpFile);
                String fileData = convertStreamToString(fis);
                response.put("data", fileData);

                sendResponse(response, callbackContext,filePath,fileName,socketEvent);
            }else{
                sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
            }
        }catch (Exception ignore){
            sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
        }
    }


    void deleteFileFromInternalStorage(String fileName, CallbackContext callbackContext,String filePath,String socketEvent) {
        try {
            if (filePath.startsWith("/")) {
                filePath = filePath.replaceFirst("/", "");
            }
            if (!filePath.endsWith("/")) {
                filePath = filePath + "/";
            }
            File tmpFile = new File(appContext.getCacheDir() + filePath + fileName);
            if (tmpFile.exists()) {
                tmpFile.delete();
            }

            sendResponse(getResultJson(true), callbackContext,filePath,fileName,socketEvent);
        }catch (Exception ignore){
            sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
        }
    }

    void isFileExistInInternalStorage(String fileName, CallbackContext callbackContext,String filePath,String socketEvent) {
        try {
            if (filePath.startsWith("/")) {
                filePath = filePath.replaceFirst("/", "");
            }
            if (!filePath.endsWith("/")) {
                filePath = filePath + "/";
            }
            File tmpFile = new File(appContext.getCacheDir() + filePath + fileName);
            if (tmpFile.exists()) {
                sendResponse(getResultJson(true), callbackContext,filePath,fileName,socketEvent);
            }else{
                sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
            }


        }catch (Exception ignore){
            sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
        }
    }

    void readFileBytesFromInternalStorage(String fileName,int startBytes,int totalBytes, CallbackContext callbackContext,String filePath,String socketEvent){
        try {
            if (filePath.startsWith("/")) {
                filePath = filePath.replaceFirst("/", "");
            }
            if (!filePath.endsWith("/")) {
                filePath = filePath + "/";
            }
            File tmpFile = new File(appContext.getCacheDir() + filePath + fileName);
            JSONObject response = getResultJson(true);
            if (tmpFile.exists()) {
                FileInputStream fis = new FileInputStream(tmpFile);

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                int bufferSize = 600;
                int readCycleSize = totalBytes/bufferSize;
                int reminderSize = totalBytes%bufferSize;

                byte[] bufferCycle = new byte[bufferSize];
                byte[] reminderBuffer = new byte[reminderSize];

                fis.skip(startBytes);

                while (readCycleSize>0){
                    fis.read(bufferCycle,0,bufferSize);
                    bos.write(bufferCycle);
                    bufferCycle = new byte[bufferSize];
                    readCycleSize--;
                }
                if(reminderSize>0){
                    fis.read(reminderBuffer,0,reminderSize);
                    bos.write(reminderBuffer);
                }


                response.put("data", bos.toByteArray());

                sendResponse(response, callbackContext,filePath,fileName,socketEvent);
            }else{
                sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
            }
        }catch (Exception ignore){
            sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
        }
    }


    void insertFileDataInInternalStorage(String fileName, String data,int startPosition,int totalLength, CallbackContext callbackContext,String filePath,String socketEvent) {
        try {
            if (filePath.startsWith("/")) {
                filePath = filePath.replaceFirst("/", "");
            }
            if (!filePath.endsWith("/")) {
                filePath = filePath + "/";
            }
            File tmpDir = new File(appContext.getCacheDir() + filePath);
            if(!tmpDir.exists()){
                sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
                return;
            }


            File tmpFile = new File(appContext.getCacheDir() + filePath + fileName);
            if (!tmpFile.exists()) {
                sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
                return;
            }

            RandomAccessFile raf ;

            try {
                raf = new RandomAccessFile(tmpFile, "rw");
                raf.seek(startPosition);
                raf.write(data.getBytes(StandardCharsets.UTF_8));
                sendResponse(getResultJson(true), callbackContext,filePath,fileName,socketEvent);
            }
            catch (Exception ioe) {
                sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
            }

        }catch (Exception ignore){
            sendResponse(getResultJson(false), callbackContext,filePath,fileName,socketEvent);
        }

    }

}
