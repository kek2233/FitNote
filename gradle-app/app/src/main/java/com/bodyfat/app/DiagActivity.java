package com.bodyfat.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * 诊断版外壳 v3（二分定位）。
 * 目标：确定「装上打不开」的失败点在 外壳 / 渲染器 / 旧界面 / 新界面 中的哪一层。
 *  - 外壳 UI 纯原生（ScrollView + TextView + Button），不依赖 WebView，一定有显示；
 *  - 自动加载 v1.1.0 的旧界面（已知在真机上可用的页面）；
 *  - 用户点按钮再加载当前新界面（安全模式）；
 *  - 每一步结果都打印到屏幕并落盘；渲染进程崩溃会自愈并记录，不杀进程。
 */
public class DiagActivity extends Activity {
    private TextView out;
    private final StringBuilder log = new StringBuilder();
    private long t0;
    private WebView web;
    private Uri diagUri;
    private boolean oldDone = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        t0 = System.currentTimeMillis();
        super.onCreate(b);
        installCrashLogger();

        /* ---------- 原生 UI ---------- */
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (14 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        ScrollView sv = new ScrollView(this);
        out = new TextView(this);
        out.setTextSize(12f);
        out.setTypeface(Typeface.MONOSPACE);
        out.setTextColor(0xFFeaf1f6);
        out.setMovementMethod(new ScrollingMovementMethod());
        sv.addView(out);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);

