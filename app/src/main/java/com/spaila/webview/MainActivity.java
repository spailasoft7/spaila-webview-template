package com.spaila.webview;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private static final String START_PAGE = "bridge-test.html";
    private static final int REQ_NOTIFICATIONS = 1001;

    private WebView webView;
    private Runnable pendingAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportMultipleWindows(true);

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri.toString().startsWith(ORIGIN)) {
                    return false; // our own pages stay inside the app
                }
                openInBrowser(uri);
                return true;
            }
        });

        // Handles window.open(...) and links with target="_blank"
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, Message resultMsg) {
                WebView temp = new WebView(MainActivity.this);
                temp.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        openInBrowser(request.getUrl());
                        return true;
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(temp);
                resultMsg.sendToTarget();
                return true;
            }
        });

        setupSpailaBridge();

        webView.loadUrl(ORIGIN + "/assets/www/" + START_PAGE);
    }

    private void openInBrowser(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- Bridge ----------

    private void setupSpailaBridge() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            Toast.makeText(this, "Spaila bridge not supported on this device", Toast.LENGTH_LONG).show();
            return;
        }

        Set<String> allowedOrigins = Collections.singleton(ORIGIN);

        WebViewCompat.addWebMessageListener(webView, "SpailaNative", allowedOrigins,
                (view, message, sourceOrigin, isMainFrame, replyProxy) -> {
                    handleMessage(message);
                });
    }

    private void handleMessage(WebMessageCompat message) {
        try {
            JSONObject json = new JSONObject(message.getData());
            String fn = json.getString("fn");
            JSONArray args = json.getJSONArray("args");

            if (fn.equals("toast")) {
                Toast.makeText(this, args.getString(0), Toast.LENGTH_SHORT).show();

            } else if (fn.equals("showNotification")) {
                final String title = args.getString(0);
                final String text = args.getString(1);
                withNotificationPermission(() -> NotificationHelper.show(this, title, text));

            } else if (fn.equals("scheduleNotification")) {
                final int id = args.getInt(0);
                final String title = args.getString(1);
                final String text = args.getString(2);
                final long seconds = args.getLong(3);
                withNotificationPermission(() -> scheduleNotification(id, title, text, seconds));

            } else if (fn.equals("cancelScheduledNotification")) {
                cancelScheduledNotification(args.getInt(0));
            }
        } catch (Exception e) {
            // Ignore bad messages for now
        }
    }

    // ---------- Notification permission ----------

    // Runs the action now if allowed, otherwise asks first and runs it if the user says yes.
    private void withNotificationPermission(Runnable action) {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            pendingAction = action;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            return;
        }
        action.run();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && pendingAction != null) {
                pendingAction.run();
            } else if (!granted) {
                Toast.makeText(this, "Notifications are turned off for this app", Toast.LENGTH_LONG).show();
            }
            pendingAction = null;
        }
    }

    // ---------- Scheduled notifications ----------

    private PendingIntent alarmIntent(int id, String title, String message, int flags) {
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.putExtra("title", title);
        intent.putExtra("message", message);
        return PendingIntent.getBroadcast(this, id, intent, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private void scheduleNotification(int id, String title, String message, long seconds) {
        AlarmManager alarmManager = getSystemService(AlarmManager.class);
        PendingIntent pending = alarmIntent(id, title, message, PendingIntent.FLAG_UPDATE_CURRENT);
        long triggerAt = System.currentTimeMillis() + seconds * 1000L;
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
    }

    private void cancelScheduledNotification(int id) {
        AlarmManager alarmManager = getSystemService(AlarmManager.class);
        PendingIntent pending = alarmIntent(id, "", "", PendingIntent.FLAG_NO_CREATE);
        if (pending != null) {
            alarmManager.cancel(pending);
            pending.cancel();
        }
    }
}
