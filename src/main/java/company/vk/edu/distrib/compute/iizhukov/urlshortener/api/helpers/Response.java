package company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public record Response(int status, String content, Map<String, String> headers) {
    public Response {
        headers = Map.copyOf(headers);
    }

    public int length() {
        return content.getBytes(StandardCharsets.UTF_8).length;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private HttpStatus status = HttpStatus.METHOD_NOT_ALLOWED;
        private String content = "";
        private final Map<String, String> headers = new ConcurrentHashMap<>(Map.of(
                "Content-Type", "text/html; charset=utf-8"
        ));

        private Builder() {

        }

        public Builder setStatus(HttpStatus status) {
            this.status = status;
            return this;
        }

        public Builder setContent(String content) {
            this.content = content;
            return this;
        }

        public Builder addHeader(String key, String value) {
            this.headers.put(key, value);
            return this;
        }

        public Response build() {
            return new Response(status.code(), content, headers);
        }
    }
}
