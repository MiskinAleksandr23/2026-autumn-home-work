package company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.middlewares;

import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.Handler;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.HttpStatus;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.Middleware;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.Response;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.db.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ErrorHandlingMiddleware implements Middleware {
    private static final Logger log = LoggerFactory.getLogger(ErrorHandlingMiddleware.class);

    @Override
    public Handler apply(Handler handler) {
        return request -> {
            try {
                return handler.handle(request);
            } catch (IllegalArgumentException e) {
                return Response.builder()
                        .setStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                        .build();
            } catch (StorageException e) {
                log.error("Request processing failed", e);
                return Response.builder()
                        .setStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .build();
            } catch (Exception e) {
                log.error("Unhandled exception", e);
                return Response.builder()
                        .setStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .build();
            }
        };
    }
}
