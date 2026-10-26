package de.flyingfinger.dialogui.serve;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/** Tiny embedded HTTP server that serves exactly one file: {@code /<sha1>.zip}. */
public final class PackServer {

    private final HttpServer server;
    private volatile byte[] zip = new byte[0];
    private volatile String sha1 = "";

    public PackServer(String bind, int port, Logger log) throws IOException {
        server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.setExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PaperDialogUI-PackServer");
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/", exchange -> {
            try (exchange) {
                byte[] body = zip;
                if (!exchange.getRequestURI().getPath().equals("/" + sha1 + ".zip")) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
                log.fine("Served pack to " + exchange.getRemoteAddress());
            }
        });
        server.start();
    }

    public void publish(byte[] zip, String sha1) {
        this.zip = zip;
        this.sha1 = sha1;
    }

    public void stop() {
        server.stop(0);
    }
}
