package com.bashworks.ugs.jobinfo.mqtt;

import com.bashworks.ugs.jobinfo.tracker.JobSnapshot;
import com.bashworks.ugs.jobinfo.tracker.JobState;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Background daemon service managing the live MQTT telemetry connection and publishing
 * 1-second throttled JobSnapshot updates.
 */
public final class MqttTelemetryService {

    private static final Logger LOGGER = Logger.getLogger(MqttTelemetryService.class.getName());
    private static final MqttTelemetryService INSTANCE = new MqttTelemetryService();

    public enum Status {
        DISABLED,
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    public interface StatusListener {
        void onMqttStatusChanged(Status status, String detail);
    }

    private final MiniMqttClient client = new MiniMqttClient();
    private final List<StatusListener> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ugs-mqtt-worker");
        t.setDaemon(true);
        return t;
    });

    private volatile MqttConfig config;
    private volatile Status status = Status.DISABLED;
    private volatile String lastDetail = "Disabled";

    private long lastPublishMillis = 0L;
    private JobState lastPublishedState = null;
    private String clientId;

    private MqttTelemetryService() {
        this.config = MqttConfig.load();
        this.clientId = "UGS-" + UUID.randomUUID().toString().substring(0, 8);

        // Background monitor: attempts reconnect if enabled and sends periodic keepalive ping
        executor.scheduleWithFixedDelay(this::backgroundMaintenance, 3, 5, TimeUnit.SECONDS);

        if (config.isEnabled()) {
            setStatus(Status.DISCONNECTED, "Ready to connect");
            triggerReconnect();
        } else {
            setStatus(Status.DISABLED, "Remote broadcast disabled");
        }
    }

    public static MqttTelemetryService getInstance() {
        return INSTANCE;
    }

    public MqttConfig getConfig() {
        return config;
    }

    public Status getStatus() {
        return status;
    }

    public String getLastDetail() {
        return lastDetail;
    }

    public void addStatusListener(StatusListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            listener.onMqttStatusChanged(this.status, this.lastDetail);
        }
    }

    public void removeStatusListener(StatusListener listener) {
        listeners.remove(listener);
    }

    public synchronized void updateConfig(MqttConfig newConfig) {
        this.config = Objects.requireNonNull(newConfig, "newConfig");
        this.config.save();

        if (!config.isEnabled()) {
            executor.submit(() -> {
                client.disconnect();
                setStatus(Status.DISABLED, "Remote broadcast disabled");
            });
        } else {
            executor.submit(() -> {
                client.disconnect();
                triggerReconnect();
            });
        }
    }

    public void onSnapshot(JobSnapshot snapshot) {
        if (!config.isEnabled() || snapshot == null) {
            return;
        }

        long now = System.currentTimeMillis();
        JobState state = snapshot.state();

        boolean isStreaming = (state == JobState.RUNNING || state == JobState.PAUSED);
        boolean stateChanged = (state != lastPublishedState);

        boolean shouldPublish;
        if (stateChanged) {
            shouldPublish = true;
        } else if (isStreaming) {
            shouldPublish = (now - lastPublishMillis >= 1000); // 1.0 second throttle
        } else {
            shouldPublish = (now - lastPublishMillis >= 30000); // 30s heartbeat when idle
        }

        if (shouldPublish) {
            lastPublishMillis = now;
            lastPublishedState = state;
            executor.submit(() -> doPublish(snapshot));
        }
    }

    private void backgroundMaintenance() {
        if (!config.isEnabled()) {
            return;
        }

        if (!client.isConnected()) {
            doConnect();
        } else {
            try {
                client.ping();
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "MQTT ping failed, reconnecting: {0}", e.getMessage());
                client.disconnect();
                setStatus(Status.DISCONNECTED, "Connection dropped, reconnecting…");
            }
        }
    }

    private void triggerReconnect() {
        executor.submit(this::doConnect);
    }

    private synchronized void doConnect() {
        if (!config.isEnabled() || client.isConnected()) {
            return;
        }

        setStatus(Status.CONNECTING, "Connecting to " + config.getHost() + "…");
        try {
            String willTopic = config.getTopic();
            byte[] willPayload = "{\"state\":\"OFFLINE\"}".getBytes(StandardCharsets.UTF_8);

            client.connect(
                    config.getHost(),
                    config.getPort(),
                    config.isUseTls(),
                    clientId,
                    config.getUsername(),
                    config.getPassword(),
                    willTopic,
                    willPayload,
                    30, // keepalive
                    8000 // timeout ms
            );

            setStatus(Status.CONNECTED, "Connected to " + config.getHost());
            LOGGER.info("MQTT telemetry connected to: " + config.getHost() + ":" + config.getPort());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to connect to MQTT broker: {0}", e.getMessage());
            setStatus(Status.ERROR, e.getMessage());
        }
    }

    private void doPublish(JobSnapshot snapshot) {
        if (!client.isConnected()) {
            return;
        }
        try {
            String json = serializeJson(snapshot);
            byte[] payload = json.getBytes(StandardCharsets.UTF_8);
            // Publish with retain=true so mobile clients get current status immediately
            client.publish(config.getTopic(), payload, true);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to publish MQTT snapshot: {0}", e.getMessage());
            client.disconnect();
            setStatus(Status.DISCONNECTED, "Publish failed, reconnecting…");
        }
    }

    private void setStatus(Status newStatus, String detail) {
        this.status = newStatus;
        this.lastDetail = (detail != null) ? detail : "";
        for (StatusListener l : listeners) {
            l.onMqttStatusChanged(this.status, this.lastDetail);
        }
    }

    public static String testConnection(MqttConfig testCfg) {
        try (MiniMqttClient testClient = new MiniMqttClient()) {
            String testId = "Test-" + UUID.randomUUID().toString().substring(0, 6);
            testClient.connect(
                    testCfg.getHost(),
                    testCfg.getPort(),
                    testCfg.isUseTls(),
                    testId,
                    testCfg.getUsername(),
                    testCfg.getPassword(),
                    null,
                    null,
                    15,
                    6000
            );
            testClient.ping();
            testClient.disconnect();
            return null; // success
        } catch (Exception e) {
            return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }
    }

    public static String serializeJson(JobSnapshot s) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        append(sb, "state", s.state().name()).append(',');
        append(sb, "fileName", s.fileName()).append(',');
        appendNumber(sb, "progressPercent", String.format(Locale.US, "%.1f", s.progressPercent())).append(',');
        appendNumber(sb, "totalTimeSeconds", String.format(Locale.US, "%.1f", s.totalTimeSeconds())).append(',');
        appendNumber(sb, "elapsedSeconds", String.format(Locale.US, "%.1f", s.elapsedTimeSeconds())).append(',');
        appendNumber(sb, "remainingSeconds", String.format(Locale.US, "%.1f", s.remainingTimeSeconds())).append(',');

        append(sb, "eta", s.etaString()).append(',');
        appendNumber(sb, "feedOverride", String.valueOf(s.feedOverridePercent())).append(',');
        appendNumber(sb, "completedCommands", String.valueOf(s.completedCommands())).append(',');
        appendNumber(sb, "totalCommands", String.valueOf(s.totalCommands())).append(',');
        append(sb, "machineProfile", s.machineProfile() != null ? s.machineProfile().getName() : "Unknown").append(',');
        appendNumber(sb, "timestamp", String.valueOf(System.currentTimeMillis()));
        sb.append('}');
        return sb.toString();
    }

    private static StringBuilder append(StringBuilder sb, String key, String val) {
        sb.append('"').append(escape(key)).append("\":\"").append(escape(val)).append('"');
        return sb;
    }

    private static StringBuilder appendNumber(StringBuilder sb, String key, String num) {
        sb.append('"').append(escape(key)).append("\":").append(num);
        return sb;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
