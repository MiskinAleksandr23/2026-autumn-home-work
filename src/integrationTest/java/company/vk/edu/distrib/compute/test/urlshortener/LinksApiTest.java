package company.vk.edu.distrib.compute.test.urlshortener;

import java.net.BindException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import company.vk.edu.distrib.compute.AbstractHttpServiceFactory;
import company.vk.edu.distrib.compute.test.TestUtils;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junitpioneer.jupiter.RetryingTest;

import static company.vk.edu.distrib.compute.test.TestUtils.CONTENT_TYPE_TEXT;
import static company.vk.edu.distrib.compute.test.TestUtils.TEST_LINK_ID;
import static company.vk.edu.distrib.compute.test.TestUtils.TEST_LONG_LINK;
import static company.vk.edu.distrib.compute.test.TestUtils.TEST_LONG_LINK_2;
import static company.vk.edu.distrib.compute.test.TestUtils.TIMEOUT;
import static company.vk.edu.distrib.compute.test.TestUtils.extractId;
import static company.vk.edu.distrib.compute.test.TestUtils.get;
import static company.vk.edu.distrib.compute.test.TestUtils.header;
import static company.vk.edu.distrib.compute.test.TestUtils.isOfString;
import static company.vk.edu.distrib.compute.test.TestUtils.randomPort;
import static company.vk.edu.distrib.compute.test.TestUtils.runHttpCtx;
import static company.vk.edu.distrib.compute.test.TestUtils.tryCreateTestUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * CRUD tests for {@link UrlShortenerService} implementation.
 */
@ParameterizedClass(allowZeroInvocations = true)
@ArgumentsSource(UrlShortenerServiceFactoryArgumentsProvider.class)
@EnabledIfEnvironmentVariable(named = "CURRENT_DATE", matches = "2026-(09-28|09-29|09-30|10-01|10-02|10-03|10-04|10-05|10-06)")
class LinksApiTest {
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    public static final String INVALID_LINK_ID = "invalid-id";
    public static final String INVALID_LONG_LINK = "not-a-valid-link";
    public static final String LINKS_PATH = "/v0/links/";

    @Parameter
    AbstractHttpServiceFactory<? extends UrlShortenerService> serviceFactory;

    @AfterAll
    public static void afterAll() {
        HTTP_CLIENT.close();
    }

    public static HttpResponse<String> getLinks(String id) {
        return getLinks(id, TestUtils.TEST_CREDENTIALS);
    }

    public static HttpResponse<String> getLinks(String id, TestUtils.Credentials credentials) {
        return TestUtils.get(credentials, LINKS_PATH + id, String.class);
    }

    public static HttpResponse<Void> updateLink(String id, String longLink) {
        return updateLink(id, longLink, TestUtils.TEST_CREDENTIALS);
    }

    public static HttpResponse<Void> updateLink(String id, String longLink, TestUtils.Credentials credentials) {
        return TestUtils.update(credentials, LINKS_PATH + id, isOfString(longLink));
    }

    public static HttpResponse<Void> deleteLink(String id) {
        return deleteLink(id, TestUtils.TEST_CREDENTIALS);
    }

    public static HttpResponse<Void> deleteLink(String id, TestUtils.Credentials credentials) {
        return TestUtils.delete(credentials, LINKS_PATH + id);
    }

    public static HttpResponse<String> createLink(String longLink) {
        return createLink(longLink, TestUtils.TEST_CREDENTIALS);
    }

