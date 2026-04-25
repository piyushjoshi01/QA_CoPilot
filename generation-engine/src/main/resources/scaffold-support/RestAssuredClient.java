package com.qacopilot.support;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

public final class RestAssuredClient {
    public Response execute(String method, String baseUrl, String path, String requestBody) {
        String resolvedPath = path.replaceAll("\\{[^/]+}", "demo");
        var request = RestAssured.given()
                .baseUri(baseUrl)
                .contentType(ContentType.JSON);

        if (requestBody != null && !requestBody.isBlank()) {
            request.body(requestBody);
        }

        return switch (method.toUpperCase()) {
            case "GET" -> request.get(resolvedPath);
            case "POST" -> request.post(resolvedPath);
            case "PUT" -> request.put(resolvedPath);
            case "PATCH" -> request.patch(resolvedPath);
            case "DELETE" -> request.delete(resolvedPath);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        };
    }
}
