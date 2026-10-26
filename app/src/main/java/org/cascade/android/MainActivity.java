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
import java.util.Set;
import java.util.UUID;

/** SPP bring-up screen; not yet connected to the CASCADE emulator core. */
public final class MainActivity extends Activity {
    private static final int REQ_BT = 40;
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothSocket socket;
    private TextView status;
    private LinearLayout list;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(20,20,20,20);
        TextView title = new TextView(this); title.setText("Cascade Android · SPP prototype"); title.setTextSize(22); root.addView(title);
        status = new TextView(this); status.setText("Выберите сопряжённый Bluetooth-адаптер. Это тест SPP, не полный CASCADE."); root.addView(status);
        Button refresh = new Button(this); refresh.setText("Обновить сопряжённые устройства"); refresh.setOnClickListener(v -> requestAndList()); root.addView(refresh);
        ScrollView sc = new ScrollView(this); list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); sc.addView(list); root.addView(sc, new LinearLayout.LayoutParams(-1,0,1));
        Button disconnect = new Button(this); disconnect.setText("Отключить"); disconnect.setOnClickListener(v -> closeSocket()); root.addView(disconnect);
        setContentView(root); requestAndList();
    }

    private void requestAndList() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT); return; }
        list.removeAllViews(); BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) { status.setText("Bluetooth не поддерживается"); return; }
        if (!adapter.isEnabled()) { status.setText("Включите Bluetooth в настройках Android"); return; }
        try {
            Set<BluetoothDevice> paired = adapter.getBondedDevices();
            for (BluetoothDevice d : paired) { Button b = new Button(this); String name = d.getName(); if (name == null || name.isEmpty()) name = "Без имени"; b.setText(name + "\n" + d.getAddress()); b.setOnClickListener(v -> connect(d)); list.addView(b); }
            status.setText("Сопряжённых устройств: " + paired.size() + ". Нажмите на адаптер ELM/SPP.");
        } catch (SecurityException e) { status.setText("Нет разрешения Bluetooth: " + e.getMessage()); }
    }

    private void connect(BluetoothDevice device) {
        closeSocket(); status.setText("Подключаюсь к " + device.getAddress() + "…");
        new Thread(() -> {
            BluetoothSocket candidate = null;
            try {
                candidate = device.createRfcommSocketToServiceRecord(SPP); candidate.connect(); socket = candidate;
                BluetoothSocket connected = candidate;
                runOnUiThread(() -> status.setText("SPP подключён: " + device.getAddress() + "\nЯдро CASCADE пока не подключено."));
                InputStream in = connected.getInputStream(); byte[] buf = new byte[256];
                while (!Thread.currentThread().isInterrupted() && connected.isConnected()) { int n = in.read(buf); if (n < 0) break; if (n > 0) { String data = new String(buf,0,n); runOnUiThread(() -> status.setText("SPP подключён. Получено: " + data)); } }
            } catch (Exception e) {
                if (candidate != null) try { candidate.close(); } catch (IOException ignored) {}
                String msg = e.getClass().getSimpleName() + ": " + e.getMessage(); runOnUiThread(() -> status.setText("SPP не подключён: " + msg));
            }
        }, "cascade-spp-connect").start();
    }
    private void closeSocket() { BluetoothSocket s = socket; socket = null; if (s != null) new Thread(() -> { try { s.close(); } catch (IOException ignored) {} }).start(); }
    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) { super.onRequestPermissionsResult(code,p,r); if (code == REQ_BT) { if (r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) requestAndList(); else Toast.makeText(this,"Нужно разрешение Bluetooth",Toast.LENGTH_LONG).show(); } }
    @Override protected void onDestroy() { closeSocket(); super.onDestroy(); }
}
