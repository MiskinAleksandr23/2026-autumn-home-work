package company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers;

@FunctionalInterface
public interface Middleware {
    Handler apply(Handler handler);
}
