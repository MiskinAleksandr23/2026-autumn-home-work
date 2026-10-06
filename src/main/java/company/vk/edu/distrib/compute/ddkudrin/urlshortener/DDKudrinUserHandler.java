package company.vk.edu.distrib.compute.ddkudrin.urlshortener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import company.vk.edu.distrib.compute.Dao;

public class DDKudrinUserHandler implements HttpHandler {
    private static final int CREDENTIALS_PARTS_COUNT = 2;

    private final Dao<String> dao;

    public DDKudrinUserHandler(Dao<String> dao) {
        this.dao = dao;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!Objects.equals(exchange.getRequestURI().getPath(), "/internal/users")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            if (!Objects.equals(exchange.getRequestMethod(), "POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String credentials = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String[] parts = credentials.split(":", CREDENTIALS_PARTS_COUNT);
            if (parts.length != CREDENTIALS_PARTS_COUNT) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            String login = parts[0];
            String password = parts[1];
            dao.upsert(login, password);
            exchange.sendResponseHeaders(200, -1);
        }
    }
    
}
