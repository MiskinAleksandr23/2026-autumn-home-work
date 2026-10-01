package company.vk.edu.distrib.compute.sovesti.urlshortener.route;

import java.util.Objects;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.sovesti.urlshortener.handler.HandlersSwitch;
import company.vk.edu.distrib.compute.sovesti.urlshortener.handler.Response;
import company.vk.edu.distrib.compute.sovesti.urlshortener.http.HeaderConstants;
import company.vk.edu.distrib.compute.sovesti.urlshortener.http.MethodConstants;
import company.vk.edu.distrib.compute.sovesti.urlshortener.http.StatusCodeConstants;

public final class RootRoute implements HttpRoute {

    private final Dao<String> links;

    public RootRoute(Dao<String> links) {
        this.links = Objects.requireNonNull(links);
    }

    @Override
    public String prefix() {
        return "/";
    }

    @Override
    public HttpHandler handler() {
        return new HandlersSwitch().with(MethodConstants.GET, exchange -> {
            exchange.getResponseHeaders().add(HeaderConstants.LOCATION, links.get(new LinkId().find(exchange)));
            new Response(StatusCodeConstants.MOVED_PERMANENTLY).accept(exchange);
        });
    }

    @Override
    public void parsePath(HttpExchange exchange) {
        new LinkId().put(prefix(), exchange);
    }
}
