package company.vk.edu.distrib.compute.kv;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an {@link RemoteDaoFactory} implementation to be picked up
 * by the UrlShortenerService integration test suite.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RemoteDaoFactoryTest {
}
