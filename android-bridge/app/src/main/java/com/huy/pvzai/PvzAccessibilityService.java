package com.huy.pvzai;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.HardwareBuffer;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PvzAccessibilityService extends AccessibilityService {
    public static volatile PvzAccessibilityService INSTANCE;
    private static final int PORT = 8765;

    private ServerSocket serverSocket;
    private Thread serverThread;
    private final ExecutorService screenshotExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        INSTANCE = this;
        startServer();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        INSTANCE = null;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        screenshotExecutor.shutdownNow();
        super.onDestroy();
    }

    private void startServer() {
        if (serverThread != null) return;
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT, 30, java.net.InetAddress.getByName("127.0.0.1"));
                while (!Thread.currentThread().isInterrupted()) {
                    Socket socket = serverSocket.accept();
                    new Thread(() -> handle(socket), "pvz-http").start();
                }
            } catch (Exception ignored) {
            }
        }, "pvz-bridge-server");
        serverThread.start();
    }

    private void handle(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(12000);
            BufferedInputStream in = new BufferedInputStream(s.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(s.getOutputStream());
            String requestLine = readLine(in);
            if (requestLine == null) return;

            int contentLength = 0;
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                String lower = line.toLowerCase(Locale.US);
                if (lower.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                }
            }
            byte[] body = new byte[contentLength];
            int got = 0;
            while (got < contentLength) {
                int r = in.read(body, got, contentLength - got);
                if (r < 0) break;
                got += r;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) { sendText(out, 400, "bad request"); return; }
            String method = parts[0];
            String path = parts[1];

            if ("GET".equals(method) && "/health".equals(path)) {
                sendJson(out, 200, "{\"ok\":true,\"service\":\"pvz-ai-bridge\",\"port\":8765}");
                return;
            }
            if ("GET".equals(method) && "/size".equals(path)) {
                int w = getResources().getDisplayMetrics().widthPixels;
                int h = getResources().getDisplayMetrics().heightPixels;
                sendJson(out, 200, String.format(Locale.US, "{\"width\":%d,\"height\":%d}", w, h));
                return;
            }
            if ("GET".equals(method) && "/screen".equals(path)) {
                byte[] jpg = captureJpeg();
                if (jpg == null) { sendText(out, 503, "screenshot unavailable"); return; }
                sendBytes(out, 200, "image/jpeg", jpg);
                return;
            }
            if ("POST".equals(method) && "/tap".equals(path)) {
                String b = new String(body, StandardCharsets.UTF_8);
                int x = jsonInt(b, "x");
                int y = jsonInt(b, "y");
                boolean ok = tap(x, y);
                sendJson(out, ok ? 200 : 500, "{\"ok\":" + ok + "}");
                return;
            }
            if ("POST".equals(method) && "/swipe".equals(path)) {
                String b = new String(body, StandardCharsets.UTF_8);
                int x1 = jsonInt(b, "x1");
                int y1 = jsonInt(b, "y1");
                int x2 = jsonInt(b, "x2");
                int y2 = jsonInt(b, "y2");
                int duration = jsonIntOptional(b, "duration_ms", 250);
                boolean ok = swipe(x1, y1, x2, y2, duration);
                sendJson(out, ok ? 200 : 500, "{\"ok\":" + ok + "}");
                return;
            }

            sendText(out, 404, "not found");
        } catch (Exception ignored) {
        }
    }

    private byte[] captureJpeg() {
        final CountDownLatch latch = new CountDownLatch(1);
        final byte[][] result = new byte[1][];
        try {
            screenshotExecutor.execute(() -> takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    screenshotExecutor,
                    new TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(ScreenshotResult screenshot) {
                            HardwareBuffer hb = screenshot.getHardwareBuffer();
                            try {
                                Bitmap hardware = Bitmap.wrapHardwareBuffer(hb, screenshot.getColorSpace());
                                if (hardware != null) {
                                    Bitmap software = hardware.copy(Bitmap.Config.ARGB_8888, false);
                                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                                    software.compress(Bitmap.CompressFormat.JPEG, 55, bos);
                                    result[0] = bos.toByteArray();
                                    software.recycle();
                                    hardware.recycle();
                                }
                            } catch (Throwable ignored) {
                            } finally {
                                hb.close();
                                latch.countDown();
                            }
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            latch.countDown();
                        }
                    }
            ));
            if (!latch.await(7, TimeUnit.SECONDS)) return null;
            return result[0];
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean tap(int x, int y) {
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(
                        new android.graphics.Path() {{ moveTo(x, y); }}, 0, 60);
        return dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    private boolean swipe(int x1, int y1, int x2, int y2, int duration) {
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, Math.max(50, Math.min(3000, duration)));
        return dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    private static String readLine(BufferedInputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int prev = -1;
        while (true) {
            int b = in.read();
            if (b < 0) return bos.size() == 0 ? null : bos.toString(StandardCharsets.UTF_8);
            if (prev == '\r' && b == '\n') {
                byte[] arr = bos.toByteArray();
                if (arr.length > 0 && arr[arr.length - 1] == '\r') bos.reset();
                else bos.reset();
                return new String(arr, 0, Math.max(0, arr.length - 1), StandardCharsets.UTF_8);
            }
            bos.write(b);
            prev = b;
        }
    }

    private static int jsonInt(String s, String key) {
        return jsonIntOptional(s, key, 0);
    }

    private static int jsonIntOptional(String s, String key, int fallback) {
        String needle = "\"" + key + "\"";
        int p = s.indexOf(needle);
        if (p < 0) return fallback;
        p = s.indexOf(':', p);
        if (p < 0) return fallback;
        int i = p + 1;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        int j = i;
        if (j < s.length() && s.charAt(j) == '-') j++;
        while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
        if (j == i) return fallback;
        try { return Integer.parseInt(s.substring(i, j)); } catch (Exception e) { return fallback; }
    }

    private static void sendText(OutputStream out, int code, String body) throws IOException {
        sendBytes(out, code, "text/plain; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendJson(OutputStream out, int code, String json) throws IOException {
        sendBytes(out, code, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendBytes(OutputStream out, int code, String contentType, byte[] bytes) throws IOException {
        String status = code == 200 ? "OK" : (code == 400 ? "Bad Request" : (code == 404 ? "Not Found" : "Error"));
        String header = "HTTP/1.1 " + code + " " + status + "\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }
}
