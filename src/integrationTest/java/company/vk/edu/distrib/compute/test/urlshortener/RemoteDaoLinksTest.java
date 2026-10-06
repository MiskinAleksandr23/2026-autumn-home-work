package company.vk.edu.distrib.compute.test.urlshortener;

import java.io.IOException;
import java.net.BindException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import company.vk.edu.distrib.compute.AbstractHttpServiceFactory;
import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.kv.KVService;
import company.vk.edu.distrib.compute.kv.KVServiceTest;
import company.vk.edu.distrib.compute.kv.RemoteDaoFactory;
import company.vk.edu.distrib.compute.kv.RemoteDaoFactoryTest;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerService;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.commons.util.ReflectionUtils;
import org.junitpioneer.jupiter.RetryingTest;

import static company.vk.edu.distrib.compute.test.AbstractArgumentsProvider.findAnnotatedFactories;
import static company.vk.edu.distrib.compute.test.TestUtils.CONTENT_TYPE_TEXT;
import static company.vk.edu.distrib.compute.test.TestUtils.TEST_LINK_ID;
import static company.vk.edu.distrib.compute.test.TestUtils.TEST_LONG_LINK;
import static company.vk.edu.distrib.compute.test.TestUtils.TEST_LONG_LINK_2;
import static company.vk.edu.distrib.compute.test.TestUtils.TIMEOUT;
import static company.vk.edu.distrib.compute.test.TestUtils.extractId;
import static company.vk.edu.distrib.compute.test.TestUtils.get;
import static company.vk.edu.distrib.compute.test.TestUtils.header;
import static company.vk.edu.distrib.compute.test.TestUtils.randomPort;
import static company.vk.edu.distrib.compute.test.TestUtils.runHttpCtx;
import static company.vk.edu.distrib.compute.test.TestUtils.tryCreateTestUser;
import static company.vk.edu.distrib.compute.test.urlshortener.LinksApiTest.createLink;
import static company.vk.edu.distrib.compute.test.urlshortener.LinksApiTest.deleteLink;
import static company.vk.edu.distrib.compute.test.urlshortener.LinksApiTest.getLinks;
import static company.vk.edu.distrib.compute.test.urlshortener.LinksApiTest.updateLink;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * CRUD tests for {@link UrlShortenerService} <-> {@link KVService} implementation.
 */
@ParameterizedClass(allowZeroInvocations = true)
@MethodSource("serviceDaoPairs")
@EnabledIfEnvironmentVariable(named = "CURRENT_DATE", matches = "2026-(09-28|09-29|09-30|10-01|10-02|10-03|10-04|10-05|10-06)")
class RemoteDaoLinksTest {
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    public static final int PACKAGE_PREFIX_LEN = "company.vk.edu.distrib.compute.".length();

    @Parameter(0)
    AbstractHttpServiceFactory<? extends UrlShortenerService> urlShortenerServiceFactory;

    @Parameter(1)
    AbstractHttpServiceFactory<? extends KVService> kvServiceFactory;

    @Parameter(2)
    RemoteDaoFactory<String> remoteDaoFactory;

    int urlShortenerServicePort;

    int kvServicePort;

    UrlShortenerService urlShortenerService;

    KVService kvService;

    Dao<String> remoteDao;

    @BeforeEach
    void setup() throws IOException {
        this.urlShortenerServicePort = randomPort();
        this.urlShortenerService = urlShortenerServiceFactory.create(urlShortenerServicePort);
        this.kvServicePort = randomPort(urlShortenerServicePort);
        this.kvService = kvServiceFactory.create(kvServicePort);
        this.remoteDao = remoteDaoFactory.create(kvServicePort);
        urlShortenerService.setLinksDao(remoteDao);
    }

