package com.limelight.binding.audio;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.core.content.ContextCompat;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.nvstream.jni.MoonBridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MicrophoneCaptureManager {
    public interface LevelListener {
        void onLevelUpdate(double level, boolean signalDetected, String status);
    }

    public static final class InputDeviceEntry {
        public final int id;
        public final String label;

        public InputDeviceEntry(int id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    public static final String ECHO_PLATFORM = "platform";
    public static final String ECHO_WEBRTC = "webrtc";

    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNEL_COUNT = 1;
    private static final int FRAME_SIZE = 960;
    private static final int DEFAULT_BITRATE = 24000;
    private int bitrate = DEFAULT_BITRATE;
    private static final int LEVEL_UPDATE_INTERVAL_MS = 50;
    private static final int SIGNAL_PEAK_THRESHOLD = 250;
    private static final double SIGNAL_RMS_THRESHOLD = 90.0;
    private static final double PREVIEW_RMS_FLOOR = 40.0;
    private static final double PREVIEW_RMS_CEILING = 4000.0;
    private static final double PREVIEW_PEAK_CEILING = 12000.0;
    private static final double PREVIEW_DECAY_FACTOR = 0.84;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AudioRecord audioRecord;
    private Thread captureThread;
    private volatile boolean running;
    private boolean streamingToHost;
    private LevelListener levelListener;
    private String currentStatus;
    private double currentLevel;
    private boolean signalDetected;
    // Echo cancellation, noise suppression and AGC of the stream being sent (none in preview)
    private volatile WebRtcApm apm;
    private final List<AudioEffect> effects = new ArrayList<>();

    public MicrophoneCaptureManager(Context context) {
        this.context = context.getApplicationContext();
        this.currentStatus = string(R.string.microphone_preview_inactive);
    }

    public static boolean hasRecordAudioPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED;
    }

    public static List<InputDeviceEntry> getAvailableInputDevices(Context context) {
        Map<String, InputDeviceEntry> uniqueEntries = new LinkedHashMap<>();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return new ArrayList<>();
        }

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return new ArrayList<>();
        }

        for (AudioDeviceInfo deviceInfo : audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            String label = describeDevice(deviceInfo);
            if (!uniqueEntries.containsKey(label)) {
                uniqueEntries.put(label, new InputDeviceEntry(deviceInfo.getId(), label));
            }
        }

        return new ArrayList<>(uniqueEntries.values());
    }

    public boolean startPreview(int preferredDeviceId, LevelListener listener) {
        return startCapture(preferredDeviceId, listener, false, "off", false, false);
    }

    // Opus bitrate of the streamed microphone, in bits per second; applies from the next start
    public void setBitrate(int bitsPerSecond) {
        bitrate = bitsPerSecond > 0 ? bitsPerSecond : DEFAULT_BITRATE;
    }

    public boolean startStreaming(int preferredDeviceId, LevelListener listener) {
        return startStreaming(preferredDeviceId, listener, "off", false, false);
    }

    // echoCancel: off, ECHO_PLATFORM or ECHO_WEBRTC. Noise suppression and AGC use the platform
    // effects with ECHO_PLATFORM, WebRTC otherwise.
    public boolean startStreaming(int preferredDeviceId, LevelListener listener,
                                  String echoCancel, boolean noiseSuppress, boolean autoGain) {
        return startCapture(preferredDeviceId, listener, true, echoCancel, noiseSuppress, autoGain);
    }

    // Called by the renderer with each PCM buffer it actually queued for playback. The writes
    // block at the device's playback rate, so the echo reference neither drifts from the
    // microphone's clock nor runs behind its echo; AEC3 estimates the remaining delay
    // (track buffer, mixer and HAL, air, capture path) by itself.
    public void onPlayback(short[] pcm, int channels) {
        WebRtcApm a = apm;
        if (a != null) {
            a.render(pcm, channels);
        }
    }

    public void stop() {
        AudioRecord recordToRelease;
        Thread threadToJoin;
        boolean wasStreaming;

        running = false;
        recordToRelease = audioRecord;
        threadToJoin = captureThread;
        wasStreaming = streamingToHost;

        if (recordToRelease != null) {
            try {
                recordToRelease.stop();
            }
            catch (IllegalStateException ignored) {
            }
        }

        if (threadToJoin != null) {
            try {
                threadToJoin.join(2000);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        captureThread = null;
        audioRecord = null;
        for (AudioEffect effect : effects) {
            effect.release();
        }
        effects.clear();
        if (recordToRelease != null) {
            recordToRelease.release();
        }
        WebRtcApm processing = apm;
        apm = null;
        if (processing != null) {
            processing.release();
        }

        if (wasStreaming) {
            MoonBridge.stopMicrophoneStreaming();
            MoonBridge.cleanupMicrophoneEncoder();
        }
        streamingToHost = false;

        currentLevel = 0.0;
        signalDetected = false;
        dispatchStatus(string(R.string.microphone_preview_inactive), 0.0, false);
    }

    private boolean startCapture(int preferredDeviceId, LevelListener listener, boolean streamToHost,
                                 String echoCancel, boolean noiseSuppress, boolean autoGain) {
        CaptureConfig config;
        AudioRecord newRecord;
        final int bufferSamples;
        boolean platform = streamToHost && ECHO_PLATFORM.equals(echoCancel);
        boolean webrtcAec = streamToHost && ECHO_WEBRTC.equals(echoCancel);
        WebRtcApm processing = null;

        stop();
        levelListener = listener;

        if (!hasRecordAudioPermission(context)) {
            dispatchStatus(string(R.string.microphone_preview_permission_required), 0.0, false);
            return false;
        }

        if (streamToHost && !MoonBridge.isMicrophoneStreamActive()) {
            dispatchStatus(string(R.string.microphone_host_not_negotiated), 0.0, false);
            return false;
        }

        if (streamToHost && !platform && (webrtcAec || noiseSuppress || autoGain)) {
            processing = new WebRtcApm(webrtcAec, noiseSuppress, autoGain);
            if (!processing.isInitialized()) {
                LimeLog.warning("Microphone: WebRTC processing failed, sending unprocessed audio");
                processing = null;
            }
        }

        // Software processing wants a source without the HAL's voice chain (non-linear, and
        // latency) in front of AEC3; platform effects want the communication source.
        int[] sources = null;
        if (processing != null) {
            sources = new int[] {MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC};
        }
        else if (platform) {
            sources = new int[] {MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC};
        }

        config = createCaptureConfig(preferredDeviceId, sources);
        if (config == null) {
            if (processing != null) {
                processing.release();
            }
            dispatchStatus(string(R.string.microphone_preview_open_failed), 0.0, false);
            return false;
        }

        newRecord = config.record;
        bufferSamples = config.bufferSamples;

        if (streamToHost && MoonBridge.setupMicrophoneEncoder(SAMPLE_RATE, CHANNEL_COUNT, bitrate) != 0) {
            if (processing != null) {
                processing.release();
            }
            newRecord.release();
            dispatchStatus(string(R.string.microphone_encoder_setup_failed), 0.0, false);
            return false;
        }

        try {
            newRecord.startRecording();
        }
        catch (IllegalStateException e) {
            if (streamToHost) {
                MoonBridge.cleanupMicrophoneEncoder();
            }
            if (processing != null) {
                processing.release();
            }
            newRecord.release();
            dispatchStatus(string(R.string.microphone_capture_start_failed), 0.0, false);
            return false;
        }

        if (newRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            if (streamToHost) {
                MoonBridge.cleanupMicrophoneEncoder();
            }
            if (processing != null) {
                processing.release();
            }
            newRecord.release();
            dispatchStatus(string(R.string.microphone_capture_start_failed), 0.0, false);
            return false;
        }

        if (platform) {
            int session = newRecord.getAudioSessionId();
            attach("echo canceller", AcousticEchoCanceler.isAvailable() ? AcousticEchoCanceler.create(session) : null);
            if (noiseSuppress) {
                attach("noise suppressor", NoiseSuppressor.isAvailable() ? NoiseSuppressor.create(session) : null);
            }
            if (autoGain) {
                attach("gain control", AutomaticGainControl.isAvailable() ? AutomaticGainControl.create(session) : null);
            }
        }
        apm = processing;

        LimeLog.info(String.format((Locale) null,
                "Microphone capture active using source %s, device %s, buffer %d samples",
                config.sourceName,
                config.deviceLabel,
                bufferSamples));
        if (streamToHost) {
            LimeLog.info("Microphone: echo cancel " + echoCancel + ", noise suppress " + noiseSuppress +
                    ", auto gain " + autoGain + ", WebRTC " + (processing != null));
        }

        audioRecord = newRecord;
        streamingToHost = streamToHost;
        currentStatus = config.statusMessage;
        currentLevel = 0.0;
        signalDetected = false;
        running = true;

        if (streamToHost) {
            MoonBridge.startMicrophoneStreaming();
        }

        // Streams read 20 ms at a time: a whole AudioRecord buffer would add its length as latency
        final int readSamples = streamToHost ? FRAME_SIZE : bufferSamples;
        final WebRtcApm loopProcessing = processing;
        final AudioRecord loopRecord = newRecord;
        captureThread = new Thread(() -> runCaptureLoop(readSamples, loopRecord, loopProcessing),
                streamToHost ? "MicStreamCapture" : "MicPreviewCapture");
        captureThread.start();
        dispatchStatus(config.statusMessage, 0.0, false);
        return true;
    }

    private void runCaptureLoop(int bufferSamples, AudioRecord record, WebRtcApm processing) {
        short[] readBuffer = new short[bufferSamples];
        int pendingPeak = 0;
        double pendingRms = 0.0;
        long lastUpdateTime = SystemClock.elapsedRealtime();
        long samplesSent = 0, frames = 0, processNs = 0;

        while (running && audioRecord != null) {
            int samplesRead;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                samplesRead = audioRecord.read(readBuffer, 0, readBuffer.length, AudioRecord.READ_BLOCKING);
            }
            else {
                samplesRead = audioRecord.read(readBuffer, 0, readBuffer.length);
            }

            if (samplesRead <= 0) {
                continue;
            }

            if (processing != null) {
                // WebRTC takes 10 ms frames; a short read leaves its tail unprocessed
                long t = System.nanoTime();
                for (int i = 0; i + WebRtcApm.FRAME_SAMPLES <= samplesRead; i += WebRtcApm.FRAME_SAMPLES) {
                    processing.capture(readBuffer, i);
                }
                processNs += System.nanoTime() - t;
                frames++;
            }

            SignalStats signalStats = calculateSignalStats(readBuffer, samplesRead);
            if (signalStats.peak > pendingPeak) {
                pendingPeak = signalStats.peak;
            }
            if (signalStats.rms > pendingRms) {
                pendingRms = signalStats.rms;
            }

            if (streamingToHost) {
                int queued = MoonBridge.queueMicrophonePcm(readBuffer, samplesRead);
                if (queued < 0) {
                    LimeLog.warning("Failed to queue microphone PCM data for native encoding");
                }
                long before = samplesSent;
                samplesSent += samplesRead;
                if (before < SAMPLE_RATE && samplesSent >= SAMPLE_RATE) {
                    logInputLatency(record, samplesSent);
                }
            }

            long now = SystemClock.elapsedRealtime();
            if (now - lastUpdateTime >= LEVEL_UPDATE_INTERVAL_MS) {
                double instantaneousLevel = calculatePreviewLevel(pendingPeak, pendingRms);
                double nextLevel = Math.max(instantaneousLevel, currentLevel * PREVIEW_DECAY_FACTOR);
                boolean nextSignalDetected = pendingPeak >= SIGNAL_PEAK_THRESHOLD ||
                        pendingRms >= SIGNAL_RMS_THRESHOLD;
                pendingPeak = 0;
                pendingRms = 0.0;
                lastUpdateTime = now;
                currentLevel = nextLevel;
                signalDetected = nextSignalDetected;
                dispatchStatus(currentStatus, nextLevel, nextSignalDetected);
            }
        }

        if (processing != null && frames > 0) {
            LimeLog.info("Microphone: WebRTC processing " + processNs / frames / 1000 + " us per " +
                    bufferSamples * 1000 / SAMPLE_RATE + " ms read");
        }
    }

    private void attach(String name, AudioEffect effect) {
        if (effect == null) {
            LimeLog.warning("Microphone: no platform " + name);
            return;
        }
        try {
            effect.setEnabled(true);
            LimeLog.info("Microphone: platform " + name + " \"" + effect.getDescriptor().name + "\"" +
                    (effect.getEnabled() ? "" : " not enabled"));
        } catch (RuntimeException e) {
            LimeLog.warning("Microphone: platform " + name + " failed: " + e);
        }
        effects.add(effect);
    }

    // Time from capturing the newest sample to read() returning it
    private static void logInputLatency(AudioRecord record, long framesRead) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        AudioTimestamp ts = new AudioTimestamp();
        if (record.getTimestamp(ts, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
            long capturedNs = ts.nanoTime + (framesRead - 1 - ts.framePosition) * 1_000_000_000L / SAMPLE_RATE;
            LimeLog.info("Microphone: input latency " + (System.nanoTime() - capturedNs) / 1_000_000 + " ms");
        }
    }

    private CaptureConfig createCaptureConfig(int preferredDeviceId, int[] sources) {
        int[] preferredSources = sources != null ? sources : new int[] {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? MediaRecorder.AudioSource.UNPROCESSED : -1,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC
        };
        int minBufferSizeBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minBufferSizeBytes <= 0) {
            minBufferSizeBytes = FRAME_SIZE * 4 * 2;
        }
        int bufferSizeBytes = Math.max(minBufferSizeBytes, FRAME_SIZE * 4 * 2);
        int bufferSamples = Math.max(FRAME_SIZE, bufferSizeBytes / 2);
        boolean missingSelectedDevice = false;
        AudioDeviceInfo preferredDevice = null;
        String preferredDeviceLabel = string(R.string.microphone_device_default);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && preferredDeviceId != 0) {
            preferredDevice = findInputDevice(preferredDeviceId);
            if (preferredDevice != null) {
                preferredDeviceLabel = describeDevice(preferredDevice);
            }
            else {
                missingSelectedDevice = true;
            }
        }

        for (int source : preferredSources) {
            AudioRecord candidate;

            if (source < 0) {
                continue;
            }

            candidate = buildAudioRecord(source, bufferSizeBytes);
            if (candidate == null) {
                continue;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && preferredDevice != null) {
                boolean preferredApplied = candidate.setPreferredDevice(preferredDevice);
                if (!preferredApplied) {
                    LimeLog.info("Preferred microphone device selection was rejected by AudioRecord");
                }
            }

            if (candidate.getState() != AudioRecord.STATE_INITIALIZED) {
                candidate.release();
                continue;
            }

            CaptureConfig config = new CaptureConfig();
            config.record = candidate;
            config.bufferSamples = bufferSamples;
            config.sourceName = audioSourceToString(source);
            config.deviceLabel = preferredDevice != null ? preferredDeviceLabel : string(R.string.microphone_device_default);
            config.statusMessage = missingSelectedDevice ?
                    string(R.string.microphone_preview_selected_missing) :
                    (preferredDevice != null ?
                            string(R.string.microphone_preview_selected_active) :
                            string(R.string.microphone_preview_default_active));
            return config;
        }

        return null;
    }

    private AudioRecord buildAudioRecord(int audioSource, int bufferSizeBytes) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioRecord.Builder builder = new AudioRecord.Builder()
                    .setAudioSource(audioSource)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build())
                    .setBufferSizeInBytes(bufferSizeBytes);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Let dictation (Gboard, other IMEs) take the microphone while we hold it
                builder.setPrivacySensitive(false);
            }
            return builder.build();
        }

        return new AudioRecord(audioSource,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSizeBytes);
    }

    private AudioDeviceInfo findInputDevice(int deviceId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null;
        }

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return null;
        }

        for (AudioDeviceInfo deviceInfo : audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            if (deviceInfo.getId() == deviceId) {
                return deviceInfo;
            }
        }

        return null;
    }

    private void dispatchStatus(String status, double level, boolean detected) {
        currentStatus = status;
        mainHandler.post(() -> {
            if (levelListener != null) {
                levelListener.onLevelUpdate(level, detected, status);
            }
        });
    }

    private String string(int resId) {
        return context.getString(resId);
    }

    private static SignalStats calculateSignalStats(short[] samples, int sampleCount) {
        int peak = 0;
        double sumSquares = 0.0;

        for (int i = 0; i < sampleCount; i++) {
            int sample = Math.abs(samples[i]);
            if (sample > peak) {
                peak = sample;
            }
            sumSquares += (double) sample * sample;
        }

        SignalStats signalStats = new SignalStats();
        signalStats.peak = peak;
        signalStats.rms = sampleCount > 0 ? Math.sqrt(sumSquares / sampleCount) : 0.0;
        return signalStats;
    }

    private static double calculatePreviewLevel(int peak, double rms) {
        double rmsLevel = (Math.log10(Math.max(rms, PREVIEW_RMS_FLOOR)) -
                Math.log10(PREVIEW_RMS_FLOOR)) /
                (Math.log10(PREVIEW_RMS_CEILING) - Math.log10(PREVIEW_RMS_FLOOR));
        double peakLevel = Math.min(1.0, peak / PREVIEW_PEAK_CEILING);
        return Math.max(0.0, Math.min(1.0, Math.max(rmsLevel, peakLevel)));
    }

    private static String audioSourceToString(int audioSource) {
        if (audioSource == MediaRecorder.AudioSource.VOICE_RECOGNITION) {
            return "VOICE_RECOGNITION";
        }
        if (audioSource == MediaRecorder.AudioSource.VOICE_COMMUNICATION) {
            return "VOICE_COMMUNICATION";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                audioSource == MediaRecorder.AudioSource.UNPROCESSED) {
            return "UNPROCESSED";
        }
        return "MIC";
    }

    private static String describeDevice(AudioDeviceInfo deviceInfo) {
        switch (deviceInfo.getType()) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC:
                return "Built-in microphone";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                return "Bluetooth headset microphone";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                return "Bluetooth audio input";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                return "Wired headset microphone";
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                return "USB microphone";
            default:
                CharSequence productName = deviceInfo.getProductName();
                if (productName != null && productName.length() > 0) {
                    return productName.toString();
                }
                return "Input device " + deviceInfo.getId();
        }
    }

    private static final class SignalStats {
        int peak;
        double rms;
    }

    private static final class CaptureConfig {
        AudioRecord record;
        int bufferSamples;
        String sourceName;
        String deviceLabel;
        String statusMessage;
    }
}
