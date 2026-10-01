package company.vk.edu.distrib.compute.sovesti.urlshortener.route;

import com.sun.net.httpserver.HttpExchange;

import company.vk.edu.distrib.compute.sovesti.urlshortener.handler.ExchangeAttribute;
import company.vk.edu.distrib.compute.sovesti.urlshortener.handler.ExchangeAttributes;
import company.vk.edu.distrib.compute.sovesti.urlshortener.handler.PathElements;

final class LinkId implements ExchangeAttribute<String> {

    @Override
    public String key() {
        return "link_id";
    }

    @Override
    public Class<String> type() {
        return String.class;
    }

    String find(HttpExchange exchange) {
        return new RandomId().throwIfInvalid(new ExchangeAttributes(exchange).find(this).get());
    }

    void put(String prefix, HttpExchange exchange) {
        new PathElements(prefix).apply(exchange) //
            .findFirst() //
            .ifPresent(id -> new ExchangeAttributes(exchange).put(this, id));
    }
}
