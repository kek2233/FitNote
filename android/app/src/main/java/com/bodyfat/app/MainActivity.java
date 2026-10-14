package com.bodyfat.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final int REQ_SELECT_FILE = 9001;
    private static final String PROMPT = "你是食物识别助手。识别图片中的食物，返回 JSON 数组，每项形如 {\"name\":\"食物名\",\"grams\":估重克数,\"kcal\":每100克热量}。无法识别时返回 []。只返回 JSON，不要多余文字。";

    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraUri;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (MainActivity.this.filePathCallback != null) MainActivity.this.filePathCallback.onReceiveValue(null);
                MainActivity.this.filePathCallback = callback;
                Intent takePicture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (takePicture.resolveActivity(getPackageManager()) != null) {
                    try {
                        File dir = new File(getFilesDir(), "photos");
                        if (!dir.exists()) dir.mkdirs();
                        File f = new File(dir, "photo_" + System.currentTimeMillis() + ".jpg");
                        f.createNewFile();
                        cameraUri = Uri.parse("content://" + FileProvider.AUTHORITY + "/" + f.getName());
                        takePicture.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                        takePicture.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) { cameraUri = null; }
                }
                Intent gallery = new Intent(Intent.ACTION_GET_CONTENT);
                gallery.addCategory(Intent.CATEGORY_OPENABLE);
                gallery.setType("image/*");
                Intent chooser = Intent.createChooser(gallery, "选择图片");
                if (cameraUri != null) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{takePicture});
                startActivityForResult(chooser, REQ_SELECT_FILE);
                return true;
            }
        });
        web.addJavascriptInterface(new AIBridge(), "AndroidAI");
        web.addJavascriptInterface(new HostBridge(), "Android");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_SELECT_FILE) {
            Uri result = null;
            if (resultCode == RESULT_OK) {
                if (data != null && data.getData() != null) result = data.getData();
                else if (cameraUri != null) result = cameraUri;
            }
            if (filePathCallback != null) { filePathCallback.onReceiveValue(null); filePathCallback = null; }
            cameraUri = null;
            if (result != null) deliverImage(result);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void deliverImage(final Uri uri) {
        new Thread(new Runnable() {
            @Override public void run() {
                final String dataUrl = loadAndCompress(uri);
                if (dataUrl == null) {
                    runOnUiThread(new Runnable() { @Override public void run() {
                        Toast.makeText(MainActivity.this, "读取图片失败", Toast.LENGTH_SHORT).show();
                    }});
                    return;
                }
                runOnUiThread(new Runnable() { @Override public void run() {
                    web.evaluateJavascript("App.onPhotoData('" + dataUrl + "')", null);
                }});
            }
        }).start();
    }

    private String loadAndCompress(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, o);
            if (in != null) in.close();
            int sample = 1, maxDim = 1024;
            while ((o.outWidth / sample) > maxDim || (o.outHeight / sample) > maxDim) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            in = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(in, null, o);
            if (in != null) in.close();
            if (bmp == null) return null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 82, bos);
            bmp.recycle();
            return "data:image/jpeg;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    private class AIBridge {
        @JavascriptInterface
        public String recognize(String dataUrl, String baseUrl, String model, String apiKey) {
            try {
                String url = baseUrl;
                if (!url.endsWith("/chat/completions")) url = url.replaceAll("/+$", "") + "/chat/completions";
                JSONObject text = new JSONObject().put("type", "text").put("text", PROMPT);
                JSONObject image = new JSONObject().put("type", "image_url").put("image_url", new JSONObject().put("url", dataUrl));
                JSONArray content = new JSONArray().put(text).put(image);
                JSONObject msg = new JSONObject().put("role", "user").put("content", content);
                JSONArray messages = new JSONArray().put(msg);
                JSONObject body = new JSONObject().put("model", (model == null || model.isEmpty()) ? "gpt-4o-mini" : model)
                    .put("messages", messages).put("max_tokens", 600).put("temperature", 0);
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();
                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                String resp = readAll(is);
                JSONObject j = new JSONObject(resp);
                return j.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
            } catch (Exception e) {
                return "__ERR__" + e.getMessage();
            }
        }
    }

    /**
     * 原生能力桥：把文本（备份 JSON）写入下载目录。
     * - Android 10+ (API 29)：走 MediaStore.Downloads，写入公共「下载」，无需任何权限
     * - 更低版本：写应用私有 Download 目录（同样不需要 WRITE_EXTERNAL_STORAGE）
     */
    private class HostBridge {
        @JavascriptInterface
        public void saveText(String name, String text) {
            String safeName = (name == null || name.trim().isEmpty()) ? "bodyfat-backup.json" : name;
            byte[] bytes = (text == null ? "" : text).getBytes(java.nio.charset.Charset.forName("UTF-8"));
            String resultPath = null;
            OutputStream os = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                    cv.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                    cv.put(MediaStore.Downloads.IS_PENDING, 1);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (uri == null) throw new Exception("无法创建下载文件");
                    os = getContentResolver().openOutputStream(uri);
                    if (os == null) throw new Exception("无法打开输出流");
                    os.write(bytes);
                    os.flush();
                    try { os.close(); os = null; } catch (Exception ignored) {}
                    ContentValues done = new ContentValues();
                    done.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(uri, done, null, null);
                    resultPath = safeName;
                } else {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) throw new Exception("存储不可用");
                    if (!dir.exists()) dir.mkdirs();
                    File f = new File(dir, safeName);
                    FileOutputStream fos = new FileOutputStream(f);
                    fos.write(bytes);
                    fos.flush();
                    fos.close();
                    resultPath = f.getAbsolutePath();
                }
                final String msg = "已保存到「下载」：" + safeName;
                runOnUiThread(new Runnable() { @Override public void run() {
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                }});
            } catch (Exception e) {
                if (os != null) { try { os.close(); } catch (Exception ignored) {} }
                final String msg = "保存失败：" + e.getMessage();
                runOnUiThread(new Runnable() { @Override public void run() {
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                }});
            }
        }
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return new String(out.toByteArray(), "UTF-8");
    }
}
