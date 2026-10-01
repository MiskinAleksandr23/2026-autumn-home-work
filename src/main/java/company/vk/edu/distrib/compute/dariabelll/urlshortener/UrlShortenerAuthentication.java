package company.vk.edu.distrib.compute.dariabelll.urlshortener;

import com.sun.net.httpserver.HttpExchange;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.NoSuchElementException;

final class UrlShortenerAuthentication {

    private static final String BASIC_SCHEME = "Basic ";

    private final JournaledDao userDao;

    UrlShortenerAuthentication(JournaledDao userDao) {
        this.userDao = userDao;
    }

    boolean isUnauthenticated(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null
                || !authorization.regionMatches(
                        true,
                0, BASIC_SCHEME,
                0, BASIC_SCHEME.length())
        ) {
            return true;
        }

        final String[] registryBody;
        try {
            registryBody = new String(
                    Base64.getDecoder().decode(authorization.substring(BASIC_SCHEME.length()).stripLeading()),
                    StandardCharsets.UTF_8
            ).split(":", 2);
        } catch (IllegalArgumentException e) {
            return true;
        }

        if (RequestValidators.isInvalidRegistryBody(registryBody)) {
            return true;
        }

        String nickname = registryBody[0];
        String password = registryBody[1];
        try {
            return !userDao.get(nickname).equals(password);
        } catch (NoSuchElementException e) {
            return true;
        }
    }
}
