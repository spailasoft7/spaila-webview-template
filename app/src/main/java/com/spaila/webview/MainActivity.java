package com.spaila.webview;

import android.app.Activity;
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

import org.json.JSONObject;

import java.util.Collections;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        // Serves files from the assets folder at ORIGIN + "/assets/..."
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

            if (fn.equals("toast")) {
                String text = json.getJSONArray("args").getString(0);
                Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            // Ignore bad messages for now
        }
    }
}
