package application.net;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class HttpClientLite {
    private final String base;

    public HttpClientLite(String base) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    // POST with JSON body (used by /move and /action)
    public String post(String path, String query, String jsonBody) throws Exception {
        String urlStr = base + path + (query == null || query.isEmpty() ? "" : ("?" + query));
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        try (Scanner sc = new Scanner(is, StandardCharsets.UTF_8)) {
            sc.useDelimiter("\\A");
            return sc.hasNext() ? sc.next() : "";
        } finally {
            conn.disconnect();
        }
    }

    // POST with application/x-www-form-urlencoded body (used by /join)
    public String post(String path, String formUrlEncoded) throws Exception {
        String urlStr = base + path;
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");

        byte[] bytes = formUrlEncoded.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        try (Scanner sc = new Scanner(is, StandardCharsets.UTF_8)) {
            sc.useDelimiter("\\A");
            return sc.hasNext() ? sc.next() : "";
        } finally {
            conn.disconnect();
        }
    }

    // SSE stream: returns a Scanner you can read line-by-line
    public Scanner getEvents(String path) throws Exception {
        String urlStr = base + path;
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setDoInput(true);
        conn.setRequestProperty("Accept", "text/event-stream");
        conn.setReadTimeout(0); // keep alive
        InputStream is = conn.getInputStream();
        // Caller must read lines and not close Scanner until done.
        return new Scanner(is, StandardCharsets.UTF_8);
    }
}