        Button bNew = new Button(this);
        bNew.setText("加载新界面");
        bNew.setAllCaps(false);
        bNew.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                step("→ 用户操作", "加载新界面（安全模式）");
                if (web != null) web.loadUrl("file:///android_asset/index.html?safe=1");
            }
        });
        btns.addView(bNew, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button bExport = new Button(this);
        bExport.setText("导出日志");
        bExport.setAllCaps(false);
        bExport.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { new HostBridge().saveText("bodyfat-诊断3.txt", log.toString()); }
        });
        btns.addView(bExport, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button bInfo = new Button(this);
        bInfo.setText("应用信息");
        bInfo.setAllCaps(false);
        bInfo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null)));
                } catch (Throwable t) { toast(String.valueOf(t)); }
            }
        });
        btns.addView(bInfo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(btns, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        /* ---------- ① 外壳信息 ---------- */
        step("① 外壳启动", "OK · Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT
                + ") · " + Build.MODEL + " · ABI " + (Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?"));
        step("① WebView 组件", webViewPkg());

        /* ---------- ② WebView 创建 ---------- */
        try {
            web = new WebView(this);
            step("② WebView 创建", "OK");
        } catch (Throwable t) {
            step("② WebView 创建", "失败：" + t);
            hint("WebView 组件不可用：请在 设置→应用→Android System WebView 启用/更新；升级前可用浏览器打开网页版。");
            setContentView(root);
            return;
        }

        try {
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setAllowFileAccess(true);
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowUniversalAccessFromFileURLs(true);
        } catch (Throwable t) {
            step("② WebView 设置", "失败：" + t);
        }
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                step("页面开始", String.valueOf(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                step("页面完成", String.valueOf(url));
                if (!oldDone && url != null && url.contains("old.html")) {
                    oldDone = true;
                    step("③ 旧界面(v1.1.0)", "OK ✅ → 外壳与渲染器正常");
                    web.evaluateJavascript("navigator.userAgent", new ValueCallback<String>() {
                        @Override public void onReceiveValue(String v) {
                            step("WebView 版本", v == null ? "?" : v.replace("\"", ""));
                        }
                    });
                }
                if (url != null && url.contains("index.html")) {
                    step("④ 新界面(当前版)", "页面完成 ✅");
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                step("资源错误", (request != null ? String.valueOf(request.getUrl()) : "?") + " → "
                        + (error != null ? String.valueOf(error.getDescription()) : "?"));
            }

            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                step("⚠ 渲染进程消失", "App 自愈（未杀进程）· 上一个页面就是凶手");
                return true;
            }
        });
        web.addJavascriptInterface(new HostBridge(), "Android");
        root.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        /* ---------- ③ 自动加载旧界面（已知良好） ---------- */
        step("③ 自动加载旧界面", "file:///android_asset/old.html");
        web.loadUrl("file:///android_asset/old.html");
    }

    /* ---------- 诊断输出 ---------- */
    private void step(String name, String detail) {
        try {
            String line = (System.currentTimeMillis() - t0) + "ms  " + name + "：" + detail + "\n";
            log.append(line);
            if (out != null) out.append(line);
            writeLogFiles();
        } catch (Throwable ignore) {}
    }

    private void hint(String text) {
        try {
            log.append("\n【结论】").append(text).append("\n");
            if (out != null) out.append("\n【结论】" + text + "\n");
            writeLogFiles();
        } catch (Throwable ignore) {}
    }

    private void toast(String msg) {
        try { Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); } catch (Throwable ignore) {}
    }

    private String webViewPkg() {
        StringBuilder sb = new StringBuilder();
        try {
            for (android.content.pm.PackageInfo pi : getPackageManager().getInstalledPackages(0)) {
                String n = pi.packageName;
                if (n != null && (n.contains("webview") || n.contains("WebView"))) sb.append(n).append(' ');
            }
        } catch (Throwable ignore) {}
        return sb.length() == 0 ? "(未找到 WebView 相关包)" : sb.toString().trim();
    }

    private void writeLogFiles() {
        try {
            File dir = getExternalFilesDir(null);
            if (dir != null) writeText(new File(dir, "bodyfat-startup.txt"), log.toString());
        } catch (Throwable ignore) {}
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (diagUri == null) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, "bodyfat-startup.txt");
                    cv.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                    cv.put(MediaStore.Downloads.IS_PENDING, 1);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (uri != null) diagUri = uri;
                }
                if (diagUri != null) {
                    OutputStream os = getContentResolver().openOutputStream(diagUri, "w");
                    if (os != null) {
                        os.write(log.toString().getBytes("UTF-8"));
                        os.flush();
                        os.close();
                    }
                }
            }
        } catch (Throwable ignore) {}
    }

    private void writeText(File f, String text) throws IOException {
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(text.getBytes("UTF-8"));
        fos.flush();
        fos.close();
    }

    private void installCrashLogger() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try {
                    step("💥 Java 崩溃（" + t.getName() + "）", String.valueOf(e));
                    File dir = getExternalFilesDir(null);
                    if (dir != null) {
                        StringBuilder sb = new StringBuilder(log);
                        sb.append("\n异常: ").append(e.toString()).append("\n");
                        for (StackTraceElement s : e.getStackTrace()) sb.append("    at ").append(s).append("\n");
                        writeText(new File(dir, "bodyfat-crash.txt"), sb.toString());
                    }
                } catch (Throwable ignore) {}
                if (!"main".equals(t.getName())) return;
                if (previous != null) previous.uncaughtException(t, e);
            }
        });
    }

    private class HostBridge {
        @JavascriptInterface
        public void saveText(String name, String text) {
            String safeName = (name == null || name.trim().isEmpty()) ? "bodyfat-backup.json" : name;
            byte[] bytes = (text == null ? "" : text).getBytes(java.nio.charset.Charset.forName("UTF-8"));
            OutputStream os = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                    cv.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
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
                    toast("已保存到「下载」：" + safeName);
                } else {
                    File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) throw new Exception("存储不可用");
                    if (!dir.exists()) dir.mkdirs();
                    File f = new File(dir, safeName);
                    FileOutputStream fos = new FileOutputStream(f);
                    fos.write(bytes);
                    fos.flush();
                    fos.close();
                    toast("已保存：" + f.getAbsolutePath());
                }
            } catch (Exception e) {
                if (os != null) { try { os.close(); } catch (Exception ignored) {} }
                toast("保存失败：" + e.getMessage());
            }
        }

        @JavascriptInterface public boolean isSafeMode() { return true; }
        @JavascriptInterface public boolean setGlassEnabled(boolean on) { return false; }
        @JavascriptInterface public void onReady() { step("JS onReady", "新界面已就绪 ✅"); }
        @JavascriptInterface public String startupLog() { return log.toString(); }
    }
}
