package company.vk.edu.distrib.compute.test.urlshortener;

import java.net.BindException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import company.vk.edu.distrib.compute.AbstractHttpServiceFactory;
import company.vk.edu.distrib.compute.test.TestUtils.Credentials;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junitpioneer.jupiter.RetryingTest;

import static company.vk.edu.distrib.compute.test.TestUtils.CONTENT_TYPE_TEXT;
import static company.vk.edu.distrib.compute.test.TestUtils.SPOTTY_TEST_CREDENTIALS;
import static company.vk.edu.distrib.compute.test.TestUtils.TEST_CREDENTIALS;
import static company.vk.edu.distrib.compute.test.TestUtils.TEST_LONG_LINK;
import static company.vk.edu.distrib.compute.test.TestUtils.TEST_LONG_LINK_2;
import static company.vk.edu.distrib.compute.test.TestUtils.TIMEOUT;
import static company.vk.edu.distrib.compute.test.TestUtils.createUser;
import static company.vk.edu.distrib.compute.test.TestUtils.extractId;
import static company.vk.edu.distrib.compute.test.TestUtils.header;
import static company.vk.edu.distrib.compute.test.TestUtils.randomPort;
import static company.vk.edu.distrib.compute.test.TestUtils.runHttpCtx;
import static company.vk.edu.distrib.compute.test.urlshortener.LinksApiTest.createLink;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Authentication tests for {@link UrlShortenerService} implementation.
 *
 */
@ParameterizedClass(allowZeroInvocations = true)
@ArgumentsSource(AuthenticatedUrlShortenerServiceFactoryArgumentsProvider.class)
@EnabledIfEnvironmentVariable(named = "CURRENT_DATE", matches = "2026-(09-28|09-29|09-30|10-01|10-02|10-03|10-04|10-05|10-06)")
class AuthenticationTest {
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Parameter
    AbstractHttpServiceFactory<? extends UrlShortenerService> serviceFactory;

    @AfterAll
    public static void afterAll() {
        HTTP_CLIENT.close();
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void createUserDoesNotRequireAuthentication() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            var service = serviceFactory.create(port);
            try {
                service.start();
                runHttpCtx(HTTP_CLIENT, port, () -> assertEquals(200, createUser(TEST_CREDENTIALS).statusCode()));
            } finally {
                service.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void protectedEndpointsRequireAuthentication() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            var service = serviceFactory.create(port);
            try {
                service.start();

                runHttpCtx(HTTP_CLIENT, port, () -> {
                    assertEquals(200, createUser(TEST_CREDENTIALS).statusCode());

                    HttpResponse<String> createResponse = createLink(TEST_LONG_LINK, SPOTTY_TEST_CREDENTIALS);
                    assertUnauthorized(createResponse);

                    String id = extractId(port, createLink(TEST_LONG_LINK, TEST_CREDENTIALS).body());

                    assertUnauthorized(LinksApiTest.getLinks(id, SPOTTY_TEST_CREDENTIALS));
                    assertUnauthorized(LinksApiTest.updateLink(id, TEST_LONG_LINK, SPOTTY_TEST_CREDENTIALS));
                    assertUnauthorized(LinksApiTest.deleteLink(id, SPOTTY_TEST_CREDENTIALS));
                });
            } finally {
                service.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void invalidCredentialsRejected() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            var service = serviceFactory.create(port);
            try {
                service.start();

                Credentials validCredentials = TEST_CREDENTIALS;
                Credentials invalidCredentials = new Credentials(validCredentials.username(), "oops");
                runHttpCtx(HTTP_CLIENT, port, () -> {
                    assertEquals(200, createUser(validCredentials).statusCode());
                    assertUnauthorized(createLink(TEST_LONG_LINK, invalidCredentials));
                });
            } finally {
                service.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void authenticatedLifecycle() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            var service = serviceFactory.create(port);
            try {
                service.start();

                runHttpCtx(HTTP_CLIENT, port, () -> {
                    assertEquals(200, createUser(TEST_CREDENTIALS).statusCode());

                    String originalLink = TEST_LONG_LINK;
                    String updatedLink = TEST_LONG_LINK_2;

                    HttpResponse<String> createResponse = createLink(originalLink, TEST_CREDENTIALS);
                    assertEquals(201, createResponse.statusCode());
                    assertEquals(CONTENT_TYPE_TEXT, header(createResponse, "Content-Type"));

                    String id = extractId(port, createResponse.body());

                    HttpResponse<String> getResponse = LinksApiTest.getLinks(id, TEST_CREDENTIALS);
                    assertEquals(200, getResponse.statusCode());
                    assertEquals(CONTENT_TYPE_TEXT, header(getResponse, "Content-Type"));
                    assertEquals(originalLink, getResponse.body());

                    assertEquals(200, LinksApiTest.updateLink(id, updatedLink, TEST_CREDENTIALS).statusCode());
                    assertEquals(updatedLink, LinksApiTest.getLinks(id, TEST_CREDENTIALS).body());

                    assertEquals(202, LinksApiTest.deleteLink(id, TEST_CREDENTIALS).statusCode());
                    assertEquals(404, LinksApiTest.getLinks(id, TEST_CREDENTIALS).statusCode());
                });
            } finally {
                service.stop();
            }
        });
    }

    private static void assertUnauthorized(HttpResponse<?> response) {
        assertEquals(401, response.statusCode());
    }
}
