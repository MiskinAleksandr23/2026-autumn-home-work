package company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers;

public enum HttpStatus {
    OK(200),
    CREATED(201),
    ACCEPTED(202),

    MOVED_PERMANENTLY(301),

    UNAUTHORIZED(401),
    NOT_FOUND(404),
    METHOD_NOT_ALLOWED(405),
    UNPROCESSABLE_CONTENT(422),

    INTERNAL_SERVER_ERROR(500);

    private final int code;

    HttpStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
