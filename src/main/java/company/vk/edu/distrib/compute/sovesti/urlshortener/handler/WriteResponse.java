package company.vk.edu.distrib.compute.sovesti.urlshortener.handler;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import company.vk.edu.distrib.compute.sovesti.urlshortener.auth.AuthenticationException;
import company.vk.edu.distrib.compute.sovesti.urlshortener.auth.AuthenticationScheme;
import company.vk.edu.distrib.compute.sovesti.urlshortener.handler.ExchangeAttribute.BodyAttribute;
import company.vk.edu.distrib.compute.sovesti.urlshortener.handler.ExchangeAttribute.StatusAttribute;
import company.vk.edu.distrib.compute.sovesti.urlshortener.http.HeaderConstants;
import company.vk.edu.distrib.compute.sovesti.urlshortener.http.StatusCodeConstants;

public final class WriteResponse extends Filter {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final AuthenticationScheme authentication;

    public WriteResponse(AuthenticationScheme authentication) {
        super();
        this.authentication = Objects.requireNonNull(authentication);
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        tryFilter(exchange, chain);
        write(exchange, new ExchangeAttributes(exchange));
    }

    private void tryFilter(HttpExchange exchange, Chain chain) {
        try {
            chain.doFilter(exchange);
        } catch (NoSuchElementException e) {
            logExceptionDebug(e);
            new Response(StatusCodeConstants.NOT_FOUND).accept(exchange);
        } catch (IllegalArgumentException | MalformedURLException e) {
            logExceptionDebug(e);
            new Response(StatusCodeConstants.UNPROCESSABLE_CONTENT).accept(exchange);
        } catch (AuthenticationException e) {
            exchange.getResponseHeaders().add(HeaderConstants.AUTHENTICATE, authentication.challenge());
            new Response(StatusCodeConstants.UNATHORIZED).accept(exchange);
        } catch (Exception e) {
            logExceptionError(e);
            new Response(StatusCodeConstants.INTERNAL_ERROR).accept(exchange);
        }
    }

    private void logExceptionDebug(Exception ex) {
        if (logger.isDebugEnabled()) {
            logger.debug(ex.getMessage(), ex);
        }
    }

    private void logExceptionError(Exception ex) {
        if (logger.isErrorEnabled()) {
            logger.error(ex.getMessage(), ex);
        }
    }

    private void write(HttpExchange exchange, ExchangeAttributes atrributes) throws IOException {
        write(exchange, //
            atrributes.find(new StatusAttribute()).orElse(StatusCodeConstants.NOT_FOUND), //
            atrributes.find(new BodyAttribute()).orElseGet(ResponseBody.Empty::new));
    }

    private void write(HttpExchange exchange, int status, ResponseBody body) throws IOException {
        exchange.sendResponseHeaders(status, body.length());
        body.write(exchange.getResponseBody());
        exchange.close();
    }

    @Override
    public String description() {
        return "Write response";
    }

}
