package company.vk.edu.distrib.compute.sovesti.urlshortener.handler;

import java.util.Objects;
import java.util.Optional;

import com.sun.net.httpserver.HttpExchange;

public final class ExchangeAttributes {

    private final HttpExchange exchange;

    public ExchangeAttributes(HttpExchange exchange) {
        this.exchange = Objects.requireNonNull(exchange);
    }

    public <T> Optional<T> find(ExchangeAttribute<T> attribute) {
        return Optional.ofNullable(exchange.getAttribute(attribute.key()))
            .filter(attribute.type()::isInstance)
            .map(attribute.type()::cast);
    }

    public <T> void put(ExchangeAttribute<T> attribute, T value) {
        exchange.setAttribute(attribute.key(), value);
    }
}
