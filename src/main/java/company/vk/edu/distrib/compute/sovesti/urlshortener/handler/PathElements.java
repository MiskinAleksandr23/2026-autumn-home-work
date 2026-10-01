package company.vk.edu.distrib.compute.sovesti.urlshortener.handler;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.sun.net.httpserver.HttpExchange;

public final class PathElements implements Function<HttpExchange, Stream<String>> {

    private final String prefix;

    public PathElements(String prefix) {
        this.prefix = Objects.requireNonNull(prefix);
    }

    @Override
    public Stream<String> apply(HttpExchange exchange) {
        return Stream.of(exchange.getRequestURI().getPath().substring(prefix.length()).split("/"))
            .filter(Predicate.not(String::isEmpty));
    }

}
