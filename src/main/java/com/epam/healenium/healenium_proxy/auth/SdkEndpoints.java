package com.epam.healenium.healenium_proxy.auth;

import org.springframework.http.HttpMethod;

import java.util.List;

/**
 * The only backend and AI paths that a CI/CD client may call through healenium-proxy in Pro mode
 * (spec 004, Requirement 5.2, design decision 8). Taken from healenium-web's RestClient:
 * {@code saveElements}, {@code getElements}, {@code getReferenceElements}, {@code healRequest},
 * {@code initReport}, {@code getTenantSettings} and {@code getXpathSelector}. The selector imitator
 * is called directly by the SDK, not through this proxy, so it is not listed.
 *
 * <p>Matching is exact on the normalized path. A {@code {sessionId}} segment matches one non-empty
 * segment.
 */
public final class SdkEndpoints {

    private static final List<Endpoint> ALLOWED = List.of(
            endpoint(HttpMethod.POST, "/healenium"),
            endpoint(HttpMethod.GET, "/healenium"),
            endpoint(HttpMethod.GET, "/healenium/elements"),
            endpoint(HttpMethod.POST, "/healenium/healing"),
            endpoint(HttpMethod.POST, "/healenium/report/init/{sessionId}"),
            endpoint(HttpMethod.GET, "/healenium/settings"),
            endpoint(HttpMethod.POST, "/healenium-ai/selectors/xpath"));

    private SdkEndpoints() {
    }

    public static boolean isAllowed(HttpMethod method, String path) {
        if (method == null || path == null) {
            return false;
        }
        String[] segments = segments(path);
        for (Endpoint endpoint : ALLOWED) {
            if (endpoint.method().equals(method) && endpoint.matches(segments)) {
                return true;
            }
        }
        return false;
    }

    /** The allow-list itself, for the coverage test. */
    public static List<String> describe() {
        return ALLOWED.stream().map(e -> e.method() + " " + e.pattern()).toList();
    }

    private static Endpoint endpoint(HttpMethod method, String pattern) {
        return new Endpoint(method, pattern, segments(pattern));
    }

    private static String[] segments(String path) {
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        return trimmed.isEmpty() ? new String[0] : trimmed.split("/", -1);
    }

    private record Endpoint(HttpMethod method, String pattern, String[] patternSegments) {

        boolean matches(String[] actual) {
            if (actual.length != patternSegments.length) {
                return false;
            }
            for (int i = 0; i < actual.length; i++) {
                String expected = patternSegments[i];
                boolean variable = expected.startsWith("{") && expected.endsWith("}");
                if (variable ? actual[i].isEmpty() : !expected.equals(actual[i])) {
                    return false;
                }
            }
            return true;
        }
    }
}
