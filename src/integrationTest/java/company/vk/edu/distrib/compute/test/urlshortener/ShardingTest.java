package company.vk.edu.distrib.compute.test.urlshortener;

import company.vk.edu.distrib.compute.AbstractHttpServiceFactory;
import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.HttpStatus;
import company.vk.edu.distrib.compute.kv.KVService;
import company.vk.edu.distrib.compute.kv.RemoteDaoFactory;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.RetryingTest;

import java.io.IOException;
import java.net.BindException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static company.vk.edu.distrib.compute.test.TestUtils.*;
import static company.vk.edu.distrib.compute.test.urlshortener.LinksApiTest.createLink;
import static company.vk.edu.distrib.compute.test.urlshortener.LinksApiTest.getLinks;
import static company.vk.edu.distrib.compute.test.urlshortener.RemoteDaoLinksTest.serviceDaoPairs;
import static org.junit.jupiter.api.Assertions.*;

@ParameterizedClass(allowZeroInvocations = true)
@MethodSource("createServiceDaoPairs")
@EnabledIfEnvironmentVariable(named = "CURRENT_DATE", matches = "2026-(10-07|10-08|10-09|10-10|10-11|10-12|10-13|10-14)")
public class ShardingTest {
    private static final int CLUSTER_SIZE = 2;
    public static final String TEST_LINK_ID = "10db3750xY";
    public static final String TEST_LINK_ID_2 = "20db3750xY";
    public static final String TEST_LONG_LINK = "https://ya.ru/search/?text=test";
    public static final String TEST_LONG_LINK_2 = "https://ya.ru/search/?text=test2";
    private static final String ENTITY_PATH = "/v0/entity/";

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Parameter(0)
    AbstractHttpServiceFactory<? extends UrlShortenerService> serviceFactory;

    @Parameter(1)
    AbstractHttpServiceFactory<? extends KVService> kvServiceFactory;

    @Parameter(2)
    RemoteDaoFactory<String> remoteDaoFactory;

    int port;

    int[] remotePorts = new int[CLUSTER_SIZE];

    UrlShortenerService service;

    Dao<String> remoteDao;

    KVService kvService;

    @BeforeEach
    void setup() throws IOException {

        for (int i = 0; i < CLUSTER_SIZE; i++) {
            this.remotePorts[i] = randomPort();
        }

        this.port = randomPort(remotePorts);
        this.service = serviceFactory.create(port);
        this.remoteDao = remoteDaoFactory.create(remotePorts);
        this.kvService = kvServiceFactory.create(randomPort(remotePorts));
        service.setLinksDao(remoteDao);
    }

    @AfterAll
    public static void afterAll() {
        HTTP_CLIENT.close();
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void fullFlowShouldWork() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            try {
                service.start();
                runHttpCtx(HTTP_CLIENT, port, () -> {
                    tryCreateTestUser();

                    String longLink = TEST_LONG_LINK;
                    HttpResponse<String> createResponse = createLink(longLink);
                    assertEquals(201, createResponse.statusCode());
                    assertEquals(CONTENT_TYPE_TEXT, header(createResponse, "Content-Type"));

                    String id = extractId(port, createResponse.body());
                    assertDoesNotThrow(() -> remoteDao.get(id));

                    HttpResponse<String> getResponse = getLinks(id);
                    assertEquals(200, getResponse.statusCode());
                    assertEquals(CONTENT_TYPE_TEXT, header(getResponse, "Content-Type"));
                    assertEquals(longLink, getResponse.body());

                    List<Integer> nodesOwningId = findNodesOwningId(id);
                    assertEquals(1, nodesOwningId.size());
                });
            } finally {
                service.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void shouldFollowSameNodeWithSameId() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            try {
                remoteDao.delete(TEST_LINK_ID);

                assertDoesNotThrow(() -> remoteDao.upsert(TEST_LINK_ID, TEST_LONG_LINK));
                assertEquals(TEST_LONG_LINK, remoteDao.get(TEST_LINK_ID));

                List<Integer> nodesOwningId = findNodesOwningId(TEST_LINK_ID);
                assertEquals(1, nodesOwningId.size());

                assertDoesNotThrow(() -> remoteDao.upsert(TEST_LINK_ID, TEST_LONG_LINK_2));
                assertEquals(TEST_LONG_LINK_2, remoteDao.get(TEST_LINK_ID));

                List<Integer> upsertedNodesOwningId = findNodesOwningId(TEST_LINK_ID);
                assertEquals(1, upsertedNodesOwningId.size());
                assertEquals(nodesOwningId.getFirst(), upsertedNodesOwningId.getFirst());

                assertDoesNotThrow(() -> remoteDao.delete(TEST_LINK_ID));
                assertTrue(findNodesOwningId(TEST_LINK_ID).isEmpty());
            } finally {
                service.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void shouldFollowDifferentNodes() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            try {
                remoteDao.delete(TEST_LINK_ID);
                remoteDao.delete(TEST_LINK_ID_2);

                assertDoesNotThrow(() -> remoteDao.upsert(TEST_LINK_ID, TEST_LONG_LINK));
                assertEquals(TEST_LONG_LINK, remoteDao.get(TEST_LINK_ID));

                assertDoesNotThrow(() -> remoteDao.upsert(TEST_LINK_ID_2, TEST_LONG_LINK_2));
                assertEquals(TEST_LONG_LINK_2, remoteDao.get(TEST_LINK_ID_2));

                List<Integer> nodesOwningId = findNodesOwningId(TEST_LINK_ID);
                assertEquals(1, nodesOwningId.size());

                List<Integer> nodesOwningId2 = findNodesOwningId(TEST_LINK_ID_2);
                assertEquals(1, nodesOwningId2.size());

                assertFalse(nodesOwningId.removeAll(nodesOwningId2));
            } finally {
                service.stop();
            }
        });
    }

    private List<Integer> findNodesOwningId(String id) {
        List<Integer> idNodes = new ArrayList<>();
        for (int remotePort : remotePorts) {
            runHttpCtx(HTTP_CLIENT, remotePort, () -> {
                if (get(ENTITY_PATH + id).statusCode() == HttpStatus.OK.code()) {
                    idNodes.add(remotePort);
                }
            });
        }
        return idNodes;
    }

    static Stream<Arguments> createServiceDaoPairs() {
        return serviceDaoPairs();
    }
}
