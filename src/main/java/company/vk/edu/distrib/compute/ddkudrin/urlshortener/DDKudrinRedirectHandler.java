package company.vk.edu.distrib.compute.ddkudrin.urlshortener;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import company.vk.edu.distrib.compute.Dao;

public class DDKudrinRedirectHandler implements HttpHandler {
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9]{10}");

    private final Dao<String> dao;

    public DDKudrinRedirectHandler(Dao<String> dao) {
        this.dao = dao;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!Objects.equals(exchange.getRequestMethod(), "GET")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String id = exchange.getRequestURI().getPath().substring(1);
            if (!ID_PATTERN.matcher(id).matches()) {
                exchange.sendResponseHeaders(422, -1);
                return;
            }

            String url;
            try {
                url = dao.get(id);
            } catch (NoSuchElementException e) {
                exchange.sendResponseHeaders(404, -1);
                return;
            } catch (IOException e) {
                exchange.sendResponseHeaders(500, -1);
                return;
            }

            exchange.getResponseHeaders().set("Location", url);
            exchange.sendResponseHeaders(301, -1);
        }
    }
}
