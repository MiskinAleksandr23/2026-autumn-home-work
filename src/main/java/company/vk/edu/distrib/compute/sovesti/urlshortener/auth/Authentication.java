package company.vk.edu.distrib.compute.sovesti.urlshortener.auth;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import company.vk.edu.distrib.compute.sovesti.urlshortener.auth.AuthenticationScheme.Credentials;

public final class Authentication extends Filter {

    private final AuthenticationScheme scheme;

    public Authentication(AuthenticationScheme credentials) {
        super();
        this.scheme = Objects.requireNonNull(credentials);
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        filter(exchange, chain, scheme.parse(exchange));
    }

    private void filter(HttpExchange exchange, Chain chain, Optional<Credentials> parsed) throws IOException {
        if (parsed.isPresent() && parsed.get().check()) {
            chain.doFilter(exchange);
        } else {
            throw new AuthenticationException();
        }
    }

    @Override
    public String description() {
        return "Authentication";
    }
}
