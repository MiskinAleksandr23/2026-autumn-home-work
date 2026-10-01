package company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener;

import java.io.Serial;

public class StatusCodeException extends Exception {
    @Serial
    private static final long serialVersionUID = 6097182046991062671L;
    private final int statusCode;

    public StatusCodeException(int statusCode) {
        super();
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public static StatusCodeException methodNotAllowed() {
        return new StatusCodeException(405);
    }

    public static StatusCodeException unauthorized() {
        return new StatusCodeException(401);
    }

    public static StatusCodeException unprocessable() {
        return new StatusCodeException(422);
    }

    public static StatusCodeException notFound() {
        return new StatusCodeException(404);
    }
}
