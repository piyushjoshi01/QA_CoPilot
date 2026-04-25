package com.qacopilot.generation.state;

import com.qacopilot.analysis.ir.EndpointContract;
import com.qacopilot.analysis.ir.FieldContract;
import com.qacopilot.analysis.ir.ParamBinding;
import com.qacopilot.analysis.ir.RequestBodyContract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

/**
 * Stable identity for an extracted endpoint so we can diff Spring code changes and reuse cached scenarios.
 */
public final class EndpointFingerprints {

    private EndpointFingerprints() {
    }

    /**
     * Change detector: includes HTTP surface, parameters, body type, media types, and auth hints so
     * scenario cache invalidates when the contract changes (not only when the path string changes).
     */
    public static String signature(EndpointContract e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getHttpMethod())
                .append('|')
                .append(e.getPath().trim())
                .append('|')
                .append(e.getControllerClassName())
                .append('|')
                .append(e.getHandlerMethodName())
                .append('|');
        e.getPathParams().stream()
                .sorted(Comparator.comparing(ParamBinding::getName))
                .forEach(
                        p -> sb.append("pv:")
                                .append(p.getName())
                                .append(':')
                                .append(p.getJavaType())
                                .append(':')
                                .append(p.isRequired())
                                .append(';')
                );
        sb.append('|');
        e.getQueryParams().stream()
                .sorted(Comparator.comparing(ParamBinding::getName))
                .forEach(
                        p -> sb.append("qp:")
                                .append(p.getName())
                                .append(':')
                                .append(p.getJavaType())
                                .append(':')
                                .append(p.isRequired())
                                .append(':')
                                .append(escapeMeta(p.getDefaultValue()))
                                .append(';')
                );
        sb.append("|body:");
        RequestBodyContract rb = e.getRequestBody();
        sb.append(rb.isPresent())
                .append(':')
                .append(rb.isRequired())
                .append(':')
                .append(rb.getDtoJavaType() == null ? "" : rb.getDtoJavaType());
        rb.getFields().stream()
                .sorted(Comparator.comparing(FieldContract::getName))
                .forEach(
                        f -> {
                            sb.append("|f:")
                                    .append(f.getName())
                                    .append(':')
                                    .append(f.getJavaType())
                                    .append(':')
                                    .append(f.isRequired());
                            for (String v : f.getValidations()) {
                                sb.append(':').append(escapeMeta(v));
                            }
                            sb.append(';');
                        }
                );
        sb.append("|c:").append(String.join(",", e.getConsumes()));
        sb.append("|p:").append(String.join(",", e.getProduces()));
        sb.append("|a:")
                .append(e.getAuth().getType())
                .append(':')
                .append(e.getAuth().isRequired());
        if (!e.getWarnings().isEmpty()) {
            sb.append("|w:");
            for (String w : e.getWarnings()) {
                sb.append(escapeMeta(w)).append(';');
            }
        }
        if (e.getOpenApiSummary() != null && !e.getOpenApiSummary().isBlank()) {
            sb.append("|oa:").append(escapeMeta(e.getOpenApiSummary()));
        }
        if (!e.getOpenApiTags().isEmpty()) {
            sb.append("|oat:").append(String.join(",", e.getOpenApiTags().stream().sorted().toList()));
        }
        if (!e.getOpenApiResponseCodes().isEmpty()) {
            sb.append("|oac:");
            e.getOpenApiResponseCodes().stream().sorted().distinct().forEach(c -> sb.append(c).append(','));
        }
        if (e.getOpenApiSpecOperationId() != null && !e.getOpenApiSpecOperationId().isBlank()) {
            sb.append("|oaop:").append(escapeMeta(e.getOpenApiSpecOperationId()));
        }
        if (e.getOpenApiSpecDescription() != null && !e.getOpenApiSpecDescription().isBlank()) {
            sb.append("|oad:").append(escapeMeta(e.getOpenApiSpecDescription()));
        }
        if (!e.getOpenApiSpecSources().isEmpty()) {
            sb.append("|oasrc:")
                    .append(String.join(",", e.getOpenApiSpecSources().stream().sorted().toList()));
        }
        return sb.toString();
    }

    private static String escapeMeta(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("|", "\\|").replace(";", "\\;");
    }

    /** Stable short id for tags and cache keys. */
    public static String stableId(EndpointContract e) {
        return sha256Hex(signature(e)).substring(0, 16);
    }

    public static String endpointTag(EndpointContract e) {
        return "@e_" + stableId(e);
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
