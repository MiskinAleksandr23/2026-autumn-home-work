package company.vk.edu.distrib.compute.sovesti.urlshortener.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

import com.sun.net.httpserver.Request;

import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.sovesti.urlshortener.dao.KeyValuePair;
import company.vk.edu.distrib.compute.sovesti.urlshortener.http.AuthenticationConstants;
import company.vk.edu.distrib.compute.sovesti.urlshortener.http.HeaderConstants;

public final class BasicAuthentication implements AuthenticationScheme {

    private final String realm;
    private final Dao<String> users;

    public BasicAuthentication(String realm, Dao<String> users) {
        this.realm = Objects.requireNonNull(realm);
        this.users = Objects.requireNonNull(users);
    }

    @Override
    public Optional<AuthenticationScheme.Credentials> parse(Request request) {
        return Optional.ofNullable(request.getRequestHeaders().getFirst(HeaderConstants.AUTHORIZATION))
            .map(this::token)
            .flatMap(this::decodeBase64)
            .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
            .map(KeyValuePair::new)
            .filter(KeyValuePair::valid)
            .map(credentials -> () -> credentials.exists(users));
    }

    private Optional<byte[]> decodeBase64(String src) {
        try {
            return Optional.of(Base64.getDecoder().decode(src));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String token(String value) {
        KeyValuePair auth = new KeyValuePair(value, ' ');
        if (!auth.valid() || !AuthenticationConstants.BASIC.equals(auth.key())) {
            throw new AuthenticationException();
        }
        return auth.value();
    }

    @Override
    public String challenge() {
        return "%s %s=\"%s\"".formatted(AuthenticationConstants.BASIC, AuthenticationConstants.REALM, realm);
    }
}
