package company.vk.edu.distrib.compute.miiishenka.urlshortener.exception;

public class MethodNotAllowedException extends HttpStatusException {
    @Override
    public int getStatusCode() {
        return 405;
    }
}
