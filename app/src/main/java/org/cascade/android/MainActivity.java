package org.cascade.android;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * Android SPP and ELM327 transport bring-up. The CASCADE CPU/emulator core is
 * not integrated yet; this screen validates the Bluetooth command channel.
 */
public final class MainActivity extends Activity {
    private static final int REQ_BT = 40;
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private volatile BluetoothSocket socket;
    private TextView log;
    private LinearLayout deviceList;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 12, 16, 12);

        TextView title = new TextView(this);
        title.setText("Cascade Android — SPP / ELM327");
        title.setTextSize(21);
        root.addView(title);

        Button refresh = new Button(this);
        refresh.setText("Показать сопряжённые устройства");
        refresh.setOnClickListener(v -> requestAndList());
        root.addView(refresh);

        ScrollView devicesScroll = new ScrollView(this);
        deviceList = new LinearLayout(this);
        deviceList.setOrientation(LinearLayout.VERTICAL);
        devicesScroll.addView(deviceList);
        root.addView(devicesScroll, new LinearLayout.LayoutParams(-1, 0, 2));

        TextView commandsTitle = new TextView(this);
        commandsTitle.setText("Команды адаптеру");
        commandsTitle.setTextSize(17);
        root.addView(commandsTitle);

        LinearLayout commands = new LinearLayout(this);
        commands.setOrientation(LinearLayout.HORIZONTAL);
        addCommandButton(commands, "ATZ", "ATZ");
        addCommandButton(commands, "Инициализация", "INIT");
        addCommandButton(commands, "OBD 0100", "0100");
        root.addView(commands);

        Button disconnect = new Button(this);
        disconnect.setText("Отключить");
        disconnect.setOnClickListener(v -> closeSocket());
        root.addView(disconnect);

        log = new TextView(this);
        log.setText("Это транспортный прототип, не готовый эмулятор CASCADE.\n");
        log.setTextIsSelectable(true);
        ScrollView logScroll = new ScrollView(this);
        logScroll.addView(log);
        root.addView(logScroll, new LinearLayout.LayoutParams(-1, 0, 3));
        setContentView(root);
        requestAndList();
    }

    private void addCommandButton(LinearLayout row, String label, String command) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(v -> {
            if ("INIT".equals(command)) {
                sendCommand("ATZ");
                sendCommand("ATE0");
                sendCommand("ATL0");
                sendCommand("ATS0");
                sendCommand("ATH0");
                sendCommand("ATSP0");
            } else sendCommand(command);
        });
        row.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void appendLog(String message) {
        runOnUiThread(() -> {
            log.append(message + "\n");
        });
    }

    private void requestAndList() {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
            return;
        }
        deviceList.removeAllViews();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) { appendLog("Bluetooth не поддерживается"); return; }
        if (!adapter.isEnabled()) { appendLog("Включите Bluetooth в настройках Android"); return; }
        try {
            Set<BluetoothDevice> paired = adapter.getBondedDevices();
            for (BluetoothDevice d : paired) {
                Button b = new Button(this);
                String name = d.getName();
                if (name == null || name.isEmpty()) name = "Без имени";
                b.setText(name + "\n" + d.getAddress());
                b.setOnClickListener(v -> connect(d));
                deviceList.addView(b);
            }
            appendLog("Сопряжённых устройств: " + paired.size());
        } catch (SecurityException e) {
            appendLog("Нет разрешения Bluetooth: " + e.getMessage());
        }
    }

    private void connect(BluetoothDevice device) {
        closeSocket();
        appendLog("Подключение SPP: " + device.getAddress());
        new Thread(() -> {
            BluetoothSocket candidate = null;
            try {
                candidate = device.createRfcommSocketToServiceRecord(SPP);
                candidate.connect();
                socket = candidate;
                appendLog("SPP подключён. Ядро CASCADE пока не интегрировано.");
                InputStream in = candidate.getInputStream();
                byte[] buffer = new byte[512];
                while (candidate.isConnected()) {
                    int n = in.read(buffer);
                    if (n < 0) break;
                    if (n > 0) appendLog("RX: " + new String(buffer, 0, n, StandardCharsets.US_ASCII).replace("\r", "␍").replace("\n", "␊\n"));
                }
            } catch (Exception e) {
                if (candidate != null) try { candidate.close(); } catch (IOException ignored) {}
                appendLog("Ошибка SPP: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }, "cascade-spp-connect").start();
    }

    private void sendCommand(String command) {
        BluetoothSocket current = socket;
        if (current == null || !current.isConnected()) {
            appendLog("Нет SPP-соединения — команда не отправлена: " + command);
            return;
        }
        new Thread(() -> {
            try {
                OutputStream out = current.getOutputStream();
                appendLog("TX: " + command);
                out.write((command + "\r").getBytes(StandardCharsets.US_ASCII));
                out.flush();
            } catch (Exception e) {
                appendLog("Ошибка отправки: " + e.getMessage());
            }
        }, "cascade-spp-send").start();
    }

    private void closeSocket() {
        BluetoothSocket current = socket;
        socket = null;
        if (current != null) new Thread(() -> {
            try { current.close(); } catch (IOException ignored) {}
        }).start();
        appendLog("SPP отключён");
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQ_BT) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) requestAndList();
            else Toast.makeText(this, "Нужно разрешение Bluetooth", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onDestroy() {
        closeSocket();
        super.onDestroy();
    }
}