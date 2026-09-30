package company.vk.edu.distrib.compute.miiishenka.urlshortener.authorization;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.sun.net.httpserver.HttpExchange;
import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.miiishenka.urlshortener.exception.UnauthorizedException;

public class BasicAuthenticator {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BASIC_PREFIX = "Basic ";
    private static final int CREDENTIAL_PARTS_COUNT = 2;

    private final Dao<String> usersDao;

    public BasicAuthenticator(Dao<String> usersDao) {
        this.usersDao = usersDao;
    }

    public void authenticate(HttpExchange exchange) throws UnauthorizedException, IOException {
        String authorization = exchange.getRequestHeaders().getFirst(AUTHORIZATION_HEADER);
        if (authorization == null || !authorization.startsWith(BASIC_PREFIX)) {
            throw new UnauthorizedException();
        }
        String token = authorization.substring(BASIC_PREFIX.length());
        String credentials = decodeCredentials(token);
        if (credentials == null) {
            throw new UnauthorizedException();
        }
        String[] parts = credentials.split(":", CREDENTIAL_PARTS_COUNT);
        if (parts.length != CREDENTIAL_PARTS_COUNT) {
            throw new UnauthorizedException();
        }
        String user = parts[0];
        String password = parts[1];
        String actualPassword = usersDao.get(user);
        if (actualPassword == null || !actualPassword.equals(password)) {
            throw new UnauthorizedException();
        }
    }

    private String decodeCredentials(String basic) {
        try {
            return new String(Base64.getDecoder().decode(basic), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
