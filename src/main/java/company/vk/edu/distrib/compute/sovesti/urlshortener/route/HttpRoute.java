package company.vk.edu.distrib.compute.sovesti.urlshortener.route;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public interface HttpRoute {

    String prefix();

    HttpHandler handler();

    void parsePath(HttpExchange exchange);

}
