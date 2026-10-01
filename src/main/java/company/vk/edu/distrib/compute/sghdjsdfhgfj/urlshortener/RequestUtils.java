package company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public final class RequestUtils {
    private static final int ID_LENGTH = 10;
    private static final Random R = new Random();

    private RequestUtils() {
    }

    public static String responseBody(HttpExchange xch) throws IOException {
        byte[] contents = xch.getRequestBody().readAllBytes();
        return new String(contents, StandardCharsets.UTF_8);
    }

    public static boolean isValidUrl(String url) {
        try {
            new URL(url).toURI();
            return true;
        } catch (MalformedURLException | URISyntaxException e) {
            return false;
        }
    }

    public static boolean isValidId(String id) {
        return id.length() == ID_LENGTH && id.matches("^[a-zA-Z0-9]+$");
    }

    public static String generateId() {
        String characters = "0123456789qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ID_LENGTH; i++) {
            sb.append(characters.charAt(R.nextInt(characters.length())));
        }
        return sb.toString();
    }
}
