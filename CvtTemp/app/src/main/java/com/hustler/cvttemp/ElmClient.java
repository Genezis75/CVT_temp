package com.hustler.cvttemp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Классический Bluetooth SPP клиент для ELM327 (v1.5 и совместимые). */
public class ElmClient {
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothSocket socket;
    private InputStream in;
    private OutputStream out;
    private final Object lock = new Object();

    public void connect(String mac) throws IOException {
        close();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) throw new IOException("Bluetooth недоступен на устройстве");
        if (!adapter.isEnabled()) throw new IOException("Bluetooth выключен");
        BluetoothDevice device;
        try {
            device = adapter.getRemoteDevice(mac);
        } catch (IllegalArgumentException e) {
            throw new IOException("Неверный MAC-адрес адаптера");
        }
        try { adapter.cancelDiscovery(); } catch (SecurityException ignored) {}

        BluetoothSocket s = device.createRfcommSocketToServiceRecord(SPP);
        try {
            s.connect();
        } catch (IOException first) {
            try { s.close(); } catch (IOException ignored) {}
            // Запасной путь для части клонов ELM327
            try {
                s = (BluetoothSocket) device.getClass()
                        .getMethod("createRfcommSocket", int.class)
                        .invoke(device, 1);
                s.connect();
            } catch (Exception second) {
                throw new IOException("Не удалось подключиться к адаптеру. " +
                        "Проверьте, что он сопряжён в настройках Bluetooth и зажигание включено.");
            }
        }
        socket = s;
        in = s.getInputStream();
        out = s.getOutputStream();
    }

    public String send(String cmd, int timeoutMs) throws IOException {
        synchronized (lock) {
            if (out == null || in == null) throw new IOException("Нет соединения с адаптером");
            out.write((cmd + "\r").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            StringBuilder sb = new StringBuilder();
            long end = System.currentTimeMillis() + Math.max(300, timeoutMs);
            byte[] buf = new byte[512];
            while (System.currentTimeMillis() < end) {
                int avail = in.available();
                if (avail > 0) {
                    int n = in.read(buf, 0, Math.min(buf.length, avail));
                    if (n < 0) throw new IOException("Соединение закрыто адаптером");
                    sb.append(new String(buf, 0, n, StandardCharsets.US_ASCII));
                    int p = sb.indexOf(">");
                    if (p >= 0) return sb.substring(0, p);
                } else {
                    try { Thread.sleep(10); }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Обмен прерван");
                    }
                }
            }
            throw new IOException("Таймаут ответа: " + cmd);
        }
    }

    public boolean isConnected() {
        BluetoothSocket s = socket;
        return s != null && s.isConnected();
    }

    public void close() {
        synchronized (lock) {
            try { if (in != null) in.close(); } catch (IOException ignored) {}
            try { if (out != null) out.close(); } catch (IOException ignored) {}
            try { if (socket != null) socket.close(); } catch (IOException ignored) {}
            in = null; out = null; socket = null;
        }
    }
}
