package com.spaila.webview;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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
    private static final String CHANNEL_ID = "spaila_default";
    private static final int REQ_NOTIFICATIONS = 1001;

    private WebView webView;
    private String pendingTitle;
    private String pendingMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
        });

        setupSpailaBridge();

        webView.loadUrl(ORIGIN + "/assets/www/index.html");
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
                showNotification(args.getString(0), args.getString(1));
            }
        } catch (Exception e) {
            // Ignore bad messages for now
        }
    }

    // ---------- Notifications ----------

    private void showNotification(String title, String message) {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            // Ask first, then show once the user answers
            pendingTitle = title;
            pendingMessage = message;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            return;
        }
        postNotification(title, message);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingTitle != null) {
                    postNotification(pendingTitle, pendingMessage);
                }
            } else {
                Toast.makeText(this, "Notifications are turned off for this app", Toast.LENGTH_LONG).show();
            }
            pendingTitle = null;
            pendingMessage = null;
        }
    }

    private void postNotification(String title, String message) {
        NotificationManager manager = getSystemService(NotificationManager.class);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Spaila notifications", NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }

        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent tapAction = PendingIntent.getActivity(
                this, 0, openApp, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = (Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(tapAction)
                .setAutoCancel(true);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
