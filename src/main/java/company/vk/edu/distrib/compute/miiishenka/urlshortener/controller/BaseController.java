package company.vk.edu.distrib.compute.miiishenka.urlshortener.controller;

import java.io.IOException;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import company.vk.edu.distrib.compute.miiishenka.urlshortener.exception.HttpStatusException;
import company.vk.edu.distrib.compute.miiishenka.urlshortener.exception.MethodNotAllowedException;
import company.vk.edu.distrib.compute.miiishenka.urlshortener.exception.NotFoundException;
import company.vk.edu.distrib.compute.miiishenka.urlshortener.exception.UnprocessableContentException;

public abstract class BaseController {
    protected static final int SHORT_ID_LENGTH = 10;
    private static final Pattern SHORT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]{%d}$".formatted(SHORT_ID_LENGTH));

    public abstract String getPath();

    public void get(HttpExchange exchange) throws HttpStatusException, IOException {
        throw new MethodNotAllowedException();
    }

    public void post(HttpExchange exchange) throws HttpStatusException, IOException {
        throw new MethodNotAllowedException();
    }

    public void put(HttpExchange exchange) throws HttpStatusException, IOException {
        throw new MethodNotAllowedException();
    }

    public void delete(HttpExchange exchange) throws HttpStatusException, IOException {
        throw new MethodNotAllowedException();
    }

    protected String extractShortId(HttpExchange exchange) throws UnprocessableContentException {
        String uriPrefix = getPath().endsWith("/") ? getPath() : getPath() + "/";
        String uriPath = exchange.getRequestURI().getPath();
        if (!uriPath.startsWith(uriPrefix)) {
            throw new UnprocessableContentException();
        }
        String shortId = uriPath.substring(uriPrefix.length());
        if (!SHORT_ID_PATTERN.matcher(shortId).matches()) {
            throw new UnprocessableContentException();
        }
        return shortId;
    }

    protected void validateExactPath(HttpExchange exchange) throws NotFoundException {
        if (!getPath().equals(exchange.getRequestURI().getPath())) {
            throw new NotFoundException();
        }
    }
}
