package company.vk.edu.distrib.compute.ddkudrin.urlshortener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.Objects;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import company.vk.edu.distrib.compute.Dao;

public class DDKudrinAuthMiddleware implements HttpHandler {

    private final HttpHandler next;
    private final Dao<String> dao;

    public DDKudrinAuthMiddleware(HttpHandler next, Dao<String> dao) {
        this.next = next;
        this.dao = dao;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String[] credentials = parseBasicAuth(exchange.getRequestHeaders().getFirst("Authorization"));
        if (credentials.length == 0) {
            unauthorized(exchange);
            return;
        }
        String password;
        try {
            password = dao.get(credentials[0]);
        } catch (NoSuchElementException e) {
            unauthorized(exchange);
            return;
        }
        if (!Objects.equals(credentials[1], password)) {
            unauthorized(exchange);
            return;
        }
        next.handle(exchange);
    }

    private static String[] parseBasicAuth(String header) {
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return new String[0];
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(header.substring(6).trim());
            String s = new String(decoded, StandardCharsets.UTF_8);
            int idx = s.indexOf(':');
            if (idx < 0) {
                return new String[0];
            }
            return new String[]{ s.substring(0, idx), s.substring(idx + 1) };
        } catch (IllegalArgumentException e) {
            return new String[0];
        }
    }

    private static void unauthorized(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"ddkudrin-url-shortener\"");
        exchange.sendResponseHeaders(401, -1);
        exchange.close();
    }
    
}
