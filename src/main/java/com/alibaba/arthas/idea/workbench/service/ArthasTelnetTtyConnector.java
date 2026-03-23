package com.alibaba.arthas.idea.workbench.service;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;
import com.jediterm.terminal.Questioner;
import com.jediterm.terminal.TtyConnector;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.net.telnet.InvalidTelnetOptionException;
import org.apache.commons.net.telnet.TelnetClient;
import org.apache.commons.net.telnet.TelnetOptionHandler;
import org.apache.commons.net.telnet.WindowSizeOptionHandler;

/**
 * 将 Arthas Telnet 控制台适配为 JediTerm 可消费的 TTY 连接器。
 */
public final class ArthasTelnetTtyConnector implements TtyConnector, AutoCloseable {

    private static final int CONNECT_TIMEOUT_MILLIS = 5000;
    private static final int DEFAULT_WIDTH = 160;
    private static final int DEFAULT_HEIGHT = 40;

    private final String host;
    private final int port;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final CountDownLatch closedLatch = new CountDownLatch(1);

    private volatile TelnetClient telnetClient;
    private volatile Reader reader;
    private volatile OutputStream outputStream;

    public ArthasTelnetTtyConnector(String host, int port) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
    }

    /**
     * 主动建立 Telnet 连接，并准备读写流。
     */
    public synchronized void connect() throws IOException {
        if (isConnected()) {
            return;
        }
        telnetClient = new TelnetClient();
        telnetClient.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        registerWindowSize(telnetClient);
        telnetClient.connect(host, port);
        reader = new InputStreamReader(telnetClient.getInputStream(), StandardCharsets.UTF_8);
        outputStream = telnetClient.getOutputStream();
        connected.set(true);
    }

    @Override
    @SuppressWarnings("removal")
    public synchronized boolean init(Questioner questioner) {
        if (isConnected()) {
            return true;
        }
        try {
            connect();
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    @Override
    public int read(char[] buffer, int offset, int length) throws IOException {
        Reader currentReader = reader;
        ensureConnected(currentReader);
        int count = currentReader.read(buffer, offset, length);
        if (count < 0) {
            disconnect();
        }
        return count;
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        OutputStream currentOutputStream = outputStream;
        ensureConnected(currentOutputStream);
        currentOutputStream.write(bytes);
        currentOutputStream.flush();
    }

    @Override
    public synchronized void write(String value) throws IOException {
        write(value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public synchronized boolean isConnected() {
        return connected.get() && telnetClient != null && telnetClient.isConnected();
    }

    @Override
    public int waitFor() throws InterruptedException {
        while (!closedLatch.await(200L, TimeUnit.MILLISECONDS)) {
            if (!isConnected()) {
                return 0;
            }
        }
        return 0;
    }

    @Override
    public boolean ready() throws IOException {
        Reader currentReader = reader;
        return isConnected() && currentReader != null && currentReader.ready();
    }

    @Override
    public String getName() {
        return "arthas-telnet-" + port;
    }

    @Override
    public synchronized void close() {
        disconnect();
    }

    private void disconnect() {
        connected.set(false);
        if (telnetClient != null) {
            try {
                telnetClient.disconnect();
            } catch (IOException ignored) {
                // 控制台主动关闭时忽略断开异常。
            } finally {
                telnetClient = null;
                reader = null;
                outputStream = null;
            }
        }
        closedLatch.countDown();
    }

    private void registerWindowSize(TelnetClient client) {
        TelnetOptionHandler sizeOpt =
                new WindowSizeOptionHandler(DEFAULT_WIDTH, DEFAULT_HEIGHT, true, true, false, false);
        try {
            client.addOptionHandler(sizeOpt);
        } catch (InvalidTelnetOptionException | IOException ignored) {
            // 窗口大小协商失败时降级为默认连接。
        }
    }

    private void ensureConnected(Reader currentReader) throws IOException {
        if (!isConnected() || currentReader == null) {
            throw new IOException(message("service.telnet.error.not_connected"));
        }
    }

    private void ensureConnected(OutputStream currentOutputStream) throws IOException {
        if (!isConnected() || currentOutputStream == null) {
            throw new IOException(message("service.telnet.error.not_connected"));
        }
    }

    private static String message(String key, Object... params) {
        return ArthasWorkbenchBundle.message(key, params);
    }
}
