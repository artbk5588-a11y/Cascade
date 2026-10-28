package org.cascade.android;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Android prototype. The CASCADE CPU sources are compiled into the native library,
 * ROM loading, CPU controls, LCD framebuffer rendering, and touchscreen keypad events are integrated.
 */
public final class MainActivity extends Activity {
    static {
        System.loadLibrary("cascade_native");
    }

    private native String nativeCoreStatus();
    private native String nativeLoadRom(String path);
    private native String nativeStartEmulation();
    private native String nativeStopEmulation();
    private native int[] nativeGetFrame();
    private native void nativeKeyEvent(int type, int keyCode);
    private static final int REQ_ROM = 41;
    private native String nativeValidatePid00(String response);
    private native String nativePollElmCommand();
    private native boolean nativeSubmitElmResponse(String command, String response);
    private static final int REQ_BT = 40;
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private volatile BluetoothSocket socket;
    private volatile InputStream socketInput;
    private volatile OutputStream socketOutput;
    private final Object responseLock = new Object();
    private final StringBuilder responseBuffer = new StringBuilder();
    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    private TextView log;
    private LinearLayout deviceList;
    private ImageView lcdView;
    private Bitmap lcdBitmap;
    private final Handler lcdHandler = new Handler(Looper.getMainLooper());
    private final Runnable lcdRefresh = new Runnable() {
        @Override public void run() {
            if (lcdView == null || lcdBitmap == null || isFinishing()) return;
            try {
                int[] pixels = nativeGetFrame();
                if (pixels != null && pixels.length == 480 * 360) {
                    lcdBitmap.setPixels(pixels, 0, 480, 0, 0, 480, 360);
                    lcdView.invalidate();
                }
            } catch (Exception e) {
                appendLog("Ошибка обновления LCD: " + e.getMessage());
            }
            lcdHandler.postDelayed(this, 100);
        }
    };

