package company.vk.edu.distrib.compute.urlshortener;

import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.HttpService;

public interface UrlShortenerService extends HttpService {

    /**
     * Injects Dao that manages links storage. Must be called before this service start method is called.
     * Should throw IllegalStateException if called after this service start or stop methods.
     *
     * @param dao - Dao instance. Dao must be ready for use when this method returns.
     */
    default void setLinksDao(Dao<String> dao) {
        throw new UnsupportedOperationException("implement me");
    }
}
