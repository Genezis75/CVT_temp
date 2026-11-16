package com.hustler.cvttemp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;
    private BtBridge bridge;
    private boolean keepAwake = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF070D18);
        getWindow().setNavigationBarColor(0xFF070D18);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUseWideViewPort(false);
        s.setLoadWithOverviewMode(false);
        s.setTextZoom(100);

        bridge = new BtBridge(this, web);
        web.addJavascriptInterface(bridge, "AndroidBT");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.setBackgroundColor(0xFF070D18);
        web.loadUrl("file:///android_asset/index.html");

        askBluetoothPermission();
    }

    /** Вызывается из JS; запоминается и переустанавливается при каждом возврате в приложение. */
    public void applyKeepAwake(boolean on) {
        keepAwake = on;
        runOnUiThread(() -> {
            if (keepAwake) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }

    public boolean isKeepAwake() { return keepAwake; }

    private void askBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1001);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        applyKeepAwake(keepAwake);
        if (requestCode == 1001 && web != null) {
            web.evaluateJavascript("window.onNativePermission && window.onNativePermission()", null);
        }
    }

    @Override
    public void onBackPressed() {
        if (web == null) { super.onBackPressed(); return; }
        web.evaluateJavascript(
                "(function(){try{return !!(window.onNativeBack&&window.onNativeBack());}catch(e){return false;}})()",
                value -> { if (!"true".equals(value)) finish(); });
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyKeepAwake(keepAwake);
        if (web != null) {
            web.onResume();
            web.evaluateJavascript("window.onNativeResume && window.onNativeResume()", null);
        }
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (bridge != null) bridge.shutdown();
        if (web != null) {
            web.removeJavascriptInterface("AndroidBT");
            web.destroy();
        }
        super.onDestroy();
    }
}
