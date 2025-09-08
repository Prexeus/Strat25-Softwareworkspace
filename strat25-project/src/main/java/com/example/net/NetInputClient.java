package com.example.net;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

public class NetInputClient implements AutoCloseable {

    private final String host;
    private final int port;

    public NetInputClient(String host, int port) {
        this.host = (host == null || host.isBlank()) ? "127.0.0.1" : host.trim();
        this.port = port;
    }

    /** Sendet die Nachricht und wartet kurz auf ACK (optional). */
    public void send(InputMessage msg) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), (int) Duration.ofSeconds(2).toMillis());
            s.setSoTimeout((int) Duration.ofSeconds(2).toMillis());
            try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
                 BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                out.write(msg.encodeLine());
                out.flush();
                // kleines ACK lesen (optional)
                in.readLine();
            }
        }
    }

    @Override
    public void close() {
        // no persistent connection here (simple fire-and-forget per call)
    }
}
