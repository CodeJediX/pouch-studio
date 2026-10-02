package com.codejedix.deadlineatlas;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String APP_URL = "https://codejedix.github.io/pouch-studio/deadline-atlas/";
    private static final int PICK_FILE_REQUEST = 1001;
    private static final int SAVE_FILE_REQUEST = 1002;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1003;
    private static final int MAX_EXPORT_BYTES = 16 * 1024 * 1024;

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> fileChooserCallback;
    private byte[] pendingExport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(7, 16, 11));
        getWindow().setNavigationBarColor(Color.rgb(7, 16, 11));

        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(7, 16, 11));
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3));
        root.addView(progressBar, progressParams);
        setContentView(root);

        NotificationScheduler.ensureChannel(this);
        NotificationScheduler.scheduleStored(this);
        configureWebView();
        String initialUrl = safeAppUrl(getIntent() == null ? null : getIntent().getData());
        if (savedInstanceState == null) webView.loadUrl(initialUrl);
        else webView.restoreState(savedInstanceState);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setUserAgentString(settings.getUserAgentString() + " DeadlineAtlasAndroid/1.2");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        WebView.setWebContentsDebuggingEnabled(false);
        webView.addJavascriptInterface(new AndroidBridge(), "DeadlineAtlasAndroid");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isAppUri(uri)) return false;
                openExternal(uri);
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showOfflinePage();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
                showOfflinePage();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;
                Intent intent;
                try {
                    intent = params.createIntent();
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                } catch (Exception ignored) {
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("*/*");
                }
                try {
                    startActivityForResult(intent, PICK_FILE_REQUEST);
                    return true;
                } catch (ActivityNotFoundException error) {
                    fileChooserCallback = null;
                    Toast.makeText(MainActivity.this, "No file picker is available.", Toast.LENGTH_LONG).show();
                    return false;
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView popup = new WebView(MainActivity.this);
                popup.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView popupView, WebResourceRequest request) {
                        openExternal(request.getUrl());
                        popupView.destroy();
                        return true;
                    }
                });
                ((WebView.WebViewTransport) resultMsg.obj).setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
        });
    }

    private boolean isAppUri(Uri uri) {
        return uri != null
                && "https".equalsIgnoreCase(uri.getScheme())
                && "codejedix.github.io".equalsIgnoreCase(uri.getHost())
                && uri.getPath() != null
                && uri.getPath().startsWith("/pouch-studio/deadline-atlas/");
    }

    private String safeAppUrl(Uri uri) {
        return isAppUri(uri) ? uri.toString() : APP_URL;
    }

    private void openExternal(Uri uri) {
        if (uri == null) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "No app can open this link.", Toast.LENGTH_LONG).show();
        }
    }

    private void showOfflinePage() {
        String html = "<!doctype html><meta name='viewport' content='width=device-width'><style>" +
                "body{margin:0;background:#07100b;color:#eef8f1;font:16px sans-serif;display:grid;place-items:center;min-height:100vh}" +
                "main{text-align:center;padding:32px;max-width:360px}h1{font-size:26px}p{color:#9bb0a2;line-height:1.5}" +
                "button{border:0;border-radius:14px;padding:14px 22px;background:#6dff9b;color:#07100b;font-weight:800}</style>" +
                "<main><h1>Deadline Atlas is offline</h1><p>Connect to the internet to securely sync your competitions.</p>" +
                "<button onclick=\"location.href='" + APP_URL + "'\">Try again</button></main>";
        webView.loadDataWithBaseURL(APP_URL, html, "text/html", "UTF-8", null);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        webView.loadUrl(safeAppUrl(intent.getData()));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(result);
            fileChooserCallback = null;
        } else if (requestCode == SAVE_FILE_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingExport != null) {
                try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
                    if (output == null) throw new IllegalStateException("Could not open destination");
                    output.write(pendingExport);
                    Toast.makeText(this, "File saved.", Toast.LENGTH_SHORT).show();
                } catch (Exception error) {
                    Toast.makeText(this, "Could not save the file.", Toast.LENGTH_LONG).show();
                }
            }
            pendingExport = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != NOTIFICATION_PERMISSION_REQUEST) return;
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            NotificationScheduler.showTest(this);
            NotificationScheduler.scheduleStored(this);
        }
        reportNotificationResult(granted);
    }

    private boolean notificationsGranted() {
        return Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestOrShowTestNotification() {
        if (notificationsGranted()) {
            NotificationScheduler.showTest(this);
            reportNotificationResult(true);
        } else if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    private void reportNotificationResult(boolean granted) {
        if (webView == null) return;
        webView.evaluateJavascript("window.deadlineAtlasNotificationResult(" + granted + ")", null);
    }

    @Override
    protected void onDestroy() {
        if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
        if (webView != null) {
            webView.removeJavascriptInterface("DeadlineAtlasAndroid");
            webView.destroy();
        }
        super.onDestroy();
    }

    public final class AndroidBridge {
        @JavascriptInterface
        public void scheduleNotifications(String payload) {
            runOnUiThread(() -> NotificationScheduler.scheduleFromPayload(MainActivity.this, payload));
        }

        @JavascriptInterface
        public String showTestNotification() {
            boolean granted = notificationsGranted();
            runOnUiThread(MainActivity.this::requestOrShowTestNotification);
            return granted ? "shown" : "permission-requested";
        }

        @JavascriptInterface
        public String getNotificationPermissionState() {
            if (notificationsGranted()) return "granted";
            if (Build.VERSION.SDK_INT >= 33 && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                return "denied";
            }
            return "prompt";
        }

        @JavascriptInterface
        public void saveFile(String name, String mimeType, String base64Data) {
            runOnUiThread(() -> {
                Uri current = Uri.parse(webView.getUrl() == null ? "" : webView.getUrl());
                if (!isAppUri(current)) return;
                try {
                    byte[] decoded = Base64.decode(base64Data, Base64.DEFAULT);
                    if (decoded.length > MAX_EXPORT_BYTES) throw new IllegalArgumentException("File too large");
                    String safeName = name == null ? "deadline-atlas-export" :
                            name.replaceAll("[^A-Za-z0-9._-]", "_");
                    String safeType = mimeType == null || mimeType.trim().isEmpty() ?
                            "application/octet-stream" : mimeType.toLowerCase(Locale.ROOT);
                    pendingExport = decoded;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType(safeType)
                            .putExtra(Intent.EXTRA_TITLE, safeName);
                    startActivityForResult(intent, SAVE_FILE_REQUEST);
                } catch (Exception error) {
                    pendingExport = null;
                    Toast.makeText(MainActivity.this, "Could not prepare the export.", Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
