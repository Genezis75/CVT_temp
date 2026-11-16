package com.hustler.cvttemp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Мост между HTML-интерфейсом и нативным Bluetooth/системой Android. */
public class BtBridge {
    private final Activity act;
    private final WebView web;
    private final ElmClient elm = new ElmClient();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean soundOn = true;
    private long lastWidget = 0;

    public BtBridge(Activity act, WebView web) {
        this.act = act;
        this.web = web;
    }

    private void js(String script) {
        main.post(() -> web.evaluateJavascript(script, null));
    }

    private void showToast(String msg) {
        main.post(() -> Toast.makeText(act, msg, Toast.LENGTH_SHORT).show());
    }

    @JavascriptInterface
    public boolean isNative() { return true; }

    @SuppressLint("MissingPermission")
    @JavascriptInterface
    public String listPaired() {
        JSONArray arr = new JSONArray();
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a != null) {
                Set<BluetoothDevice> devices = a.getBondedDevices();
                for (BluetoothDevice d : devices) {
                    JSONObject o = new JSONObject();
                    String name = d.getName();
                    o.put("name", name == null || name.isEmpty() ? d.getAddress() : name);
                    o.put("mac", d.getAddress());
                    arr.put(o);
                }
            }
        } catch (Exception ignored) {}
        return arr.toString();
    }

    @JavascriptInterface
    public boolean bluetoothEnabled() {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            return a != null && a.isEnabled();
        } catch (Exception e) { return false; }
    }

    @JavascriptInterface
    public void openBluetoothSettings() {
        main.post(() -> {
            try { act.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
            catch (Exception e) { showToast("Не удалось открыть настройки Bluetooth"); }
        });
    }

    @JavascriptInterface
    public void connect(String mac) {
        io.execute(() -> {
            try {
                elm.connect(mac);
                js("window.onNativeConnected(true," + JSONObject.quote("Подключено") + ")");
            } catch (Exception e) {
                String m = e.getMessage() == null ? "Ошибка подключения" : e.getMessage();
                js("window.onNativeConnected(false," + JSONObject.quote(m) + ")");
            }
        });
    }

    @JavascriptInterface
    public void disconnect() { io.execute(elm::close); }

    @JavascriptInterface
    public boolean isConnected() { return elm.isConnected(); }

    @JavascriptInterface
    public void send(String cmd, int id, int timeoutMs) {
        io.execute(() -> {
            try {
                String r = elm.send(cmd, timeoutMs);
                js("window.onNativeResponse(" + id + "," + JSONObject.quote(r) + ")");
            } catch (Exception e) {
                String m = e.getMessage() == null ? "Ошибка обмена" : e.getMessage();
                js("window.onNativeError(" + id + "," + JSONObject.quote(m) + ")");
            }
        });
    }

        @JavascriptInterface
    public void setKeepScreenOn(boolean on) {
        if (act instanceof MainActivity) ((MainActivity) act).applyKeepAwake(on);
        else main.post(() -> {
            if (on) act.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else act.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }

    @JavascriptInterface
    public boolean getKeepScreenOn() {
        return act instanceof MainActivity && ((MainActivity) act).isKeepAwake();
    }

    @JavascriptInterface
    public void updateTemps(double cvt, double eng, String cz, String ez, String mode) {
        Store.save(act, (float) cvt, (float) eng, cz, ez, mode);
        long now = System.currentTimeMillis();
        if (now - lastWidget >= 1500) {
            lastWidget = now;
            TempWidget.updateAll(act);
        }
    }

    @JavascriptInterface
    public void vibrate() {
        try {
            Vibrator v = (Vibrator) act.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(new long[]{0, 130, 80, 130}, -1));
            } else {
                v.vibrate(new long[]{0, 130, 80, 130}, -1);
            }
        } catch (Exception ignored) {}
    }

    @JavascriptInterface
    public void setSoundEnabled(boolean on) { soundOn = on; }

    @JavascriptInterface
    public void playAlarm(int level) {
        if (!soundOn) return;
        try {
            final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85);
            int tone = level >= 3 ? ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD : ToneGenerator.TONE_PROP_BEEP;
            tg.startTone(tone, level >= 3 ? 450 : 180);
            main.postDelayed(tg::release, level >= 3 ? 650 : 350);
        } catch (Exception ignored) {}
    }

    @JavascriptInterface
    public void toast(String msg) { showToast(String.valueOf(msg)); }

    /** Сохранение CSV в публичную папку Download. */
    @JavascriptInterface
    public String saveCsv(String name, String content) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/csv");
                cv.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = act.getContentResolver()
                        .insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) throw new Exception("MediaStore вернул null");
                try (OutputStream os = act.getContentResolver().openOutputStream(uri)) {
                    if (os == null) throw new Exception("Не удалось открыть поток");
                    os.write(content.getBytes(StandardCharsets.UTF_8));
                }
                return "Download/" + name;
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, name);
                try (FileOutputStream os = new FileOutputStream(f)) {
                    os.write(content.getBytes(StandardCharsets.UTF_8));
                }
                return f.getAbsolutePath();
            }
        } catch (Exception e) {
            try {
                File dir = new File(act.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "cvt");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, name);
                try (FileOutputStream os = new FileOutputStream(f)) {
                    os.write(content.getBytes(StandardCharsets.UTF_8));
                }
                return f.getAbsolutePath();
            } catch (Exception e2) {
                return "ERROR: " + e2.getMessage();
            }
        }
    }

    public void shutdown() {
        io.execute(elm::close);
        io.shutdown();
    }
}
