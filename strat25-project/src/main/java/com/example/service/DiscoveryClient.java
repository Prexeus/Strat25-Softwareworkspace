package com.example.service;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;


public class DiscoveryClient {
    public static String queryMode(InetAddress target) throws IOException {
        try (DatagramSocket s = new DatagramSocket()) {
            s.setSoTimeout(1000);
            byte[] q = DiscoveryResponder.QUERY.getBytes(StandardCharsets.UTF_8);
            DatagramPacket send = new DatagramPacket(q, q.length,
                    new InetSocketAddress(target, DiscoveryResponder.PORT));
            s.send(send);

            byte[] buf = new byte[256];
            DatagramPacket recv = new DatagramPacket(buf, buf.length);
            s.receive(recv);
            return new String(recv.getData(), recv.getOffset(), recv.getLength(),
                    StandardCharsets.UTF_8).trim();
        }
    }
}