    public static HttpResponse<String> createLink(String longLink, TestUtils.Credentials credentials) {
        return TestUtils.post(credentials, "/v0/links", longLink);
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void getAbsent() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            var service = serviceFactory.create(port);
            try {
                service.start();
                runHttpCtx(HTTP_CLIENT, port, () -> {
                    tryCreateTestUser();
                    assertEquals(404, getLinks(TEST_LINK_ID).statusCode());
                });
            } finally {
                service.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void createAndGet() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            var service = serviceFactory.create(port);
            try {
                service.start();
                runHttpCtx(HTTP_CLIENT, port, () -> {
                    tryCreateTestUser();

                    String longLink = TEST_LONG_LINK;
                    HttpResponse<String> createResponse = createLink(longLink);
                    assertEquals(201, createResponse.statusCode());
                    assertEquals(CONTENT_TYPE_TEXT, header(createResponse, "Content-Type"));

                    String id = extractId(port, createResponse.body());

                    HttpResponse<String> getResponse = getLinks(id);
                    assertEquals(200, getResponse.statusCode());
                    assertEquals(CONTENT_TYPE_TEXT, header(getResponse, "Content-Type"));
                    assertEquals(longLink, getResponse.body());
                });
            } finally {
                service.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void createInvalidLink() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            var service = serviceFactory.create(port);
            try {
                service.start();
                runHttpCtx(HTTP_CLIENT, port, () -> {
                    tryCreateTestUser();
                    assertEquals(422, createLink(INVALID_LONG_LINK).statusCode());
                });
            } finally {
                service.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void getInvalidId() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            var service = serviceFactory.create(port);
            try {
                service.start();
                runHttpCtx(HTTP_CLIENT, port, () -> {
                    tryCreateTestUser();
                    assertEquals(422, getLinks(INVALID_LINK_ID).statusCode());
                });
            } finally {
                service.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void update() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            var service = serviceFactory.create(port);
            try {
                service.start();
                runHttpCtx(HTTP_CLIENT, port, () -> {
                    tryCreateTestUser();

                    String originalLink = TEST_LONG_LINK;
                    String updatedLink = TEST_LONG_LINK_2;
                    String id = extractId(port, createLink(originalLink).body());

                    assertEquals(200, updateLink(id, updatedLink).statusCode());
                    assertEquals(updatedLink, getLinks(id).body());
                });
            } finally {
                service.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void updateInvalidId() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            var service = serviceFactory.create(port);
            try {
                service.start();
                runHttpCtx(HTTP_CLIENT, port, () -> {
                    tryCreateTestUser();
                    assertEquals(422, updateLink(INVALID_LINK_ID, TEST_LONG_LINK).statusCode());
                });
            } finally {
                service.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void updateInvalidLink() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            var service = serviceFactory.create(port);
            try {
                service.start();
                runHttpCtx(HTTP_CLIENT, port, () -> {
                    tryCreateTestUser();
                    String id = extractId(port, createLink(TEST_LONG_LINK).body());
                    assertEquals(422, updateLink(id, INVALID_LONG_LINK).statusCode());
                });
            } finally {
                service.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void updateAbsent() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            var service = serviceFactory.create(port);
            try {
                service.start();
                runHttpCtx(HTTP_CLIENT, port, () -> {
                    tryCreateTestUser();
                    deleteLink(TEST_LINK_ID);
                    assertEquals(404, updateLink(TEST_LINK_ID, TEST_LONG_LINK).statusCode());
                });
            } finally {
                service.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void deleteInvalidId() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            var service = serviceFactory.create(port);
            try {
                service.start();
                runHttpCtx(HTTP_CLIENT, port, () -> {
                    tryCreateTestUser();
                    assertEquals(422, deleteLink(INVALID_LINK_ID).statusCode());
                });
            } finally {
                service.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void delete() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            var service = serviceFactory.create(port);
            try {
                service.start();
                runHttpCtx(HTTP_CLIENT, port, () -> {
                    tryCreateTestUser();

                    String id = extractId(port, createLink(TEST_LONG_LINK).body());

                    assertEquals(202, deleteLink(id).statusCode());
                    assertEquals(404, getLinks(id).statusCode());
                });
            } finally {
                service.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void deleteAbsent() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            var service = serviceFactory.create(port);
            try {
                service.start();
                runHttpCtx(HTTP_CLIENT, port, () -> {
                    tryCreateTestUser();
                    assertEquals(202, deleteLink(TEST_LINK_ID).statusCode());
                });
            } finally {
                service.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void redirect() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            var service = serviceFactory.create(port);
            try {
                service.start();
                runHttpCtx(HTTP_CLIENT, port, () -> {
                    tryCreateTestUser();

                    String id = extractId(port, createLink(TEST_LONG_LINK).body());
                    HttpResponse<String> response = get("/" + id);
                    assertEquals(301, response.statusCode());
                    assertEquals(TEST_LONG_LINK, header(response, "Location"));
                });
            } finally {
                service.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void redirectAbsent() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            var service = serviceFactory.create(port);
            try {
                service.start();

                runHttpCtx(HTTP_CLIENT, port, () -> assertEquals(404, get("/oops123456").statusCode()));
            } finally {
                service.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void redirectInvalidId() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            var service = serviceFactory.create(port);
            try {
                service.start();

                runHttpCtx(HTTP_CLIENT, port, () -> assertEquals(422, get("/" + INVALID_LINK_ID).statusCode()));
            } finally {
                service.stop();
            }
        });
    }
}
