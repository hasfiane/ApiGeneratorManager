package com.api.generator.account.api;

import com.api.generator.account.AppUser;
import com.api.generator.account.GeneratedApi;
import com.api.generator.account.repo.AppUserRepository;
import com.api.generator.api.service.GenerationService;
import com.api.generator.api.service.PreviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StreamUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/account")
public class PreviewProxyController {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade",
            "host",
            "content-length"
    );
    private static final Set<String> BLOCKED_REQUEST_HEADERS = Set.of(
            "authorization",
            "cookie",
            "forwarded",
            "proxy-authorization",
            "x-csrf-token",
            "x-forwarded-for",
            "x-forwarded-host",
            "x-forwarded-port",
            "x-forwarded-proto",
            "x-forwarded-prefix",
            "x-real-ip",
            "x-xsrf-token"
    );
    private static final Set<String> BLOCKED_RESPONSE_HEADERS = Set.of(
            "clear-site-data",
            "content-security-policy",
            "cross-origin-embedder-policy",
            "cross-origin-opener-policy",
            "cross-origin-resource-policy",
            "permissions-policy",
            "set-cookie",
            "strict-transport-security",
            "www-authenticate",
            "x-content-type-options",
            "x-frame-options"
    );

    private final AppUserRepository users;
    private final GenerationService generations;
    private final PreviewService previews;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public PreviewProxyController(AppUserRepository users,
                                  GenerationService generations,
                                  PreviewService previews) {
        this.users = users;
        this.generations = generations;
        this.previews = previews;
    }

    @RequestMapping(value = {"/apis/{id}/preview/proxy", "/apis/{id}/preview/proxy/**"})
    public ResponseEntity<byte[]> proxy(@PathVariable UUID id, Authentication auth, HttpServletRequest request) {
        AppUser user = requireUser(auth);
        GeneratedApi generatedApi = generations.requireOwned(id, user);
        var preview = previews.getByGeneratedApi(generatedApi);
        if (preview.getBaseUrl() == null || preview.getBaseUrl().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Preview is not running");
        }
        return proxyToBaseUrl(preview.getBaseUrl(), "/api/account/apis/" + id + "/preview/proxy", request);
    }

    @RequestMapping(value = {"/apis/{id}/proxy", "/apis/{id}/proxy/**"})
    public ResponseEntity<byte[]> generatedApiProxy(@PathVariable UUID id, Authentication auth, HttpServletRequest request) {
        AppUser user = requireUser(auth);
        GeneratedApi generatedApi = generations.requireOwned(id, user);
        if (generatedApi.getApiBaseUrl() == null || generatedApi.getApiBaseUrl().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Generated API is not running");
        }
        if (!URI.create(generatedApi.getApiBaseUrl()).isAbsolute()) {
            throw new ResponseStatusException(BAD_REQUEST, "Generated API is exposed through the public runtime route");
        }
        return proxyToBaseUrl(generatedApi.getApiBaseUrl(), "/api/account/apis/" + id + "/proxy", request);
    }

    private ResponseEntity<byte[]> proxyToBaseUrl(String baseUrl, String basePath, HttpServletRequest request) {
        String target = buildTargetUrl(request, basePath, baseUrl);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(target))
                    .timeout(Duration.ofSeconds(60));
            copyRequestHeaders(request, builder);
            addTrustedForwardedHeaders(request, builder, basePath);
            builder.method(request.getMethod(), buildBodyPublisher(request));
            HttpResponse<byte[]> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            ResponseEntity.BodyBuilder entity = ResponseEntity.status(response.statusCode());
            copyResponseHeaders(response, entity, baseUrl, basePath);
            return entity.body(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ResponseStatusException(BAD_GATEWAY, "Preview proxy request failed", e);
        }
    }

    private String buildTargetUrl(HttpServletRequest request, String basePath, String baseUrl) {
        String requestUri = request.getRequestURI();
        String remainder = requestUri.length() > basePath.length() ? requestUri.substring(basePath.length()) : "";
        StringBuilder target = new StringBuilder(baseUrl);
        if (!remainder.isBlank()) {
            if (!remainder.startsWith("/")) {
                target.append('/');
            }
            target.append(remainder);
        }
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            target.append('?').append(request.getQueryString());
        }
        return target.toString();
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpRequest.Builder builder) {
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String normalized = headerName.toLowerCase();
            if (HOP_BY_HOP_HEADERS.contains(normalized) || BLOCKED_REQUEST_HEADERS.contains(normalized)) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(headerName);
            while (values.hasMoreElements()) {
                builder.header(headerName, values.nextElement());
            }
        }
    }

    private void addTrustedForwardedHeaders(HttpServletRequest request, HttpRequest.Builder builder, String basePath) {
        builder.header("X-Forwarded-Host", request.getServerName());
        builder.header("X-Forwarded-Proto", request.getScheme());
        builder.header("X-Forwarded-Prefix", basePath);
        int port = request.getServerPort();
        if (port > 0) {
            builder.header("X-Forwarded-Port", Integer.toString(port));
        }
    }

    private HttpRequest.BodyPublisher buildBodyPublisher(HttpServletRequest request) throws IOException {
        if ("GET".equalsIgnoreCase(request.getMethod()) || "HEAD".equalsIgnoreCase(request.getMethod())) {
            return HttpRequest.BodyPublishers.noBody();
        }
        try (InputStream inputStream = request.getInputStream()) {
            byte[] body = StreamUtils.copyToByteArray(inputStream);
            if (body.length == 0) {
                return HttpRequest.BodyPublishers.noBody();
            }
            return HttpRequest.BodyPublishers.ofByteArray(body);
        }
    }

    private void copyResponseHeaders(HttpResponse<byte[]> response,
                                     ResponseEntity.BodyBuilder entity,
                                     String targetBaseUrl,
                                     String proxyBasePath) {
        response.headers().map().forEach((name, values) -> {
            String normalized = name.toLowerCase();
            if (HOP_BY_HOP_HEADERS.contains(normalized) || BLOCKED_RESPONSE_HEADERS.contains(normalized)) {
                return;
            }
            for (String value : values) {
                entity.header(name, rewriteResponseHeader(name, value, targetBaseUrl, proxyBasePath));
            }
        });
        if (!response.headers().firstValue(HttpHeaders.CONTENT_TYPE).isPresent()) {
            entity.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
        }
    }

    private String rewriteResponseHeader(String name, String value, String targetBaseUrl, String proxyBasePath) {
        if (!HttpHeaders.LOCATION.equalsIgnoreCase(name) || value == null || value.isBlank()) {
            return value;
        }

        URI targetBaseUri = URI.create(targetBaseUrl);
        URI locationUri = URI.create(value);
        if (!locationUri.isAbsolute()) {
            String path = value.startsWith("/") ? value : "/" + value;
            return proxyBasePath + path;
        }

        if (sameOrigin(targetBaseUri, locationUri)) {
            String path = locationUri.getRawPath();
            StringBuilder rewritten = new StringBuilder(proxyBasePath);
            if (path != null && !path.isBlank() && !"/".equals(path)) {
                if (!path.startsWith("/")) {
                    rewritten.append('/');
                }
                rewritten.append(path);
            }
            if (locationUri.getRawQuery() != null && !locationUri.getRawQuery().isBlank()) {
                rewritten.append('?').append(locationUri.getRawQuery());
            }
            if (locationUri.getRawFragment() != null && !locationUri.getRawFragment().isBlank()) {
                rewritten.append('#').append(locationUri.getRawFragment());
            }
            return rewritten.toString();
        }

        return value;
    }

    private boolean sameOrigin(URI left, URI right) {
        int leftPort = left.getPort() == -1 ? defaultPort(left.getScheme()) : left.getPort();
        int rightPort = right.getPort() == -1 ? defaultPort(right.getScheme()) : right.getPort();
        return leftPort == rightPort
                && safeEquals(left.getScheme(), right.getScheme())
                && safeEquals(left.getHost(), right.getHost());
    }

    private boolean safeEquals(String left, String right) {
        return left != null && left.equalsIgnoreCase(right);
    }

    private int defaultPort(String scheme) {
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        return 80;
    }

    private AppUser requireUser(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Not authenticated");
        }
        return users.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("User not found"));
    }
}
