package company.vk.edu.distrib.compute.sovesti.urlshortener.handler;

import java.util.Objects;
import java.util.function.Predicate;

import com.sun.net.httpserver.Request;

public record MethodIs(String method) implements Predicate<Request> {

    public MethodIs {
        Objects.requireNonNull(method);
    }

    @Override
    public boolean test(Request request) {
        return method.equals(request.getRequestMethod());
    }

}
