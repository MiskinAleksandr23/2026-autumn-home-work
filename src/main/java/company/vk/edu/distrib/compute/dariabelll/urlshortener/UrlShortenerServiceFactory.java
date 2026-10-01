package company.vk.edu.distrib.compute.dariabelll.urlshortener;

import company.vk.edu.distrib.compute.AbstractHttpServiceFactory;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerAuthTest;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerTest;

import java.io.IOException;
import java.nio.file.Path;

@UrlShortenerTest
@UrlShortenerAuthTest
public class UrlShortenerServiceFactory extends AbstractHttpServiceFactory<UrlShortenerServiceImpl> {

    private static final Path DATA_DIRECTORY = Path.of(
            System.getProperty("java.io.tmpdir"),
            "dariabelll-url-shortener"
    );
    private static final Path URL_FILE_PATH = DATA_DIRECTORY.resolve("urls.log");
    private static final Path USER_FILE_PATH = DATA_DIRECTORY.resolve("users.log");

    @Override
    protected UrlShortenerServiceImpl doCreate(int port) throws IOException {
        JournaledDao urlDao = new JournaledDao(URL_FILE_PATH);
        try {
            JournaledDao userDao = new JournaledDao(USER_FILE_PATH);
            try {
                return new UrlShortenerServiceImpl(port, urlDao, userDao);
            } catch (IOException | RuntimeException e) {
                closeOnFailure(userDao, e);
                throw e;
            }
        } catch (IOException | RuntimeException e) {
            closeOnFailure(urlDao, e);
            throw e;
        }
    }

    private static void closeOnFailure(JournaledDao dao, Exception failure) {
        try {
            dao.close();
        } catch (IOException e) {
            failure.addSuppressed(e);
        }
    }
}
