package company.vk.edu.distrib.compute.sovesti.urlshortener.handler;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import company.vk.edu.distrib.compute.sovesti.urlshortener.handler.ExchangeAttribute.BodyAttribute;
import company.vk.edu.distrib.compute.sovesti.urlshortener.handler.ExchangeAttribute.StatusAttribute;

public record Response(int code, ResponseBody body) implements Consumer<HttpExchange>, HttpHandler {

    public Response {
        Objects.requireNonNull(code);
        Objects.requireNonNull(body);
    }

    public Response(int code) {
        this(code, new ResponseBody.Empty());
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        accept(exchange);
    }

    @Override
    public void accept(HttpExchange exchange) {
        put(new ExchangeAttributes(exchange));
    }

    private void put(ExchangeAttributes attributes) {
        attributes.put(new StatusAttribute(), code);
        attributes.put(new BodyAttribute(), body);
    }

}
