package com.example.net;

import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Host -> broadcastet binäre Snapshots (byte[]) an alle verbundenen Slaves.
 * Pro Frame: zuerst int Length, dann Payload.
 */
public class GameSyncServer implements AutoCloseable {

    private final int port;
    private volatile boolean running = false;

    private ServerSocket server;
    private final ExecutorService acceptor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "GameSyncServer-acceptor");
        t.setDaemon(true);
        return t;
    });

    private final List<Client> clients = new CopyOnWriteArrayList<>();

    public GameSyncServer(int port) {
        this.port = port;
    }

    public void start() {
        if (running) return;
        running = true;
        acceptor.submit(this::runAcceptLoop);
    }

    private void runAcceptLoop() {
        try (ServerSocket srv = new ServerSocket(port)) {
            this.server = srv;
            while (running) {
                try {
                    Socket s = srv.accept();
                    s.setTcpNoDelay(true);
                    Client c = new Client(s);
                    clients.add(c);
                } catch (IOException ignored) {
                    if (!running) break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeClients();
        }
    }

    /** Schickt einen Frame an alle Clients, entfernt tote Verbindungen. */
    public void broadcast(byte[] payload) {
        Iterator<Client> it = clients.iterator();
        while (it.hasNext()) {
            Client c = it.next();
            try {
                c.out.writeInt(payload.length);
                c.out.write(payload);
                c.out.flush();
            } catch (IOException e) {
                c.close();
                it.remove();
            }
        }
    }

    private void closeClients() {
        for (Client c : clients) c.close();
        clients.clear();
    }

    @Override
    public void close() {
        running = false;
        try { if (server != null) server.close(); } catch (IOException ignored) {}
        acceptor.shutdownNow();
        closeClients();
    }

    private static final class Client implements Closeable {
        final Socket socket;
        final DataOutputStream out;

        Client(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new DataOutputStream(socket.getOutputStream());
        }

        @Override public void close() {
            try { out.close(); } catch (IOException ignored) {}
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