    private final Runnable elmBridgePoll = new Runnable() {
        @Override public void run() {
            if (isFinishing()) return;
            try {
                BluetoothSocket current = socket;
                if (current != null && current.isConnected()) {
                    String command = nativePollElmCommand();
                    if (command != null && !command.isEmpty()) {
                        sender.execute(() -> exchangeBridgeCommand(command));
                    }
                }
            } catch (Exception e) {
                appendLog("ELM bridge poll error: " + e.getMessage());
            }
            lcdHandler.postDelayed(this, 100);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 12, 16, 12);

        TextView title = new TextView(this);
        title.setText("CASCADE Android — ядро подключено");
        title.setTextSize(21);
        root.addView(title);

        lcdBitmap = Bitmap.createBitmap(480, 360, Bitmap.Config.ARGB_8888);
        lcdBitmap.eraseColor(Color.BLACK);
        lcdView = new ImageView(this);
        lcdView.setImageBitmap(lcdBitmap);
        lcdView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        lcdView.setAdjustViewBounds(true);
        lcdView.setBackgroundColor(Color.BLACK);
        root.addView(lcdView, new LinearLayout.LayoutParams(-1, 360));

        Button refresh = new Button(this);
        refresh.setText("Показать сопряжённые устройства");
        refresh.setOnClickListener(v -> requestAndList());
        root.addView(refresh);

        Button chooseRom = new Button(this);
        chooseRom.setText("Выбрать ROM CASCADE");
        chooseRom.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, REQ_ROM);
        });
        root.addView(chooseRom);

        LinearLayout coreControls = new LinearLayout(this);
        Button startCore = new Button(this);
        startCore.setText("Запустить CASCADE");
        startCore.setOnClickListener(v -> new Thread(() ->
                appendLog(nativeStartEmulation()), "cascade-core-start").start());
        coreControls.addView(startCore, new LinearLayout.LayoutParams(0, -2, 1));
        Button stopCore = new Button(this);
        stopCore.setText("Остановить CASCADE");
        stopCore.setOnClickListener(v -> new Thread(() ->
                appendLog(nativeStopEmulation()), "cascade-core-stop").start());
        coreControls.addView(stopCore, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(coreControls);

        TextView touchHint = new TextView(this);
        touchHint.setText("Управление: тап по LCD — Ввод; свайп — перемещение. Кнопки Да / Нет / Esc.");
        root.addView(touchHint);

        lcdView.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private static final int SWIPE_DISTANCE_DP = 28;

            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = event.getX();
                    downY = event.getY();
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    float threshold = SWIPE_DISTANCE_DP * getResources().getDisplayMetrics().density;
                    int keyCode;
                    if (Math.abs(dx) > threshold || Math.abs(dy) > threshold) {
                        // A swipe moves one step in the corresponding direction.
                        if (Math.abs(dx) > Math.abs(dy))
                            keyCode = dx < 0 ? 1008 : 1009; // LEFT / RIGHT
                        else
                            keyCode = dy < 0 ? 1006 : 1007; // UP / DOWN
                    } else {
                        // The core has keyboard input, not a mouse pointer. Use
                        // screen edge zones for navigation and the center to confirm.
                        float x = event.getX() / Math.max(1f, view.getWidth());
                        float y = event.getY() / Math.max(1f, view.getHeight());
                        if (x < 0.22f) keyCode = 1008;       // LEFT
                        else if (x > 0.78f) keyCode = 1009;  // RIGHT
                        else if (y < 0.22f) keyCode = 1006;  // UP
                        else if (y > 0.78f) keyCode = 1007;  // DOWN
                        else keyCode = 1004;                 // RETURN / select
                    }
                    pressGestureKey(keyCode, 140);
                    return true;
                }
                return true;
            }
        });

        LinearLayout yesNoRow = new LinearLayout(this);
        Button yesButton = new Button(this);
        yesButton.setText("Да");
        yesButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                nativeKeyEvent(1, 121);
                v.setPressed(true);
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP ||
                       event.getAction() == MotionEvent.ACTION_CANCEL) {
                nativeKeyEvent(2, 121);
                v.setPressed(false);
                return true;
            }
            return true;
        });
        yesNoRow.addView(yesButton, new LinearLayout.LayoutParams(0, -2, 1));

        Button noButton = new Button(this);
        noButton.setText("Нет");
        noButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                nativeKeyEvent(1, 110);
                v.setPressed(true);
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP ||
                       event.getAction() == MotionEvent.ACTION_CANCEL) {
                nativeKeyEvent(2, 110);
                v.setPressed(false);
                return true;
            }
            return true;
        });
        yesNoRow.addView(noButton, new LinearLayout.LayoutParams(0, -2, 1));

        Button escButton = new Button(this);
        escButton.setText("Esc");
        escButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                nativeKeyEvent(1, 1005); // UIKEY_ESCAPE
                v.setPressed(true);
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP ||
                       event.getAction() == MotionEvent.ACTION_CANCEL) {
                nativeKeyEvent(2, 1005); // UIKEY_ESCAPE
                v.setPressed(false);
                return true;
            }
            return true;
        });
        yesNoRow.addView(escButton, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(yesNoRow);

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
        log.setText("Ядро CASCADE собрано в native-библиотеку. Выберите ROM, запустите CPU и наблюдайте LCD выше. Сенсорная клавиатура CASCADE доступна ниже экрана.\n\nBluetooth/ELM327 используется ядром CASCADE для поддерживаемых запросов Mode 01; сначала подключи адаптер SPP и запусти ядро.\n");
        log.setTextIsSelectable(true);
        ScrollView logScroll = new ScrollView(this);
        logScroll.addView(log);
        root.addView(logScroll, new LinearLayout.LayoutParams(-1, 0, 3));
        setContentView(root);
        lcdHandler.postDelayed(elmBridgePoll, 100);
        appendLog("Native: " + nativeCoreStatus());
        appendLog("Native parser self-test: " + nativeValidatePid00("41 00 98 3B 80 11"));
        requestAndList();
        lcdHandler.post(lcdRefresh);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_ROM || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        new Thread(() -> {
            File imported = new File(getCacheDir(), "cascade-selected-rom.bin");
            try (java.io.InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(imported)) {
                if (in == null) throw new IOException("Не удалось открыть выбранный файл");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                String result = nativeLoadRom(imported.getAbsolutePath());
                appendLog(result);
            } catch (Exception e) {
                appendLog("Ошибка чтения ROM: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }, "cascade-rom-loader").start();
    }

    private void pressGestureKey(int keyCode, long durationMs) {
        nativeKeyEvent(1, keyCode);
        lcdHandler.postDelayed(() -> nativeKeyEvent(2, keyCode), durationMs);
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
        // Some vendor Android builds enforce BLUETOOTH_CONNECT even when the
        // app targets API 28. Request it on Android 12+ based on OS version,
        // not targetSdkVersion, before calling getBondedDevices().
        if (Build.VERSION.SDK_INT >= 31) {
            java.util.ArrayList<String> missing = new java.util.ArrayList<>();
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.BLUETOOTH_SCAN);
            if (!missing.isEmpty()) {
                requestPermissions(missing.toArray(new String[0]), REQ_BT);
                return;
            }
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

    private String describeBtError(Exception e) {
        StringBuilder message = new StringBuilder();
        Throwable t = e;
        int depth = 0;
        while (t != null && depth++ < 5) {
            if (message.length() > 0) message.append(" <- ");
            message.append(t.getClass().getSimpleName());
            if (t.getMessage() != null && !t.getMessage().isEmpty())
                message.append(": ").append(t.getMessage());
            t = t.getCause();
        }
        return message.toString();
    }

    private BluetoothSocket trySpp(String label, SocketFactory factory) throws Exception {
        BluetoothSocket s = null;
        try {
            s = factory.create();
            s.connect();
            appendLog("SPP подключён: " + label);
            return s;
        } catch (Exception e) {
            if (s != null) try { s.close(); } catch (IOException ignored) {}
            appendLog("SPP " + label + " ОШИБКА: " + describeBtError(e));
            throw e;
        }
    }

    private interface SocketFactory {
        BluetoothSocket create() throws Exception;
    }

    private BluetoothSocket openSppSocket(BluetoothDevice device) throws Exception {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            try { adapter.cancelDiscovery(); } catch (Exception e) {
                appendLog("cancelDiscovery: " + describeBtError(e));
            }
        }
        Exception last = null;
        try {
            return trySpp("secure UUID", () -> device.createRfcommSocketToServiceRecord(SPP));
        } catch (Exception e) { last = e; }
        try {
            return trySpp("insecure UUID", () -> device.createInsecureRfcommSocketToServiceRecord(SPP));
        } catch (Exception e) { last = e; }
        try {
            return trySpp("RFCOMM channel 1", () -> {
                java.lang.reflect.Method method = device.getClass().getMethod("createRfcommSocket", int.class);
                return (BluetoothSocket) method.invoke(device, 1);
            });
        } catch (Exception e) { last = e; }
        // Some Android vendor builds expose the insecure channel method even when
        // the public UUID methods are routed through a broken vendor Bluetooth stack.
        try {
            return trySpp("insecure channel 1", () -> {
                java.lang.reflect.Method method = device.getClass().getMethod("createInsecureRfcommSocket", int.class);
                return (BluetoothSocket) method.invoke(device, 1);
            });
        } catch (Exception e) { last = e; }
        throw last;
    }

    private void connect(BluetoothDevice device) {
        closeSocket();
        appendLog("Подключение SPP: " + device.getAddress());
        new Thread(() -> {
            BluetoothSocket candidate = null;
            try {
                candidate = openSppSocket(device);
                socket = candidate;
                socketInput = candidate.getInputStream();
                socketOutput = candidate.getOutputStream();
                synchronized (responseLock) { responseBuffer.setLength(0); }
                appendLog("SPP подключён. Ожидание ответов ELM327 до приглашения '>'.");
                InputStream in = socketInput;
                byte[] buffer = new byte[512];
                StringBuilder currentResponse = new StringBuilder();
                while (candidate.isConnected()) {
                    int n = in.read(buffer);
                    if (n < 0) break;
                    for (int i = 0; i < n; i++) {
                        char ch = (char) (buffer[i] & 0xff);
                        if (ch == '>') {
                            String response = currentResponse.toString().trim();
                            currentResponse.setLength(0);
                            synchronized (responseLock) {
                                responseBuffer.append(response).append("\n");
                                responseLock.notifyAll();
                            }
                            if (!response.isEmpty()) appendLog("ELM RX: " + response.replace("\r", " ").replace("\n", " "));
                        } else if (ch != '\0') {
                            currentResponse.append(ch);
                        }
                    }
                }
                appendLog("SPP соединение завершено");
            } catch (Exception e) {
                if (candidate != null) try { candidate.close(); } catch (IOException ignored) {}
                appendLog("Ошибка SPP после всех способов: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }, "cascade-spp-connect").start();
    }

    private void sendCommand(String command) {
        BluetoothSocket current = socket;
        if (current == null || !current.isConnected()) {
            appendLog("Нет SPP-соединения — команда не отправлена: " + command);
            return;
        }
        sender.execute(() -> {
            try {
                OutputStream out = socketOutput;
                if (out == null || current != socket || !current.isConnected()) {
                    appendLog("SPP уже отключён — команда отменена: " + command);
                    return;
                }
                synchronized (responseLock) { responseBuffer.setLength(0); }
                appendLog("ELM TX: " + command);
                out.write((command + "\r").getBytes(StandardCharsets.US_ASCII));
                out.flush();

                // ELM327 terminates each command response with the '>' prompt.
                long deadline = System.currentTimeMillis() + 5000;
                String response = null;
                synchronized (responseLock) {
                    while (current == socket && current.isConnected() &&
                           responseBuffer.length() == 0 &&
                           System.currentTimeMillis() < deadline) {
                        long remaining = deadline - System.currentTimeMillis();
                        if (remaining > 0) responseLock.wait(remaining);
                    }
                    if (responseBuffer.length() > 0) {
                        response = responseBuffer.toString().trim();
                        responseBuffer.setLength(0);
                    }
                }
                if (response == null || response.isEmpty()) {
                    appendLog("ELM timeout: нет ответа с приглашением '>' на " + command);
                } else {
                    appendLog("Ответ на " + command + ": " + response.replace("\n", " | "));
                    if ("0100".equalsIgnoreCase(command.replace(" ", ""))) {
                        appendLog("Проверка PID 00: " + nativeValidatePid00(response));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                appendLog("Ожидание ответа ELM прервано");
            } catch (Exception e) {
                appendLog("Ошибка обмена с ELM: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });;
    }

    private void exchangeBridgeCommand(String command) {
        BluetoothSocket current = socket;
        if (current == null || !current.isConnected()) {
            nativeSubmitElmResponse(command, "NO SPP CONNECTION");
            return;
        }
        String response = "";
        try {
            OutputStream out = socketOutput;
            if (out == null || current != socket || !current.isConnected()) {
                nativeSubmitElmResponse(command, "SPP DISCONNECTED");
                return;
            }
            synchronized (responseLock) {
                responseBuffer.setLength(0);
            }
            appendLog("CASCADE → ELM: " + command);
            out.write((command + "\r").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            long deadline = System.currentTimeMillis() + 5000;
            synchronized (responseLock) {
                while (current == socket && current.isConnected() &&
                       responseBuffer.length() == 0 && System.currentTimeMillis() < deadline) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining > 0) responseLock.wait(remaining);
                }
                if (responseBuffer.length() > 0) {
                    response = responseBuffer.toString().trim();
                    responseBuffer.setLength(0);
                }
            }
            if (response.isEmpty()) response = "ELM TIMEOUT";
            appendLog("CASCADE ← ELM (" + command + "): " + response.replace("\n", " | "));
            boolean accepted = nativeSubmitElmResponse(command, response);
            if (!accepted) appendLog("ELM bridge: ответ не распознан или запрос не поддержан");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            nativeSubmitElmResponse(command, "ELM INTERRUPTED");
        } catch (Exception e) {
            nativeSubmitElmResponse(command, "ELM ERROR");
            appendLog("ELM bridge error: " + e.getMessage());
        }
    }

    private void closeSocket() {
        BluetoothSocket current = socket;
        socket = null;
        socketInput = null;
        socketOutput = null;
        synchronized (responseLock) {
            responseBuffer.setLength(0);
            responseLock.notifyAll();
        }
        if (current != null) new Thread(() -> {
            try { current.close(); } catch (IOException ignored) {}
        }).start();
        appendLog("SPP отключён");
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQ_BT) {
            boolean allGranted = results.length > 0;
            for (int result : results) allGranted &= result == PackageManager.PERMISSION_GRANTED;
            if (allGranted) requestAndList();
            else Toast.makeText(this, "Нужно разрешение Bluetooth", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onDestroy() {
        lcdHandler.removeCallbacks(lcdRefresh);
        lcdHandler.removeCallbacks(elmBridgePoll);
        new Thread(() -> nativeStopEmulation(), "cascade-core-shutdown").start();
        lcdView = null;
        lcdBitmap = null;
        closeSocket();
        sender.shutdownNow();
        super.onDestroy();
    }
}