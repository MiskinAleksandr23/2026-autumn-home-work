package company.vk.edu.distrib.compute.near.urlshortener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.NoSuchElementException;

import company.vk.edu.distrib.compute.Dao;
import org.jspecify.annotations.Nullable;

final class BasicAuthentication {
    private final Dao<String> users;

    BasicAuthentication(Dao<String> users) {
        this.users = users;
    }

    void register(String body) throws IOException {
        String[] credentials = parseCredentials(body);
        users.upsert(credentials[0], credentials[1]);
    }

    boolean isAuthenticated(@Nullable String header) throws IOException {
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return false;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(header.substring(6).strip());
            String[] credentials = parseCredentials(new String(decoded, StandardCharsets.UTF_8));
            return users.get(credentials[0]).equals(credentials[1]);
        } catch (IllegalArgumentException | NoSuchElementException e) {
            return false;
        }
    }

    private static String[] parseCredentials(String value) {
        if (!value.contains(":") || value.chars().anyMatch(ch -> ch < 32 || ch == 127)) {
            throw new IllegalArgumentException("Expected username:password");
        }
        return value.split(":", 2);
    }
}