    @AfterAll
    public static void afterAll() {
        HTTP_CLIENT.close();
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void getAbsent() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            try {
                kvService.start();
                remoteDao.delete(TEST_LINK_ID);
                urlShortenerService.start();
                runHttpCtx(HTTP_CLIENT, urlShortenerServicePort, () -> {
                    tryCreateTestUser();
                    assertThrows(NoSuchElementException.class, () -> remoteDao.get(TEST_LINK_ID));
                    assertEquals(404, getLinks(TEST_LINK_ID).statusCode());
                });
            } finally {
                urlShortenerService.stop();
                kvService.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void createAndGet() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            try {
                kvService.start();
                urlShortenerService.start();
                runHttpCtx(HTTP_CLIENT, urlShortenerServicePort, () -> {
                    tryCreateTestUser();

                    String longLink = TEST_LONG_LINK;
                    HttpResponse<String> createResponse = createLink(longLink);
                    assertEquals(201, createResponse.statusCode());
                    assertEquals(CONTENT_TYPE_TEXT, header(createResponse, "Content-Type"));

                    String id = extractId(urlShortenerServicePort, createResponse.body());
                    assertDoesNotThrow(() -> remoteDao.get(id));

                    HttpResponse<String> getResponse = getLinks(id);
                    assertEquals(200, getResponse.statusCode());
                    assertEquals(CONTENT_TYPE_TEXT, header(getResponse, "Content-Type"));
                    assertEquals(longLink, getResponse.body());
                });
            } finally {
                urlShortenerService.stop();
                kvService.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void update() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            try {
                kvService.start();
                urlShortenerService.start();
                runHttpCtx(HTTP_CLIENT, urlShortenerServicePort, () -> {
                    tryCreateTestUser();

                    String originalLink = TEST_LONG_LINK;
                    String updatedLink = TEST_LONG_LINK_2;
                    String id = extractId(urlShortenerServicePort, createLink(originalLink).body());
                    assertDoesNotThrow(() -> remoteDao.get(id));

                    assertEquals(200, updateLink(id, updatedLink).statusCode());
                    assertEquals(updatedLink, getLinks(id).body());
                    assertEquals(updatedLink, remoteDao.get(id));
                });
            } finally {
                urlShortenerService.stop();
                kvService.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void updateAbsent() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            try {
                kvService.start();
                urlShortenerService.start();
                runHttpCtx(HTTP_CLIENT, urlShortenerServicePort, () -> {
                    tryCreateTestUser();
                    deleteLink(TEST_LINK_ID);
                    assertEquals(404, updateLink(TEST_LINK_ID, TEST_LONG_LINK).statusCode());
                });
            } finally {
                urlShortenerService.stop();
                kvService.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void delete() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            try {
                kvService.start();
                urlShortenerService.start();
                runHttpCtx(HTTP_CLIENT, urlShortenerServicePort, () -> {
                    tryCreateTestUser();

                    String id = extractId(urlShortenerServicePort, createLink(TEST_LONG_LINK).body());

                    assertEquals(202, deleteLink(id).statusCode());
                    assertThrows(NoSuchElementException.class, () -> remoteDao.get(id));
                    assertEquals(404, getLinks(id).statusCode());
                });
            } finally {
                urlShortenerService.stop();
                kvService.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void deleteAbsent() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            try {
                kvService.start();
                remoteDao.delete(TEST_LINK_ID);
                urlShortenerService.start();
                runHttpCtx(HTTP_CLIENT, urlShortenerServicePort, () -> {
                    tryCreateTestUser();
                    assertEquals(202, deleteLink(TEST_LINK_ID).statusCode());
                });
            } finally {
                urlShortenerService.stop();
                kvService.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void redirect() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            try {
                kvService.start();
                urlShortenerService.start();
                runHttpCtx(HTTP_CLIENT, urlShortenerServicePort, () -> {
                    tryCreateTestUser();

                    String id = extractId(urlShortenerServicePort, createLink(TEST_LONG_LINK).body());
                    assertDoesNotThrow(() -> remoteDao.get(id));
                    HttpResponse<String> response = get("/" + id);
                    assertEquals(301, response.statusCode());
                    assertEquals(TEST_LONG_LINK, header(response, "Location"));
                });
            } finally {
                urlShortenerService.stop();
                kvService.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void redirectAbsent() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            try {
                kvService.start();
                urlShortenerService.start();
                runHttpCtx(HTTP_CLIENT, urlShortenerServicePort, () -> assertEquals(404, get("/oops123456").statusCode()));
            } finally {
                urlShortenerService.stop();
                kvService.stop();
            }
        });
    }

    static Stream<Arguments> serviceDaoPairs() {
        var urlShortenerServiceFactories = groupByPackageName(findAnnotatedFactories(UrlShortenerTest.class));
        var kvServiceFactories = groupByPackageName(findAnnotatedFactories(KVServiceTest.class));
        var remoteDaoFactories = groupByPackageName(findAnnotatedFactories(RemoteDaoFactoryTest.class));
        return urlShortenerServiceFactories.entrySet().stream()
            .filter(it -> kvServiceFactories.containsKey(it.getKey()) && remoteDaoFactories.containsKey(it.getKey()))
            .map(it -> Arguments.of(
                ReflectionUtils.newInstance(it.getValue()),
                ReflectionUtils.newInstance(kvServiceFactories.get(it.getKey())),
                ReflectionUtils.newInstance(remoteDaoFactories.get(it.getKey()))));
    }

    static Map<String, Class<?>> groupByPackageName(Collection<Class<?>> classes) {
        return classes.stream().collect(Collectors.toMap(
            clazz -> extractUsername(clazz.getPackageName()),
            Function.identity(),
            (x, _) -> x
        ));
    }

    static String extractUsername(String packageName) {
        var withoutPrefix = packageName.substring(PACKAGE_PREFIX_LEN);
        return withoutPrefix.substring(0, withoutPrefix.indexOf("."));
    }
}

