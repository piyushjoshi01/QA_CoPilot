package com.qacopilot.support;

import io.restassured.response.Response;

public final class ApiTestContext {
    private String baseUrl;
    private String requestBody;
    private Response response;
    private int simulatedStatusCode = 200;
    private String lastHttpMethod;
    private String lastRequestPath;
    private String lastRequestBodySnapshot;
    private String lastFullUrl;
    private Integer expectedHttpStatus;
    private String expectedBodyFragment;

    public void resetForScenario() {
        baseUrl = null;
        requestBody = null;
        response = null;
        simulatedStatusCode = 200;
        lastHttpMethod = null;
        lastRequestPath = null;
        lastRequestBodySnapshot = null;
        lastFullUrl = null;
        expectedHttpStatus = null;
        expectedBodyFragment = null;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public Response getResponse() {
        return response;
    }

    public void setResponse(Response response) {
        this.response = response;
    }

    public int getSimulatedStatusCode() {
        return simulatedStatusCode;
    }

    public void setSimulatedStatusCode(int simulatedStatusCode) {
        this.simulatedStatusCode = simulatedStatusCode;
    }

    public String getLastHttpMethod() {
        return lastHttpMethod;
    }

    public void setLastHttpMethod(String lastHttpMethod) {
        this.lastHttpMethod = lastHttpMethod;
    }

    public String getLastRequestPath() {
        return lastRequestPath;
    }

    public void setLastRequestPath(String lastRequestPath) {
        this.lastRequestPath = lastRequestPath;
    }

    public String getLastRequestBodySnapshot() {
        return lastRequestBodySnapshot;
    }

    public void setLastRequestBodySnapshot(String lastRequestBodySnapshot) {
        this.lastRequestBodySnapshot = lastRequestBodySnapshot;
    }

    public String getLastFullUrl() {
        return lastFullUrl;
    }

    public void setLastFullUrl(String lastFullUrl) {
        this.lastFullUrl = lastFullUrl;
    }

    public Integer getExpectedHttpStatus() {
        return expectedHttpStatus;
    }

    public void setExpectedHttpStatus(Integer expectedHttpStatus) {
        this.expectedHttpStatus = expectedHttpStatus;
    }

    public String getExpectedBodyFragment() {
        return expectedBodyFragment;
    }

    public void setExpectedBodyFragment(String expectedBodyFragment) {
        this.expectedBodyFragment = expectedBodyFragment;
    }
}
