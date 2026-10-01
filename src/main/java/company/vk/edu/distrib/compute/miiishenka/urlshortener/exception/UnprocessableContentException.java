package company.vk.edu.distrib.compute.miiishenka.urlshortener.exception;

public class UnprocessableContentException extends HttpStatusException {
    @Override
    public int getStatusCode() {
        return 422;
    }
}
