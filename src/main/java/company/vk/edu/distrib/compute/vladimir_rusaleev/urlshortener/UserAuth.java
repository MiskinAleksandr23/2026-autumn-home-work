package company.vk.edu.distrib.compute.vladimir_rusaleev.urlshortener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.NoSuchElementException;
import com.sun.net.httpserver.HttpExchange;
import company.vk.edu.distrib.compute.Dao;

final class UserAuth {
    private static final String POST_METHOD = "POST";

    private final Dao<String> users;

    UserAuth(Dao<String> users) {
        this.users = users;
    }

    void createUser(HttpExchange exchange) throws IOException {
        if (!POST_METHOD.equals(exchange.getRequestMethod())) {
            UrlLinks.respond(exchange, 405, null);
            return;
        }
        String body = UrlLinks.readBody(exchange);
        int sep = body.indexOf(':');
        if (sep <= 0 || sep == body.length() - 1) {
            UrlLinks.respond(exchange, 422, null);
            return;
        }
        users.upsert(body.substring(0, sep), body.substring(sep + 1));
        UrlLinks.respond(exchange, 200, null);
    }

    boolean authenticated(HttpExchange exchange) throws IOException {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return false;
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        int sep = decoded.indexOf(':');
        if (sep <= 0) {
            return false;
        }
        try {
            String expected = users.get(decoded.substring(0, sep));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                decoded.substring(sep + 1).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchElementException exception) {
            return false;
        }
    }
}
