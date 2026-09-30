package company.vk.edu.distrib.compute.dariabelll.urlshortener;

import java.net.URI;

final class RequestValidators {

    static final int ID_SIZE = 10;
    private static final int MAX_PORT = 65535;
    private static final int REGISTRY_BODY_PARTS_COUNT = 2;

    private RequestValidators() {
    }

    static boolean isInvalidId(String id) {
        return !id.matches("[a-zA-Z0-9]{" + ID_SIZE + "}");
    }

    static boolean isInvalidRegistryBody(String... registryBody) {
        if (registryBody.length != REGISTRY_BODY_PARTS_COUNT) {
            return true;
        }
        String nickname = registryBody[0];
        String password = registryBody[1];
        return nickname.chars().anyMatch(Character::isISOControl)
                || password.chars().anyMatch(Character::isISOControl);
    }

    static boolean isInvalidLink(String rawUrl) {
        try {
            URI candidate = URI.create(rawUrl);
            String protocol = candidate.getScheme();

            boolean supportedProtocol = "http".equalsIgnoreCase(protocol)
                    || "https".equalsIgnoreCase(protocol);
            boolean hasHost = candidate.getHost() != null;
            boolean validPort = candidate.getPort() <= MAX_PORT;

            return !(supportedProtocol && hasHost && validPort);
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}
