package company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener.StatusCodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class CustomHttpHandlerTranslator implements HttpHandler {
    private final CustomHttpHandler handler;
    private static final Logger LOG = LoggerFactory.getLogger(CustomHttpHandlerTranslator.class);

    public CustomHttpHandlerTranslator(CustomHttpHandler handler) {
        this.handler = handler;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        try {
            String method = httpExchange.getRequestMethod();
            switch (method) {
                case "GET" -> handler.handleGet(httpExchange);
                case "POST" -> handler.handlePost(httpExchange);
                case "PUT" -> handler.handlePut(httpExchange);
                case "DELETE" -> handler.handleDelete(httpExchange);
                default -> httpExchange.sendResponseHeaders(404, 0);
            }
        } catch (StatusCodeException e) {
            httpExchange.sendResponseHeaders(e.getStatusCode(), 0);
        } catch (Exception e) {
            if (LOG.isErrorEnabled()) {
                LOG.error(e.getMessage(), e);
            }
            httpExchange.sendResponseHeaders(500, 0);
            httpExchange.getResponseBody().write(e.toString().getBytes());
        }
        httpExchange.close();
    }
}
