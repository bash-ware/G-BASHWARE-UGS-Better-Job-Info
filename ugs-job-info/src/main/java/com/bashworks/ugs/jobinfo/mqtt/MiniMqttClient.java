package com.bashworks.ugs.jobinfo.mqtt;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Lightweight, zero-dependency pure Java MQTT 3.1.1 client.
 * Supports standard TCP, TLS/SSL (with SNI verification), authentication, QoS 0,
 * retain flags, LWT (Last Will and Testament), and keep-alive ping.
 */
public final class MiniMqttClient implements AutoCloseable {

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private volatile boolean connected = false;

    public synchronized void connect(String host,
                                     int port,
                                     boolean useTls,
                                     String clientId,
                                     String username,
                                     String password,
                                     String willTopic,
                                     byte[] willPayload,
                                     int keepAliveSeconds,
                                     int timeoutMs) throws Exception {
        disconnect();

        if (useTls) {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket ssl = (SSLSocket) factory.createSocket();
            SSLParameters params = ssl.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS"); // Enables SNI and modern hostname verification
            ssl.setSSLParameters(params);
            ssl.connect(new InetSocketAddress(host, port), timeoutMs);
            ssl.startHandshake();
            this.socket = ssl;
        } else {
            Socket plain = new Socket();
            plain.connect(new InetSocketAddress(host, port), timeoutMs);
            this.socket = plain;
        }

        this.socket.setTcpNoDelay(true);
        this.socket.setSoTimeout(timeoutMs);
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();

        // ----------------- CONNECT Packet -----------------
        ByteArrayOutputStream variableAndPayload = new ByteArrayOutputStream();

        // 1. Variable Header
        // Protocol Name: "MQTT"
        writeString(variableAndPayload, "MQTT");
        // Protocol Level: 4 (MQTT 3.1.1)
        variableAndPayload.write(0x04);

        // Connect Flags
        int flags = 0x02; // Clean Session = 1
        boolean hasWill = (willTopic != null && !willTopic.isBlank() && willPayload != null);
        if (hasWill) {
            flags |= 0x04; // Will Flag = 1
            flags |= 0x20; // Will Retain = 1
            // Will QoS = 0 (bits 3-4 are 00)
        }
        boolean hasUser = (username != null && !username.isBlank());
        if (hasUser) {
            flags |= 0x80; // Username Flag = 1
        }
        boolean hasPass = (password != null && !password.isBlank());
        if (hasPass) {
            flags |= 0x40; // Password Flag = 1
        }
        variableAndPayload.write(flags);

        // Keep Alive (2 bytes big endian)
        variableAndPayload.write((keepAliveSeconds >> 8) & 0xFF);
        variableAndPayload.write(keepAliveSeconds & 0xFF);

        // 2. Payload
        writeString(variableAndPayload, clientId != null ? clientId : "UGS-JobInfo-Client");
        if (hasWill) {
            writeString(variableAndPayload, willTopic);
            variableAndPayload.write((willPayload.length >> 8) & 0xFF);
            variableAndPayload.write(willPayload.length & 0xFF);
            variableAndPayload.write(willPayload);
        }
        if (hasUser) {
            writeString(variableAndPayload, username);
        }
        if (hasPass) {
            writeString(variableAndPayload, password);
        }

        // Fixed Header: 0x10 (CONNECT)
        out.write(0x10);
        writeRemainingLength(out, variableAndPayload.size());
        variableAndPayload.writeTo(out);
        out.flush();

        // ----------------- Read CONNACK Packet -----------------
        int header = in.read();
        if (header != 0x20) {
            disconnect();
            throw new IOException("Expected CONNACK (0x20), received: " + header);
        }
        int remLen = readRemainingLength(in);
        if (remLen < 2) {
            disconnect();
            throw new IOException("Malformed CONNACK remaining length: " + remLen);
        }
        int ackFlags = in.read();
        int returnCode = in.read();

        // Drain any extra remaining length if broker sent extra bytes
        for (int i = 2; i < remLen; i++) {
            in.read();
        }

        if (returnCode != 0) {
            disconnect();
            throw new IOException("MQTT connection rejected by broker with code " + returnCode + ": " + getConnAckError(returnCode));
        }

        this.connected = true;
    }

    public synchronized void publish(String topic, byte[] payload, boolean retain) throws IOException {
        if (!connected || socket == null || socket.isClosed()) {
            throw new IOException("MQTT client is not connected");
        }

        byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
        int remLen = 2 + topicBytes.length + (payload != null ? payload.length : 0);

        // Fixed header: 0x30 (PUBLISH, QoS 0) | (retain ? 0x01 : 0x00)
        int fixed = 0x30 | (retain ? 0x01 : 0x00);
        out.write(fixed);
        writeRemainingLength(out, remLen);

        // Variable header: Topic length (2 bytes) + Topic UTF-8 bytes
        out.write((topicBytes.length >> 8) & 0xFF);
        out.write(topicBytes.length & 0xFF);
        out.write(topicBytes);

        // Payload
        if (payload != null && payload.length > 0) {
            out.write(payload);
        }
        out.flush();
    }

    public synchronized void ping() throws IOException {
        if (!connected || socket == null || socket.isClosed()) {
            return;
        }
        // PINGREQ (0xC0, 0x00)
        out.write(0xC0);
        out.write(0x00);
        out.flush();

        // Expect PINGRESP (0xD0, 0x00)
        int resp = in.read();
        if (resp != 0xD0) {
            disconnect();
            throw new IOException("Invalid PINGRESP: " + resp);
        }
        in.read(); // read 0x00
    }

    public synchronized void disconnect() {
        this.connected = false;
        try {
            if (out != null && socket != null && !socket.isClosed()) {
                // DISCONNECT packet: 0xE0, 0x00
                out.write(0xE0);
                out.write(0x00);
                out.flush();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (socket != null) socket.close();
        } catch (Throwable ignored) {
        }
        this.socket = null;
        this.in = null;
        this.out = null;
    }

    @Override
    public void close() {
        disconnect();
    }

    public boolean isConnected() {
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    private static void writeString(ByteArrayOutputStream baos, String s) throws IOException {
        byte[] b = (s != null ? s : "").getBytes(StandardCharsets.UTF_8);
        baos.write((b.length >> 8) & 0xFF);
        baos.write(b.length & 0xFF);
        baos.write(b);
    }

    private static void writeRemainingLength(OutputStream out, int length) throws IOException {
        do {
            int digit = length % 128;
            length /= 128;
            if (length > 0) {
                digit |= 0x80;
            }
            out.write(digit);
        } while (length > 0);
    }

    private static int readRemainingLength(InputStream in) throws IOException {
        int multiplier = 1;
        int value = 0;
        int digit;
        do {
            digit = in.read();
            if (digit == -1) throw new EOFException("Connection closed while reading MQTT length");
            value += (digit & 0x7F) * multiplier;
            multiplier *= 128;
            if (multiplier > 128 * 128 * 128) {
                throw new IOException("Malformed MQTT remaining length");
            }
        } while ((digit & 0x80) != 0);
        return value;
    }

    private static String getConnAckError(int code) {
        switch (code) {
            case 1: return "Unacceptable protocol version";
            case 2: return "Identifier rejected";
            case 3: return "Server unavailable";
            case 4: return "Bad user name or password";
            case 5: return "Not authorized";
            default: return "Unknown return code (" + code + ")";
        }
    }
}
