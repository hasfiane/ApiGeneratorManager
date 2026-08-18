package com.api.generator.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validates JDBC URLs to prevent SQL injection and malicious connections.
 */
@Component
public class JdbcUrlValidator {

    private static final Logger log = LoggerFactory.getLogger(JdbcUrlValidator.class);

    private static final Set<String> ALLOWED_SCHEMES = Set.of("postgresql", "mysql", "oracle", "h2");

    private static final Pattern HOSTNAME_PATTERN = Pattern.compile(
        "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)*[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?$|"
            + "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"
    );
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$"
    );

    private static final Pattern DB_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-]{1,64}$");

    private static final Set<String> BLOCKED_KEYWORDS = Set.of(
        "javascript:", "file:", "exec", "eval", "system", "cmd"
    );

    private static final Set<String> ALLOWED_H2_SETTINGS = Set.of(
            "mode", "db_close_delay", "db_close_on_exit", "database_to_lower", "case_insensitive_identifiers"
    );

    private final boolean allowPrivateHosts;
    private final Set<String> allowedHosts;

    public JdbcUrlValidator(
            @Value("${app.security.jdbc.allow-private-hosts:true}") boolean allowPrivateHosts,
            @Value("${app.security.jdbc.allowed-hosts:}") String allowedHosts
    ) {
        this.allowPrivateHosts = allowPrivateHosts;
        this.allowedHosts = Arrays.stream(allowedHosts.split(","))
                .map(String::trim)
                .filter(host -> !host.isBlank())
                .map(host -> normalizeHost(host).toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public void validate(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new SecurityException("JDBC URL cannot be empty");
        }

        jdbcUrl = jdbcUrl.trim();
        String lowerUrl = jdbcUrl.toLowerCase(Locale.ROOT);
        for (String keyword : BLOCKED_KEYWORDS) {
            if (lowerUrl.contains(keyword)) {
                throw new SecurityException("JDBC URL contains blocked keyword: " + keyword);
            }
        }

        if (!lowerUrl.startsWith("jdbc:")) {
            throw new SecurityException("JDBC URL must start with 'jdbc:'");
        }

        String withoutJdbc = lowerUrl.substring(5);
        int schemeEnd = withoutJdbc.indexOf(':');
        if (schemeEnd == -1) {
            throw new SecurityException("Invalid JDBC URL format");
        }

        String scheme = withoutJdbc.substring(0, schemeEnd);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw new SecurityException("JDBC scheme not allowed: " + scheme + ". Allowed: " + ALLOWED_SCHEMES);
        }

        switch (scheme) {
            case "postgresql" -> validatePostgresUrl(jdbcUrl);
            case "mysql" -> validateMysqlUrl(jdbcUrl);
            case "h2" -> validateH2Url(jdbcUrl);
            case "oracle" -> validateOracleUrl(jdbcUrl);
            default -> throw new SecurityException("Unsupported JDBC scheme: " + scheme);
        }
    }

    private void validatePostgresUrl(String url) {
        if (!url.matches("^jdbc:postgresql://[^/]+/[^?]+(\\?.*)?$")) {
            throw new SecurityException("Invalid PostgreSQL JDBC URL format");
        }
        validateHostAndDb(url, "postgresql");
    }

    private void validateMysqlUrl(String url) {
        if (!url.matches("^jdbc:mysql://[^/]+/[^?]+(\\?.*)?$")) {
            throw new SecurityException("Invalid MySQL JDBC URL format");
        }
        validateHostAndDb(url, "mysql");
    }

    private void validateH2Url(String url) {
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        if (lowerUrl.contains("file:")) {
            throw new SecurityException("H2 file: URLs are not allowed for security reasons");
        }
        if (!lowerUrl.startsWith("jdbc:h2:mem:") && !lowerUrl.startsWith("jdbc:h2:tcp:")) {
            throw new SecurityException("Only H2 mem: and tcp: URLs are allowed");
        }

        String settingsPart;
        if (lowerUrl.startsWith("jdbc:h2:mem:")) {
            settingsPart = validateH2DatabaseAndSettings(url.substring("jdbc:h2:mem:".length()), true);
        } else if (lowerUrl.startsWith("jdbc:h2:tcp://")) {
            String afterScheme = url.substring("jdbc:h2:tcp://".length());
            int slashPos = afterScheme.indexOf('/');
            if (slashPos == -1) {
                throw new SecurityException("Invalid H2 TCP JDBC URL format");
            }
            String hostPort = afterScheme.substring(0, slashPos);
            validateHostPort(hostPort);
            settingsPart = validateH2DatabaseAndSettings(afterScheme.substring(slashPos + 1), false);
        } else {
            throw new SecurityException("Invalid H2 JDBC URL format");
        }

        validateH2Settings(settingsPart);
    }

    private String validateH2DatabaseAndSettings(String databaseAndSettings, boolean memUrl) {
        if (databaseAndSettings == null || databaseAndSettings.isBlank()) {
            throw new SecurityException("Missing H2 database name");
        }
        String[] parts = databaseAndSettings.split(";", -1);
        String database = parts[0];
        if (memUrl && !database.matches("^[A-Za-z0-9_-]{1,64}$")) {
            throw new SecurityException("Invalid H2 in-memory database name");
        }
        if (!memUrl && !database.matches("^[A-Za-z0-9_:/.-]{1,128}$")) {
            throw new SecurityException("Invalid H2 TCP database name");
        }
        return databaseAndSettings.substring(database.length());
    }

    private void validateH2Settings(String settingsPart) {
        if (settingsPart == null || settingsPart.isBlank()) {
            return;
        }
        String[] settings = settingsPart.split(";", -1);
        for (String rawSetting : settings) {
            if (rawSetting == null || rawSetting.isBlank()) {
                continue;
            }
            String[] keyValue = rawSetting.split("=", 2);
            String key = keyValue[0].trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_H2_SETTINGS.contains(key)) {
                throw new SecurityException("H2 JDBC setting is not allowed: " + keyValue[0].trim());
            }
            if (keyValue.length < 2 || keyValue[1].contains(";") || keyValue[1].length() > 64) {
                throw new SecurityException("Invalid H2 JDBC setting value for: " + keyValue[0].trim());
            }
        }
    }

    private void validateOracleUrl(String url) {
        if (!url.matches("^jdbc:oracle:thin:@(//)?[^/]+[:/][^?]+(\\?.*)?$")) {
            throw new SecurityException("Invalid Oracle JDBC URL format");
        }

        String afterAt = url.substring("jdbc:oracle:thin:@".length());
        String hostPort;
        if (afterAt.startsWith("//")) {
            String authorityAndService = afterAt.substring(2);
            int slashPos = authorityAndService.indexOf('/');
            if (slashPos == -1) {
                throw new SecurityException("Invalid Oracle JDBC URL format");
            }
            hostPort = authorityAndService.substring(0, slashPos);
        } else {
            String[] parts = afterAt.split(":", 3);
            if (parts.length < 2) {
                throw new SecurityException("Invalid Oracle JDBC URL format");
            }
            hostPort = parts[0] + ":" + parts[1];
        }
        validateHostPort(hostPort);
    }

    private void validateHostAndDb(String url, String scheme) {
        try {
            String afterScheme = url.substring(("jdbc:" + scheme + "://").length());
            int slashPos = afterScheme.indexOf('/');
            if (slashPos == -1) {
                throw new SecurityException("Missing database name in JDBC URL");
            }

            String hostPort = afterScheme.substring(0, slashPos);
            String rest = afterScheme.substring(slashPos + 1);

            int questionPos = rest.indexOf('?');
            String dbName = questionPos == -1 ? rest : rest.substring(0, questionPos);

            validateHostPort(hostPort);

            if (!DB_NAME_PATTERN.matcher(dbName).matches()) {
                throw new SecurityException("Invalid database name. Only alphanumeric, underscore, and hyphen allowed.");
            }
        } catch (IndexOutOfBoundsException e) {
            throw new SecurityException("Malformed JDBC URL");
        }
    }

    private void validateHostPort(String hostPort) {
        ParsedHostPort parsed = parseHostPort(hostPort);
        validateHostname(parsed.host());
        if (parsed.port() != null) {
            if (parsed.port().isBlank()) {
                throw new SecurityException("Invalid port number in JDBC URL");
            }
            try {
                int port = Integer.parseInt(parsed.port());
                if (port < 1 || port > 65535) {
                    throw new SecurityException("Invalid port number: " + port);
                }
            } catch (NumberFormatException e) {
                throw new SecurityException("Invalid port number in JDBC URL");
            }
        }
    }

    private ParsedHostPort parseHostPort(String hostPort) {
        if (hostPort == null || hostPort.isBlank()) {
            throw new SecurityException("Missing host in JDBC URL");
        }
        if (hostPort.startsWith("[")) {
            int closingBracket = hostPort.indexOf(']');
            if (closingBracket < 0) {
                throw new SecurityException("Invalid IPv6 host in JDBC URL");
            }
            String host = hostPort.substring(1, closingBracket);
            String rest = hostPort.substring(closingBracket + 1);
            if (!rest.isEmpty() && !rest.startsWith(":")) {
                throw new SecurityException("Invalid IPv6 host in JDBC URL");
            }
            String port = rest.startsWith(":") ? rest.substring(1) : null;
            return new ParsedHostPort(host, port);
        }
        int colonPos = hostPort.indexOf(':');
        if (colonPos == -1) {
            return new ParsedHostPort(hostPort, null);
        }
        return new ParsedHostPort(hostPort.substring(0, colonPos), hostPort.substring(colonPos + 1));
    }

    private void validateHostname(String host) {
        String normalizedHost = normalizeHost(host);
        String lowerHost = normalizedHost.toLowerCase(Locale.ROOT);
        boolean explicitlyAllowed = !allowedHosts.isEmpty() && allowedHosts.contains(lowerHost);

        if (!allowedHosts.isEmpty() && !explicitlyAllowed) {
            throw new SecurityException("JDBC host is not in the allowed hosts list: " + normalizedHost);
        }

        if (looksLikeIpv4Literal(normalizedHost) && !IPV4_PATTERN.matcher(normalizedHost).matches()) {
            throw new SecurityException("Invalid IP address: " + normalizedHost);
        }
        if (normalizedHost.contains(":")) {
            validateIpv6Literal(normalizedHost);
        }

        if (!isIpLiteral(normalizedHost) && !HOSTNAME_PATTERN.matcher(normalizedHost).matches()) {
            throw new SecurityException("Invalid hostname: " + normalizedHost);
        }

        boolean privateOrLocal = isPrivateOrLocalHost(normalizedHost);
        if (privateOrLocal && !allowPrivateHosts && !explicitlyAllowed) {
            throw new SecurityException("Private or local JDBC hosts are not allowed in this environment: " + normalizedHost);
        }

        if (privateOrLocal) {
            log.warn("Connecting to private or local JDBC host: {}", normalizedHost);
        }
    }

    private static String normalizeHost(String host) {
        String normalized = host == null ? "" : host.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean isIpLiteral(String host) {
        return host.contains(":") || host.matches("^\\d{1,3}(\\.\\d{1,3}){3}$");
    }

    private static boolean looksLikeIpv4Literal(String host) {
        return host.matches("^\\d+(\\.\\d+){3}$");
    }

    private static void validateIpv6Literal(String host) {
        try {
            InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new SecurityException("Invalid IP address: " + host);
        }
    }

    private boolean isPrivateOrLocalHost(String host) {
        if (host.equalsIgnoreCase("localhost")) {
            return true;
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isPrivateOrLocalAddress(address)) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            log.warn("JDBC host could not be resolved during validation: {}", host);
            return false;
        }
    }

    private boolean isPrivateOrLocalAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] raw = address.getAddress();
        if (raw.length == 4) {
            int first = Byte.toUnsignedInt(raw[0]);
            int second = Byte.toUnsignedInt(raw[1]);
            return first == 0
                    || first == 10
                    || first == 127
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168)
                    || (first == 169 && second == 254);
        }
        if (raw.length == 16) {
            int first = Byte.toUnsignedInt(raw[0]);
            return (first & 0xfe) == 0xfc;
        }
        return false;
    }

    public String sanitizeForLogging(String jdbcUrl) {
        if (jdbcUrl == null) {
            return "null";
        }

        return jdbcUrl.replaceAll("([?&])password=[^&]*", "$1password=***")
                      .replaceAll("([?&])pass=[^&]*", "$1pass=***")
                      .replaceAll("([?&])pwd=[^&]*", "$1pwd=***");
    }

    private record ParsedHostPort(String host, String port) {}
}
