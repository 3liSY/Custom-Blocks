package com.customblocks;

import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal HTTP server that serves the generated resource pack zip.
 * Listens on 0.0.0.0:8080 at /pack-v10.zip
 */
public class PackHttpServer {

    private static final Logger LOGGER = LoggerFactory.getLogger("CustomBlocks/PackHTTP");
    private static final int    PORT   = 8080;
    private static final String PATH   = "/pack-v10.zip";

    private static HttpServer server = null;
    private static final AtomicReference<byte[]> packBytes = new AtomicReference<>(null);

    public static void start() {
        if (server != null) return;
        try {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
            server.createContext(PATH, exchange -> {
                byte[] data = packBytes.get();
                if (data == null || exchange.getRequestMethod().equals("HEAD")) {
                    if (data == null) {
                        String msg = "Pack not generated yet. Run /cb packurl first.";
                        exchange.sendResponseHeaders(503, msg.length());
                        exchange.getResponseBody().write(msg.getBytes());
                    } else {
                        exchange.getResponseHeaders().add("Content-Type", "application/zip");
                        exchange.getResponseHeaders().add("Content-Length", String.valueOf(data.length));
                        exchange.sendResponseHeaders(200, -1);
                    }
                } else {
                    exchange.getResponseHeaders().add("Content-Type", "application/zip");
                    exchange.getResponseHeaders().add("Content-Disposition",
                            "attachment; filename=\"pack-v10.zip\"");
                    exchange.sendResponseHeaders(200, data.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(data); }
                }
                exchange.close();
            });
            // Health check
            server.createContext("/health", exchange -> {
                String msg = "OK";
                exchange.sendResponseHeaders(200, msg.length());
                exchange.getResponseBody().write(msg.getBytes());
                exchange.close();
            });
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            LOGGER.info("[CustomBlocks] Pack HTTP server started on port {}", PORT);
        } catch (IOException e) {
            LOGGER.error("[CustomBlocks] Failed to start HTTP server: {}", e.getMessage());
        }
    }

    public static void stop() {
        if (server != null) { server.stop(0); server = null; }
    }

    /** Update the in-memory pack bytes (called after every pack regeneration). */
    public static void updatePack(byte[] bytes) {
        packBytes.set(bytes);
        LOGGER.info("[CustomBlocks] Pack updated in HTTP server ({} bytes).", bytes.length);
    }

    public static String getUrl() {
        try {
            String host = InetAddress.getLocalHost().getHostAddress();
            return "http://" + host + ":" + PORT + PATH;
        } catch (UnknownHostException e) {
            return "http://127.0.0.1:" + PORT + PATH;
        }
    }

    public static boolean isRunning() { return server != null; }
}
