package company.vk.edu.distrib.compute.dzolin.urlshortener;

import java.io.Serial;

public class UnauthorizedException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public UnauthorizedException(String message, Exception exception) {
        super(message, exception);
    }

    public UnauthorizedException(String message) {
        super(message);
    }
}
