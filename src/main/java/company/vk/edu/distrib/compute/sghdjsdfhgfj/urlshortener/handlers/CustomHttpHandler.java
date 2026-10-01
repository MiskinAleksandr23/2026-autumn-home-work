package company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener.handlers;

import com.sun.net.httpserver.HttpExchange;
import company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener.StatusCodeException;

import java.io.IOException;

public interface CustomHttpHandler {
    default void handleGet(HttpExchange xch) throws IOException, StatusCodeException {
        throw StatusCodeException.methodNotAllowed();
    }

    default void handlePost(HttpExchange xch) throws IOException, StatusCodeException {
        throw StatusCodeException.methodNotAllowed();
    }

    default void handlePut(HttpExchange xch) throws IOException, StatusCodeException {
        throw StatusCodeException.methodNotAllowed();
    }

    default void handleDelete(HttpExchange xch) throws IOException, StatusCodeException {
        throw StatusCodeException.methodNotAllowed();
    }
}
