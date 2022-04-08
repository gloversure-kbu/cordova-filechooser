package com.megster.cordova;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;

public class FileChooser extends CordovaPlugin {

    private static final String TAG = "FileChooser";
    private static final String ACTION_OPEN = "open";
    private static final int PICK_FILE_REQUEST = 1;
    private static final int PICK_INSECURE_REQUEST = 2;

    public static final String MIME = "mime";
    public static final String INSECURE = "insecure";

    CallbackContext callback;

    @Override
    public boolean execute(String action, JSONArray inputs, CallbackContext callbackContext) throws JSONException {

        if (action.equals(ACTION_OPEN)) {
            JSONObject filters = inputs.optJSONObject(0);
            chooseFile(filters, callbackContext);
            return true;
        }

        return false;
    }

    public void chooseFile(JSONObject filter, CallbackContext callbackContext) {
        String uri_filter = filter.has(MIME) ? filter.optString(MIME) : "*/*";

        // type and title should be configurable

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(uri_filter);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);

        Intent chooser = Intent.createChooser(intent, "Select File");
        int requestType = (filter.has(INSECURE) && filter.optBoolean(INSECURE, true)) ? PICK_INSECURE_REQUEST : PICK_FILE_REQUEST;
        cordova.startActivityForResult(this, chooser, requestType);

        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        callback = callbackContext;
        callbackContext.sendPluginResult(pluginResult);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if ((requestCode == PICK_FILE_REQUEST || requestCode == PICK_INSECURE_REQUEST) && callback != null) {

            if (resultCode == Activity.RESULT_OK) {

                Uri uri = data.getData();
                if (requestCode == PICK_INSECURE_REQUEST) {
                    uri = this.getFilePathFromUri(uri);
                }

                if (uri != null) {

                    Log.w(TAG, uri.toString());
                    callback.success(uri.toString());

                } else {

                    callback.error("File uri was null");

                }

            } else if (resultCode == Activity.RESULT_CANCELED) {
                // keep this string the same as in iOS document picker plugin
                // https://github.com/iampossible/Cordova-DocPicker
                callback.error("User canceled.");
            } else {

                callback.error(resultCode);
            }
        }
    }

    public static Uri getFilePathFromUri(Uri uri) throws JSONException {
        String fileName = getFileName(uri);
        File file = new File(myContext.getExternalCacheDir(), fileName);
        file.createNewFile();
        try (OutputStream outputStream = new FileOutputStream(file);
             InputStream inputStream = myContext.getContentResolver().openInputStream(uri)) {
            copyStream(inputStream, outputStream); //Simply reads input to output stream
            outputStream.flush();
        }
        return Uri.fromFile(file);
    }

    public static String getFileName(Uri uri) {
        String fileName = getFileNameFromCursor(uri);
        if (fileName == null) {
            String fileExtension = getFileExtension(uri);
            fileName = "temp_file" + (fileExtension != null ? "." + fileExtension : "");
        } else if (!fileName.contains(".")) {
            String fileExtension = getFileExtension(uri);
            fileName = fileName + "." + fileExtension;
        }
        return fileName;
    }

    public static String getFileExtension(Uri uri) {
        String fileType = myContext.getContentResolver().getType(uri);
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(fileType);
    }

    public static String getFileNameFromCursor(Uri uri) {
        Cursor fileCursor = myContext.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
        String fileName = null;
        if (fileCursor != null && fileCursor.moveToFirst()) {
            int cIndex = fileCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (cIndex != -1) {
                fileName = fileCursor.getString(cIndex);
            }
        }
        return fileName;
    }

    public static void copyStream(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        try {
            OutputStream out = new FileOutputStream(dst);
            try {
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }
}
