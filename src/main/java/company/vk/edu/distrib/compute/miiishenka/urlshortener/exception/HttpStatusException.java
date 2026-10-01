package company.vk.edu.distrib.compute.miiishenka.urlshortener.exception;

import java.io.Serial;

public abstract class HttpStatusException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    public abstract int getStatusCode();
}
