package com.limelight.utils;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PerformanceDataTracker {

    private static final String API_URL = "https://script.google.com/macros/s/AKfycbwU-jKQdSAHgd-emZjqkF9TH3hJzNmX1caxeTybuhCzjTWOsxQHa62xIYGgGQHwecOraw/exec";

    // Constants for field names
    private static final String FIELD_DEVICE = "Device";
    private static final String FIELD_OS_VERSION = "OS Version";
    private static final String FIELD_APP_VERSION = "App Version";
    private static final String FIELD_CODEC = "Codec";
    private static final String FIELD_STATS_LOG = "Performance Stats Log";
    private static final String FIELD_DECODING_TIME = "Decoding Time (ms)";
    private static final String FIELD_BITRATE = "Bitrate (Mbps)";
    private static final String FIELD_RESOLUTION = "Resolution";
    private static final String FIELD_FRAME_RATE = "Frame Rate (FPS)";
    private static final String FIELD_AVERAGE = "Average Latency";
    private static final String FIELD_FRAME_PACING = "Frame Pacing";
    private static final String FIELD_DATETIME = "Date/Time";

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public void sendPerformanceStatistics(
            String device,
            String osVersion,
            String appVersion,
            String codec,
            String decodingTimeMs,
            String stats,
            String bitrateMbps,
            String resolution,
            String frameRateFps,
            String average,
            String framePacing,
            String dateTime) {

        executorService.execute(() -> sendData(device, osVersion, appVersion, codec, decodingTimeMs, stats, bitrateMbps, resolution, frameRateFps, average, framePacing, dateTime));
    }

    private void sendData(String device, String osVersion, String appVersion, String codec, String decodingTimeMs, String stats, String bitrateMbps, String resolution, String frameRateFps, String average, String framePacing, String dateTime) {
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(API_URL);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setDoOutput(true);

            JSONObject jsonObject = new JSONObject();
            jsonObject.put(FIELD_DEVICE, device);
            jsonObject.put(FIELD_OS_VERSION, osVersion);
            jsonObject.put(FIELD_APP_VERSION, appVersion);
            jsonObject.put(FIELD_CODEC, codec);
            jsonObject.put(FIELD_DECODING_TIME, decodingTimeMs);
            jsonObject.put(FIELD_STATS_LOG, stats);
            jsonObject.put(FIELD_BITRATE, bitrateMbps);
            jsonObject.put(FIELD_RESOLUTION, resolution);
            jsonObject.put(FIELD_FRAME_RATE, frameRateFps);
            jsonObject.put(FIELD_AVERAGE, average);
            jsonObject.put(FIELD_FRAME_PACING, framePacing);
            jsonObject.put(FIELD_DATETIME, dateTime);

            OutputStream os = new BufferedOutputStream(urlConnection.getOutputStream());
            os.write(jsonObject.toString().getBytes());
            os.flush();
            os.close();

            int responseCode = urlConnection.getResponseCode();
            Log.d("PerformanceDataTracker", "Response Code: " + responseCode);

            BufferedReader br = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            br.close();

            Log.d("PerformanceDataTracker", "Response: " + response.toString());

        } catch (Exception e) {
            Log.e("PerformanceDataTracker", "Error: " + e.getMessage());
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }
}
