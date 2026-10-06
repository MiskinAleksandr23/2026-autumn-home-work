package company.vk.edu.distrib.compute.ruavee.urlshortener;

import com.sun.net.httpserver.HttpExchange;
import company.vk.edu.distrib.compute.Dao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.ReentrantLock;

public class BasicAuth {
    private static final int CREDENTIAL_PARTS = 2;
    private final Dao<String> usersDao;
    private final ReentrantLock lock = new ReentrantLock();

    public BasicAuth(Dao<String> usersDao) {
        this.usersDao = usersDao;
    }

    private boolean isAuthorized(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null) {
            return false;
        }
        if (authHeader.regionMatches(true, 0, "Basic ", 0, 6)) {
            String data = authHeader.substring(6);
            try {
                data = new String(java.util.Base64.getDecoder().decode(data), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return false;
            }
            if (data.contains(":")) {
                String[] parts = data.split(":", CREDENTIAL_PARTS);
                String login = parts[0];
                String password = parts[1];
                String expectedPassword;
                try {
                    expectedPassword = usersDao.get(login);
                } catch (NoSuchElementException e) {
                    return false;
                }
                return expectedPassword.equals(password);
            }
        }
        return false;
    }

    boolean checkAuthorization(HttpExchange exchange) throws IOException {
        if (isAuthorized(exchange)) {
            return true;
        } else {
            exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"urlshortener\"");
            exchange.sendResponseHeaders(401, -1);
            return false;
        }
    }

    public void handleCreateUser(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String[] parts = body.split(":", CREDENTIAL_PARTS);
        if (parts.length != CREDENTIAL_PARTS) {
            exchange.sendResponseHeaders(422, -1);
            return;
        }
        String login = parts[0];
        String password = parts[1];
        lock.lock();
        try {
            usersDao.upsert(login, password);
        } finally {
            lock.unlock();
        }
        exchange.sendResponseHeaders(200, -1);
    }

}
