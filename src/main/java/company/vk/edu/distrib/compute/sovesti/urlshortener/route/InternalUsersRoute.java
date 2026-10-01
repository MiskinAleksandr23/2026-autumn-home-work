package company.vk.edu.distrib.compute.sovesti.urlshortener.route;

import java.util.Objects;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.sovesti.urlshortener.dao.KeyValuePair;
import company.vk.edu.distrib.compute.sovesti.urlshortener.handler.HandlersSwitch;
import company.vk.edu.distrib.compute.sovesti.urlshortener.handler.Body;
import company.vk.edu.distrib.compute.sovesti.urlshortener.handler.Response;
import company.vk.edu.distrib.compute.sovesti.urlshortener.http.MethodConstants;
import company.vk.edu.distrib.compute.sovesti.urlshortener.http.StatusCodeConstants;

public final class InternalUsersRoute implements HttpRoute {

    private final Dao<String> users;

    public InternalUsersRoute(Dao<String> users) {
        this.users = Objects.requireNonNull(users);
    }

    @Override
    public String prefix() {
        return "/internal/users";
    }

    @Override
    public HttpHandler handler() {
        return new HandlersSwitch().with(MethodConstants.POST, exchange -> {
            new KeyValuePair(new Body(exchange).decode()).upsert(users);
            new Response(StatusCodeConstants.OK).accept(exchange);
        });
    }

    @Override
    public void parsePath(HttpExchange exchange) {
        // ignore
    }

}
