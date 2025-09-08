package com.example.net;

import com.example.service.GameService;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;

public class NetInputServer implements AutoCloseable {

    private final int port;
    private final GameService gameService;

    private final ExecutorService acceptor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "NetInputServer-acceptor");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService workers = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "NetInputServer-worker");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running;
    private ServerSocket server;

    public NetInputServer(int port, GameService gameService) {
        this.port = port;
        this.gameService = gameService;
    }

    public void start() {
        if (running) return;
        running = true;
        acceptor.submit(this::run);
    }

    private void run() {
        try (ServerSocket srv = new ServerSocket(port)) {
            this.server = srv;
            while (running) {
                try {
                    Socket s = srv.accept();
                    workers.submit(() -> handleClient(s));
                } catch (IOException ignored) {
                    if (!running) break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace(); // optional: Logger
        }
    }

    private void handleClient(Socket s) {
        try (s;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()))) {

            String line;
            while ((line = in.readLine()) != null) {
                try {
                    InputMessage msg = InputMessage.decodeLine(line);
                    gameService.applyInputMessage(msg); // delegiert auf Logic-Thread
                    // optional ack:
                    out.write("OK\n");
                    out.flush();
                } catch (Exception ex) {
                    out.write("ERR " + ex.getMessage() + "\n");
                    out.flush();
                }
            }
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() {
        running = false;
        try { if (server != null) server.close(); } catch (IOException ignored) {}
        acceptor.shutdownNow();
        workers.shutdownNow();
    }
}
