package com.bodyfat.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * FitNote App 外壳（v2.0.0）
 *
 * 设计要点（针对真机「装上但一闪就退」的加固）：
 * 1) 崩溃循环检测：每次启动先把 launch_ok 置 false；页面 JS 就绪后调 Android.onReady() 才置 true。
 *    若上次启动没走到就绪（闪退），本次启动强制进入「安全模式」——软件渲染 + 关闭液态玻璃位移特效。
 * 2) 渲染进程自愈：WebView 渲染进程崩溃（GPU 滤镜类问题的典型死法）时 onRenderProcessGone 返回 true
 *    并自行重建 WebView（软件渲染 + ?safe=1 重载），不再让整个 App 进程被杀。
 * 3) 启动轨迹日志：onCreate → loadUrl → onPageStarted → onPageFinished → JS onReady 每步都落盘
 *    （应用外部目录 + 「下载」目录各一份），App 若仍打不开，用户可在设置页一键导出诊断日志。
 * 4) 特效开关：液态玻璃（feDisplacementMap + 硬件加速）默认关闭，用户在设置里手动开启；
 *    开启后若崩溃，下一次启动自动回到安全模式。
 */
public class MainActivity extends Activity {
    private static final int REQ_SELECT_FILE = 9001;
    private static final String PROMPT = "你是食物识别助手。识别图片中的食物，返回 JSON 数组，每项形如 {\"name\":\"食物名\",\"grams\":估重克数,\"kcal\":每100克热量}。无法识别时返回 []。只返回 JSON，不要多余文字。";

    private static final String PREFS = "bodyfat";
    private static final String K_ATTEMPTED = "attempted";   // 是否启动过（用来区分首次安装与上次崩溃）
    private static final String K_LAUNCH_OK = "launch_ok";   // 上次启动是否成功走到 JS 就绪
    private static final String K_GLASS = "glass";           // 液态玻璃特效偏好（默认开）
    private static final String K_FORCE_SAFE = "force_safe"; // 上次启动异常 → 本次强制安全模式

    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraUri;
    private boolean fileFallbackTried = false;
    private boolean currentGlass = true;   // 本次启动实际是否开启了特效（供 JS 查询真实状态）

    private final StringBuilder startupLog = new StringBuilder();
    private long t0 = 0;
    private Uri diagUri = null; // 「下载」目录里的 bodyfat-startup.txt

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        t0 = System.currentTimeMillis();
        log("onCreate 开始");
        super.onCreate(savedInstanceState);
        installCrashLogger();

        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean attempted = sp.getBoolean(K_ATTEMPTED, false);       // 之前启动过吗（区分「首次安装」与「上次崩了」）
        boolean prevOk = sp.getBoolean(K_LAUNCH_OK, false);          // 上次是否走到 JS 就绪
        boolean glassPref = sp.getBoolean(K_GLASS, true);            // 特效偏好：默认开启（硬件加速 + 全特效）
        boolean crashLoop = attempted && !prevOk;                    // 上次启动了却没走完 = 真的崩过
        currentGlass = glassPref && !crashLoop;                      // 只有真崩过才临时降级一次
        sp.edit().putBoolean(K_ATTEMPTED, true).putBoolean(K_LAUNCH_OK, false).apply();

        web = buildWebView();
        setContentView(web);
        if (!currentGlass) web.setLayerType(View.LAYER_TYPE_SOFTWARE, null); // 仅在崩溃降级/用户手动关闭时软件渲染

