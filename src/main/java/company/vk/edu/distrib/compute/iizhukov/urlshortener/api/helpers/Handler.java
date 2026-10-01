package company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers;

@FunctionalInterface
public interface Handler {
    Response handle(Request request);
}
