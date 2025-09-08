package com.example.service;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kleiner UDP-Responder, der den aktuellen NodeMode (HOST/SLAVE) zurückmeldet.
 * 
 * Startet einen Thread, der auf Port 53535 lauscht.
 * Auf die Anfrage "WHO_ARE_YOU?" antwortet er mit "MODE:HOST" oder "MODE:SLAVE".
 */
public class DiscoveryResponder implements AutoCloseable {

    public static final int PORT = 53535;
    public static final String QUERY = "WHO_ARE_YOU?";
    private static final int BUF = 256;

    private final GameService gameService;
    private final ExecutorService exec;
    private volatile boolean running = false;
    private DatagramSocket socket;

    public DiscoveryResponder(GameService gameService) {
        this.gameService = gameService;
        this.exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "DiscoveryResponder");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (running) return;
        running = true;
        exec.submit(this::runLoop);
    }

    private void runLoop() {
        try (DatagramSocket s = new DatagramSocket(new InetSocketAddress("0.0.0.0", PORT))) {
            s.setSoTimeout((int) Duration.ofSeconds(1).toMillis());
            this.socket = s;

            byte[] buf = new byte[BUF];
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    s.receive(packet);
                    String incoming = new String(packet.getData(), packet.getOffset(),
                            packet.getLength(), StandardCharsets.UTF_8).trim();
                    if (QUERY.equals(incoming)) {
                        String reply = "MODE:" + gameService.getNodeMode().name();
                        byte[] out = reply.getBytes(StandardCharsets.UTF_8);
                        DatagramPacket resp = new DatagramPacket(out, out.length,
                                packet.getAddress(), packet.getPort());
                        s.send(resp);
                    }
                } catch (SocketTimeoutException ignored) {
                    // Timeout erlaubt uns, regelmäßig zu prüfen ob running == true
                } catch (IOException io) {
                    if (running) {
                        io.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            if (running) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void close() {
        running = false;
        if (socket != null) {
            socket.close();
        }
        exec.shutdownNow();
    }
}