        String url = "file:///android_asset/index.html" + (currentGlass ? "" : "?safe=1");
        log("loadUrl " + url + "（attempted=" + attempted + " prevOk=" + prevOk + " crashLoop=" + crashLoop + "）");
        web.loadUrl(url);
    }

    /** 统一装配 WebView：设置 / 客户端 / JS 桥。 */
    @SuppressLint("SetJavaScriptEnabled")
    private WebView buildWebView() {
        final WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        w.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // 主文档加载失败时回退一次纯 file://，保证界面一定能打开
                if (!fileFallbackTried && request != null && request.isForMainFrame()) {
                    fileFallbackTried = true;
                    log("onReceivedError 主文档 → 回退纯 file://");
                    view.loadUrl("file:///android_asset/index.html");
                    return;
                }
                super.onReceivedError(view, request, error);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                log("onPageStarted " + url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                log("onPageFinished " + url);
            }

            /**
             * 渲染进程消失（GPU 驱动/滤镜崩溃的典型表现）：返回 true 表示 App 自行恢复，
             * 阻止系统默认的「杀进程」行为；否则用户看到的就是「一闪就退」。
             */
            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                log("onRenderProcessGone：渲染进程消失，切换安全模式自愈");
                try {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putBoolean(K_FORCE_SAFE, true)
                            .putBoolean(K_ATTEMPTED, true)
                            .putBoolean(K_LAUNCH_OK, false)   // 下次启动判为「上次崩过」→ 自动安全模式
                            .apply();
                } catch (Throwable ignore) {}
                runOnUiThread(new Runnable() {
                    @Override public void run() { recoverSafeMode(); }
                });
                return true;
            }
        });
        w.setWebChromeClient(new WebChromeClient() {
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
        w.addJavascriptInterface(new AIBridge(), "AndroidAI");
        w.addJavascriptInterface(new HostBridge(), "Android");
        return w;
    }

    /** 渲染进程崩溃后自愈：销毁旧 WebView，软件渲染 + 安全模式重载。 */
    private void recoverSafeMode() {
        log("recoverSafeMode：重建 WebView");
        try {
            if (web != null) {
                ViewGroup parent = (ViewGroup) web.getParent();
                if (parent != null) parent.removeView(web);
                web.destroy();
            }
        } catch (Throwable ignore) {}
        web = buildWebView();
        currentGlass = false;
        web.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentView(web);
        fileFallbackTried = false;
        web.loadUrl("file:///android_asset/index.html?safe=1");
        Toast.makeText(this, "检测到画面异常，已自动切换安全模式", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        try { if (web != null) web.destroy(); } catch (Throwable ignore) {}
        super.onDestroy();
    }

    /* ------------------------------ 启动轨迹日志 ------------------------------ */

    private void log(String msg) {
        try {
            String line = (System.currentTimeMillis() - t0) + "ms  " + msg + "\n";
            startupLog.append(line);
            File dir = getExternalFilesDir(null);
            if (dir != null) writeText(new File(dir, "bodyfat-startup.txt"), startupLog.toString());
            writeDiagToDownloads(startupLog.toString());
        } catch (Throwable ignore) {}
    }

    /** 把启动轨迹写到公共「下载」目录，方便用户直接抓取发回。 */
    private void writeDiagToDownloads(String text) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
            if (diagUri == null) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, "bodyfat-startup.txt");
                cv.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                cv.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) return;
                diagUri = uri;
            }
            OutputStream os = getContentResolver().openOutputStream(diagUri, "w");
            if (os == null) return;
            os.write(text.getBytes("UTF-8"));
            os.flush();
            os.close();
        } catch (Throwable ignore) {}
    }

    /** 安装崩溃捕获：把堆栈写到「下载/bodyfat-crash.txt」与应用外部目录；后台线程异常不再杀进程。 */
    private void installCrashLogger() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try { saveCrash(t, e); } catch (Throwable ignore) {}
                if (!"main".equals(t.getName())) {
                    return; // 后台线程崩溃：记录但不杀进程，App 继续可用
                }
                if (previous != null) previous.uncaughtException(t, e);
            }
        });
    }

    private void saveCrash(Thread t, Throwable e) {
        StringBuilder sb = new StringBuilder();
        sb.append("FitNote 崩溃日志\n");
        sb.append("时间: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(new java.util.Date())).append("\n");
        sb.append("线程: ").append(t.getName()).append("\n");
        sb.append("异常: ").append(e.toString()).append("\n");
        for (StackTraceElement s : e.getStackTrace()) sb.append("    at ").append(s).append("\n");
        Throwable c = e.getCause();
        while (c != null) { sb.append("  由引起: ").append(c.toString()).append("\n"); c = c.getCause(); }
        final String text = sb.toString();
        try {
            File dir = getExternalFilesDir(null);
            if (dir != null) writeText(new File(dir, "bodyfat-crash.txt"), text);
        } catch (Throwable ignore) {}
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, "bodyfat-crash.txt");
                cv.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                cv.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri != null) {
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    os.write(text.getBytes("UTF-8"));
                    os.close();
                    ContentValues done = new ContentValues();
                    done.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(uri, done, null, null);
                }
            }
        } catch (Throwable ignore) {}
        try {
            final String msg = "App 崩溃：" + e.toString();
            new android.os.Handler(getMainLooper()).post(new Runnable() {
                @Override public void run() {
                    Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                }
            });
        } catch (Throwable ignore) {}
    }

    private void writeText(File f, String text) throws IOException {
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(text.getBytes("UTF-8"));
        fos.flush();
        fos.close();
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
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
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
     * 原生能力桥：
     * - saveText：把文本写入「下载」目录（API 29+ 走 MediaStore，无需任何权限）
     * - isSafeMode / setGlassEnabled：液态玻璃特效开关（安全模式下软件渲染，开启时硬件加速）
     * - onReady：页面 JS 就绪回调，标记本次启动成功（供崩溃循环检测）
     * - startupLog：启动轨迹，供设置页导出诊断
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

        /** true = 当前处于安全模式（特效关闭、软件渲染）。 */
        @JavascriptInterface
        public boolean isSafeMode() {
            return !currentGlass;
        }

        /**
         * 开关液态玻璃特效。开启 = 硬件加速 + 位移滤镜；关闭 = 软件渲染 + 无滤镜。
         * 用户手动开关视为明确意图：忽略上一次的异常记录，重新开始。
         */
        @JavascriptInterface
        public boolean setGlassEnabled(final boolean on) {
            currentGlass = on;
            try {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putBoolean(K_GLASS, on)
                        .putBoolean(K_FORCE_SAFE, false)
                        .putBoolean(K_ATTEMPTED, false)
                        .putBoolean(K_LAUNCH_OK, true)
                        .apply();
            } catch (Throwable ignore) {}
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    try {
                        if (web != null) {
                            web.setLayerType(on ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_SOFTWARE, null);
                        }
                    } catch (Throwable ignore) {}
                }
            });
            return true;
        }

        /** 页面 JS 就绪：标记本次启动成功，脱离崩溃循环检测。 */
        @JavascriptInterface
        public void onReady() {
            log("JS onReady：启动成功");
            try {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(K_LAUNCH_OK, true).apply();
            } catch (Throwable ignore) {}
        }

        /** 启动轨迹（设置页导出诊断用）。 */
        @JavascriptInterface
        public String startupLog() {
            return startupLog.toString();
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
