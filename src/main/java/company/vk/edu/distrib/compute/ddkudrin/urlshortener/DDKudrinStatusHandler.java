package company.vk.edu.distrib.compute.ddkudrin.urlshortener;

import java.io.IOException;
import java.util.Objects;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class DDKudrinStatusHandler implements HttpHandler {
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!Objects.equals(exchange.getRequestURI().getPath(), "/v0/status")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            if (!Objects.equals(exchange.getRequestMethod(), "GET")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            exchange.sendResponseHeaders(200, -1);
        }
    }
}
