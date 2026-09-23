package company.vk.edu.distrib.compute.urlshortener;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import company.vk.edu.distrib.compute.AbstractHttpServiceFactory;

/**
 * Marks an {@link AbstractHttpServiceFactory} implementation to be picked up
 * by the URL shortener integration test suite.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface UrlShortenerTest {
}
