package com.example.net;

import com.example.model.Game;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * Slave -> empfängt kontinuierlich Frames (byte[]) und deserialisiert sie zu Game.
 * Pro Frame: int Length + Payload (Java-Serialization).
 */
public class GameSyncClient implements AutoCloseable {

    private final String host;
    private final int port;
    private final Consumer<Game> onSnapshot;

    private volatile boolean running = false;
    private Thread readerThread;

    public GameSyncClient(String host, int port, Consumer<Game> onSnapshot) {
        this.host = (host == null || host.isBlank()) ? "127.0.0.1" : host.trim();
        this.port = port;
        this.onSnapshot = onSnapshot;
    }

    public void start() {
        if (running) return;
        running = true;
        readerThread = new Thread(this::run, "GameSyncClient");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void run() {
        while (running) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), 2000);
                s.setSoTimeout(0);
                try (DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()))) {
                    while (running) {
                        int len = in.readInt();
                        byte[] buf = new byte[len];
                        in.readFully(buf);

                        try (ObjectInputStream oin = new ObjectInputStream(new ByteArrayInputStream(buf))) {
                            Game snap = (Game) oin.readObject();
                            onSnapshot.accept(snap);
                        } catch (Exception e) {
                            // Snapshot fehlerhaft -> ignorieren
                        }
                    }
                }
            } catch (IOException e) {
                // Reconnect-Backoff
                sleepQuiet(750);
            }
        }
    }

    private void sleepQuiet(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    @Override
    public void close() {
        running = false;
        if (readerThread != null) readerThread.interrupt();
    }
}
