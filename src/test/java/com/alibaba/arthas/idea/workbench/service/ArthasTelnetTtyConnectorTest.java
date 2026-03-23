package com.alibaba.arthas.idea.workbench.service;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/**
 * {@link ArthasTelnetTtyConnector} 的基础读写能力测试。
 */
public class ArthasTelnetTtyConnectorTest {

    @Test
    /**
     * 验证连接器可以读取服务端输出，并把终端原始输入完整写回服务端。
     */
    public void shouldReadServerOutputAndWriteRawTerminalPayload() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            CountDownLatch payloadLatch = new CountDownLatch(1);
            AtomicReference<String> receivedPayload = new AtomicReference<>("");

            Thread serverThread = new Thread(
                    () -> {
                        try (Socket socket = serverSocket.accept()) {
                            socket.setSoTimeout(5000);
                            socket.getOutputStream().write("arthas>\r\n".getBytes(StandardCharsets.UTF_8));
                            socket.getOutputStream().flush();

                            InputStream inputStream = socket.getInputStream();
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                            while (System.nanoTime() < deadline) {
                                int value = inputStream.read();
                                if (value < 0) {
                                    break;
                                }
                                buffer.write(value);
                                String payload = buffer.toString(StandardCharsets.UTF_8);
                                if (payload.contains("dashboard\t")) {
                                    receivedPayload.set(payload);
                                    payloadLatch.countDown();
                                    break;
                                }
                            }
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    },
                    "arthas-tty-test-server");
            serverThread.setDaemon(true);
            serverThread.start();

            try (ArthasTelnetTtyConnector connector =
                    new ArthasTelnetTtyConnector("127.0.0.1", serverSocket.getLocalPort())) {
                connector.connect();
                char[] buffer = new char[64];
                int count = connector.read(buffer, 0, buffer.length);
                assertTrue(count > 0);
                assertTrue(new String(buffer, 0, count).contains("arthas>"));

                connector.write("dashboard\t");
                assertTrue("服务端未收到终端原始输入", payloadLatch.await(5, TimeUnit.SECONDS));
                assertTrue(receivedPayload.get().contains("dashboard\t"));
            }
        }
    }
}
