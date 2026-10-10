package com.bashworks.ugs.jobinfo.mqtt;

import java.util.Objects;
import java.util.UUID;
import java.util.prefs.Preferences;

/**
 * Configuration and persistence for remote MQTT telemetry broadcast.
 */
public final class MqttConfig {

    public static final String DEFAULT_PUBLIC_HOST = "broker.hivemq.com";
    public static final int DEFAULT_PUBLIC_PORT = 1883;
    public static final int DEFAULT_TLS_PORT = 8883;

    private boolean enabled;
    private String host;
    private int port;
    private boolean useTls;
    private String username;
    private String password;
    private String topic;

    public MqttConfig() {
        this.enabled = false;
        this.host = DEFAULT_PUBLIC_HOST;
        this.port = DEFAULT_PUBLIC_PORT;
        this.useTls = false;
        this.username = "";
        this.password = "";
        this.topic = generateDefaultTopic();
    }

    public static String generateDefaultTopic() {
        String shortId = UUID.randomUUID().toString().substring(0, 8);
        return "gbashware/ugs/" + shortId + "/status";
    }

    private static Preferences getPrefs() {
        try {
            Class<?> nbPrefClass = Class.forName("org.openide.util.NbPreferences");
            return (Preferences) nbPrefClass.getMethod("forModule", Class.class).invoke(null, MqttConfig.class);
        } catch (Throwable fallback) {
            try {
                return Preferences.userNodeForPackage(MqttConfig.class);
            } catch (Throwable noRegistry) {
                return null;
            }
        }
    }

    public static MqttConfig load() {
        MqttConfig c = new MqttConfig();
        Preferences p = getPrefs();
        if (p == null) {
            return c;
        }
        try {
            c.enabled = p.getBoolean("mqtt_enabled", false);
            c.host = p.get("mqtt_host", DEFAULT_PUBLIC_HOST);
            c.port = p.getInt("mqtt_port", DEFAULT_PUBLIC_PORT);
            c.useTls = p.getBoolean("mqtt_tls", false);
            c.username = p.get("mqtt_username", "");
            c.password = p.get("mqtt_password", "");
            c.topic = p.get("mqtt_topic", generateDefaultTopic());
        } catch (Throwable ignored) {
        }
        return c;
    }

    public void save() {
        Preferences p = getPrefs();
        if (p == null) {
            return;
        }
        try {
            p.putBoolean("mqtt_enabled", enabled);
            p.put("mqtt_host", host != null ? host.trim() : DEFAULT_PUBLIC_HOST);
            p.putInt("mqtt_port", port > 0 ? port : DEFAULT_PUBLIC_PORT);
            p.putBoolean("mqtt_tls", useTls);
            p.put("mqtt_username", username != null ? username.trim() : "");
            p.put("mqtt_password", password != null ? password.trim() : "");
            p.put("mqtt_topic", topic != null ? topic.trim() : generateDefaultTopic());
        } catch (Throwable ignored) {
        }
    }


    public void applyPublicPreset() {
        this.host = DEFAULT_PUBLIC_HOST;
        this.port = DEFAULT_PUBLIC_PORT;
        this.useTls = false;
        this.username = "";
        this.password = "";
    }

    public void applyHiveMqCloudPreset(String cloudHost) {
        this.host = (cloudHost != null && !cloudHost.isBlank()) ? cloudHost.trim() : "";
        this.port = DEFAULT_TLS_PORT;
        this.useTls = true;
    }

    // Getters and Setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public boolean isUseTls() { return useTls; }
    public void setUseTls(boolean useTls) { this.useTls = useTls; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MqttConfig that = (MqttConfig) o;
        return enabled == that.enabled &&
                port == that.port &&
                useTls == that.useTls &&
                Objects.equals(host, that.host) &&
                Objects.equals(username, that.username) &&
                Objects.equals(password, that.password) &&
                Objects.equals(topic, that.topic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, host, port, useTls, username, password, topic);
    }
}
