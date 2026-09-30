package company.vk.edu.distrib.compute.miiishenka.urlshortener.exception;

public class NotFoundException extends HttpStatusException {
    @Override
    public int getStatusCode() {
        return 404;
    }
}
