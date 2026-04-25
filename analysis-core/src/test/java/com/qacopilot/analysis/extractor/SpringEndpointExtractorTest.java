package com.qacopilot.analysis.extractor;

import com.qacopilot.analysis.ir.EndpointContract;
import com.qacopilot.analysis.ir.FieldContract;
import com.qacopilot.analysis.ir.ParamBinding;
import com.qacopilot.analysis.ir.RequestBodyContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SpringEndpointExtractorTest {

    private final SpringEndpointExtractor extractor = new SpringEndpointExtractor();

    @Test
    void extractsGetAndPostMappingsFromRestController() {
        String source = """
                package demo;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping("/users")
                class UserController {
                    @GetMapping("/profile")
                    public String getProfile() { return "ok"; }

                    @PostMapping(path = "/create")
                    public String createUser() { return "ok"; }
                }
                """;

        List<EndpointContract> endpoints = extractor.extractFromSource(source);

        assertEquals(2, endpoints.size());
        assertEndpoint(endpoints.get(0), "GET", "/users/profile", "UserController", "getProfile");
        assertEndpoint(endpoints.get(1), "POST", "/users/create", "UserController", "createUser");
    }

    @Test
    void extractsDefaultRootWhenNoPathsProvided() {
        String source = """
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class HealthController {
                    @GetMapping
                    String health() { return "UP"; }
                }
                """;

        List<EndpointContract> endpoints = extractor.extractFromSource(source);

        assertEquals(1, endpoints.size());
        assertEndpoint(endpoints.get(0), "GET", "/", "HealthController", "health");
    }

    @Test
    void extractsPutPatchAndDeleteMappings() {
        String source = """
                import org.springframework.web.bind.annotation.DeleteMapping;
                import org.springframework.web.bind.annotation.PatchMapping;
                import org.springframework.web.bind.annotation.PutMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class ItemController {
                    @PutMapping("/items/{id}")
                    String putItem() { return "ok"; }

                    @PatchMapping("/items/{id}")
                    String patchItem() { return "ok"; }

                    @DeleteMapping("/items/{id}")
                    void deleteItem() { }
                }
                """;

        List<EndpointContract> endpoints = extractor.extractFromSource(source);

        assertEquals(3, endpoints.size());
        assertEndpoint(endpoints.get(0), "PUT", "/items/{id}", "ItemController", "putItem");
        assertEndpoint(endpoints.get(1), "PATCH", "/items/{id}", "ItemController", "patchItem");
        assertEndpoint(endpoints.get(2), "DELETE", "/items/{id}", "ItemController", "deleteItem");
    }

    @Test
    void extractsPathVariablesRequestParamsAndRequestBody() {
        String source = """
                package demo;

                import org.springframework.web.bind.annotation.PathVariable;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RequestBody;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RequestParam;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping("/api")
                class SearchController {
                    @PostMapping("/items/{id}/tag")
                    String tag(
                            @PathVariable("id") long id,
                            @RequestParam(name = "dryRun", required = false, defaultValue = "false") boolean dryRun,
                            @RequestBody String body
                    ) { return "ok"; }
                }
                """;

        List<EndpointContract> endpoints = extractor.extractFromSource(source);
        assertEquals(1, endpoints.size());
        EndpointContract e = endpoints.get(0);
        assertEquals("POST", e.getHttpMethod());
        assertEquals("/api/items/{id}/tag", e.getPath());

        List<String> pvNames = e.getPathParams().stream().map(ParamBinding::getName).sorted().collect(Collectors.toList());
        assertEquals(List.of("id"), pvNames);
        ParamBinding id = e.getPathParams().get(0);
        assertEquals("long", id.getJavaType());
        assertTrue(id.isRequired());

        assertEquals(1, e.getQueryParams().size());
        ParamBinding q = e.getQueryParams().get(0);
        assertEquals("dryRun", q.getName());
        assertEquals("false", q.getDefaultValue());
        assertFalse(q.isRequired());

        RequestBodyContract b = e.getRequestBody();
        assertTrue(b.isPresent());
        assertTrue(b.isRequired());
        assertEquals("String", b.getDtoJavaType());
    }

    @Test
    void extractFromProjectWalksJavaFiles(@TempDir Path root) throws Exception {
        Path pkg = root.resolve("src/main/java/demo");
        Files.createDirectories(pkg);
        Files.writeString(
                pkg.resolve("Api.java"),
                """
                        package demo;
                        import org.springframework.web.bind.annotation.GetMapping;
                        import org.springframework.web.bind.annotation.RestController;
                        @RestController
                        class Api {
                            @GetMapping("/x")
                            String x() { return "1"; }
                        }
                        """
        );

        List<EndpointContract> endpoints = extractor.extractFromProject(root, List.of(), List.of());

        assertEquals(1, endpoints.size());
        assertEndpoint(endpoints.get(0), "GET", "/x", "Api", "x");
        assertEquals("src/main/java/demo/Api.java", endpoints.get(0).getSourceFile());
    }

    @Test
    void ignoresNonRestControllerClasses() {
        String source = """
                import org.springframework.web.bind.annotation.GetMapping;

                class InternalClass {
                    @GetMapping("/internal")
                    String internal() { return "x"; }
                }
                """;

        List<EndpointContract> endpoints = extractor.extractFromSource(source);
        assertEquals(0, endpoints.size());
    }

    @Test
    void symbolSolverEnrichesRequestBodyFields(@TempDir Path root) throws Exception {
        Path pkg = root.resolve("src/main/java/demo");
        Files.createDirectories(pkg);
        Files.writeString(
                pkg.resolve("OrderDto.java"),
                """
                        package demo;
                        public class OrderDto {
                            @NotBlank
                            private String sku;
                        }
                        """
        );
        Files.writeString(
                pkg.resolve("OrderApi.java"),
                """
                        package demo;
                        import org.springframework.web.bind.annotation.PostMapping;
                        import org.springframework.web.bind.annotation.RequestBody;
                        import org.springframework.web.bind.annotation.RestController;
                        @RestController
                        public class OrderApi {
                            @PostMapping("/orders")
                            public void create(@RequestBody OrderDto body) {}
                        }
                        """
        );
        SpringEndpointExtractor ext = new SpringEndpointExtractor();
        List<EndpointContract> eps = ext.extractFromProject(root, List.of(), List.of(), SpringAnalysisOptions.defaults());
        assertEquals(1, eps.size());
        RequestBodyContract body = eps.get(0).getRequestBody();
        assertTrue(body.isPresent());
        List<FieldContract> fields = body.getFields();
        assertFalse(fields.isEmpty(), "Expected DTO fields via symbol solver");
        FieldContract sku =
                fields.stream().filter(f -> "sku".equals(f.getName())).findFirst().orElse(null);
        assertNotNull(sku);
        assertTrue(sku.isRequired());
        assertTrue(sku.getValidations().stream().anyMatch(v -> v.contains("NotBlank")));
    }

    @Test
    void extractsSpringdocHints() {
        String source = """
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @Tag(name = "Items")
                class C {
                    @PostMapping("/items")
                    @Operation(summary = "Create item", tags = {"write"})
                    @ApiResponses({
                        @ApiResponse(responseCode = "201"),
                        @ApiResponse(responseCode = "400")
                    })
                    void create() {}
                }
                """;

        List<EndpointContract> endpoints = extractor.extractFromSource(source);
        assertEquals(1, endpoints.size());
        EndpointContract e = endpoints.get(0);
        assertEquals("Create item", e.getOpenApiSummary());
        assertTrue(e.getOpenApiTags().contains("Items"));
        assertTrue(e.getOpenApiTags().contains("write"));
        assertTrue(e.getOpenApiResponseCodes().contains(201));
        assertTrue(e.getOpenApiResponseCodes().contains(400));
    }

    @Test
    void requestMappingWithExplicitMethod() {
        String source = """
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RequestMethod;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class R {
                    @RequestMapping(path = "/z", method = RequestMethod.PUT)
                    String z() { return "1"; }
                }
                """;

        List<EndpointContract> endpoints = extractor.extractFromSource(source);
        assertEquals(1, endpoints.size());
        assertEquals("PUT", endpoints.get(0).getHttpMethod());
        assertEquals("/z", endpoints.get(0).getPath());
    }

    private static void assertEndpoint(
            EndpointContract endpoint,
            String expectedHttpMethod,
            String expectedPath,
            String expectedController,
            String expectedHandler
    ) {
        assertEquals(expectedHttpMethod, endpoint.getHttpMethod());
        assertEquals(expectedPath, endpoint.getPath());
        assertEquals(expectedController, endpoint.getControllerClassName());
        assertEquals(expectedHandler, endpoint.getHandlerMethodName());
    }
}
