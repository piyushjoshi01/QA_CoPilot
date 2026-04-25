package com.qacopilot.analysis.extractor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityFilterChainScannerTest {

    @Test
    void detectsSecurityCallPatterns(@TempDir Path root) throws Exception {
        Path pkg = root.resolve("src/main/java/cfg");
        Files.createDirectories(pkg);
        Files.writeString(
                pkg.resolve("SecurityConfig.java"),
                """
                        package cfg;
                        import org.springframework.context.annotation.Bean;
                        class SecurityConfig {
                            @Bean
                            org.springframework.security.web.SecurityFilterChain chain(
                                    org.springframework.security.config.annotation.web.builders.HttpSecurity http
                            ) throws Exception {
                                return http.requestMatchers("/api/public/**").oauth2ResourceServer().and().build();
                            }
                        }
                        """
        );

        SecurityScanResult r = SecurityFilterChainScanner.scan(root, List.of(), List.of());
        assertTrue(r.getPatterns().contains("oauth2ResourceServer"));
        assertTrue(r.getRequestMatcherLiterals().contains("/api/public/**"));
    }
}
