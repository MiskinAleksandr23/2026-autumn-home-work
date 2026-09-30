package company.vk.edu.distrib.compute.sovesti.urlshortener.route;

import java.util.Locale;
import java.util.Optional;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Request;

import company.vk.edu.distrib.compute.sovesti.urlshortener.http.ContentTypeConstants;
import company.vk.edu.distrib.compute.sovesti.urlshortener.http.HeaderConstants;

final class HtmlUtf8 {

    void orThrow(Request request) {
        Optional.ofNullable(request.getRequestHeaders().getFirst(HeaderConstants.CONTENT_TYPE))
            .map(this::clean)
            .filter(clean(ContentTypeConstants.HTML_UTF8)::equals)
            .orElseThrow(IllegalArgumentException::new);
    }

    private String clean(String value) {
        return value.replace(" ", "").toLowerCase(Locale.getDefault());
    }

    void respond(HttpExchange exchange) {
        exchange.getResponseHeaders().add(HeaderConstants.CONTENT_TYPE, ContentTypeConstants.HTML_UTF8);
    }

}
