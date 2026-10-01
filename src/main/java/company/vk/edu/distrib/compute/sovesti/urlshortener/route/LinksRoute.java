package company.vk.edu.distrib.compute.sovesti.urlshortener.route;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.sovesti.urlshortener.handler.Body;
import company.vk.edu.distrib.compute.sovesti.urlshortener.handler.HandlersSwitch;
import company.vk.edu.distrib.compute.sovesti.urlshortener.handler.Response;
import company.vk.edu.distrib.compute.sovesti.urlshortener.handler.ResponseBody;
import company.vk.edu.distrib.compute.sovesti.urlshortener.http.HeaderConstants;
import company.vk.edu.distrib.compute.sovesti.urlshortener.http.MethodConstants;
import company.vk.edu.distrib.compute.sovesti.urlshortener.http.StatusCodeConstants;

public final class LinksRoute implements HttpRoute {

    private final Dao<String> links;
    private final RandomId ids = new RandomId();

    public LinksRoute(Dao<String> links) {
        this.links = Objects.requireNonNull(links);
    }

    @Override
    public String prefix() {
        return "/v0/links";
    }

    @Override
    public HttpHandler handler() {
        return new HandlersSwitch() //
            .with(MethodConstants.POST, this::post) //
            .with(MethodConstants.GET, this::get) //
            .with(MethodConstants.PUT, this::put) //
            .with(MethodConstants.DELETE, this::delete);
    }

    private void post(HttpExchange exchange) throws IOException {
        new HtmlUtf8().orThrow(exchange);
        String id = ids.get();
        links.upsert(id, linkFromBody(exchange));
        new HtmlUtf8().respond(exchange);
        new Response(StatusCodeConstants.CREATED, new ResponseBody.Plain(shortenedUrl(exchange, id))).accept(exchange);
    }

    private void get(HttpExchange exchange) throws IOException {
        new HtmlUtf8().respond(exchange);
        new Response(StatusCodeConstants.OK, new ResponseBody.Plain(linkFromDao(exchange))).accept(exchange);
    }

    private void put(HttpExchange exchange) throws IOException {
        new HtmlUtf8().orThrow(exchange);
        linkFromDao(exchange);
        links.upsert(linkId(exchange), linkFromBody(exchange));
        new Response(StatusCodeConstants.OK).accept(exchange);
    }

    private void delete(HttpExchange exchange) throws IOException {
        links.delete(linkId(exchange));
        new Response(StatusCodeConstants.ACCEPTED).accept(exchange);
    }

    private String shortenedUrl(HttpExchange exchange, String id) {
        return "http://%s/%s".formatted(exchange.getRequestHeaders().getFirst(HeaderConstants.HOST), id);
    }

    private String linkFromBody(HttpExchange exchange) throws IOException {
        return URI.create(new Body(exchange).decode()).toURL().toString();
    }

    private String linkFromDao(HttpExchange exchange) throws IOException {
        return links.get(linkId(exchange));
    }

    private String linkId(HttpExchange exchange) {
        return new LinkId().find(exchange);
    }

    @Override
    public void parsePath(HttpExchange exchange) {
        new LinkId().put(prefix(), exchange);
    }

}
