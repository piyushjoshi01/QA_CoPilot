package com.qacopilot.stepdefs.common;

import com.qacopilot.support.ApiTestContext;
import com.qacopilot.support.GeneratedPayloadFactory;
import com.qacopilot.support.QaCopilotHtmlReport;
import com.qacopilot.support.RestAssuredClient;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.junit.jupiter.api.Assertions;

import java.nio.charset.StandardCharsets;

/**
 * Shared REST/Gherkin step definitions used by all domain features. Keep a single definition per step phrase here
 * to avoid Cucumber duplicate-step errors.
 */
public final class CommonSteps {
    private final ApiTestContext context = new ApiTestContext();
    private final RestAssuredClient client = new RestAssuredClient();
    private Scenario scenario;

    @Before
    public void bindScenario(Scenario scenario) {
        this.scenario = scenario;
        context.resetForScenario();
    }

    @After
    public void emitHtmlReportRow(Scenario scenario) {
        QaCopilotHtmlReport.record(scenario, context);
    }

    private boolean dryRun() {
        return Boolean.parseBoolean(System.getProperty("qacopilot.dryRun", "false"));
    }

    @Given("API base URL is configured")
    public void apiBaseUrlIsConfigured() {
        String baseUrl = System.getProperty("api.base.url", "http://localhost:8080");
        context.setBaseUrl(baseUrl);
    }

    @And("request body matches {word}")
    public void requestBodyMatches(String requestType) {
        context.setRequestBody(GeneratedPayloadFactory.payloadFor(requestType));
    }

    @And("request body is:")
    public void requestBodyIsDocString(String body) {
        context.setRequestBody(body == null ? "" : body.strip());
    }

    @When("I send a {word} request to {string}")
    public void iSendARequestTo(String method, String path) {
        context.setLastHttpMethod(method);
        context.setLastRequestPath(path);
        context.setLastRequestBodySnapshot(context.getRequestBody());
        context.setLastFullUrl(QaCopilotHtmlReport.buildFullUrl(context.getBaseUrl(), path));
        logLine(">>> HTTP " + method + " " + path + " | body: " + abbrev(context.getRequestBody(), 800));
        if (dryRun()) {
            context.setSimulatedStatusCode(200);
            logLine("<<< (dry-run) no HTTP call executed");
            return;
        }
        context.setResponse(client.execute(method, context.getBaseUrl(), path, context.getRequestBody()));
        var res = context.getResponse();
        logLine("<<< status " + res.statusCode() + " | body: " + abbrev(res.asString(), 1200));
        attachText("response-body.txt", res.asString());
    }

    @Then("response status should be {int}")
    public void responseStatusShouldBe(int statusCode) {
        context.setExpectedHttpStatus(statusCode);
        if (dryRun()) {
            context.setSimulatedStatusCode(statusCode);
            logLine("(dry-run) treating status as " + statusCode);
            return;
        }
        Assertions.assertNotNull(context.getResponse(), "Response should not be null");
        Assertions.assertEquals(statusCode, context.getResponse().statusCode());
    }

    @Then("response body as text contains {string}")
    public void responseBodyContains(String fragment) {
        context.setExpectedBodyFragment(fragment);
        if (dryRun()) {
            logLine("(dry-run) skipping body contains check for: " + fragment);
            return;
        }
        Assertions.assertNotNull(context.getResponse());
        Assertions.assertTrue(
                context.getResponse().asString().contains(fragment),
                "Body should contain: " + fragment
        );
    }

    @And("the endpoint contract is logged for reporting")
    public void logEndpointContract() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Endpoint contract snapshot ===\n");
        sb.append("Method: ").append(nullToEmpty(context.getLastHttpMethod())).append("\n");
        sb.append("URL: ").append(nullToEmpty(context.getLastFullUrl())).append("\n");
        sb.append("Path: ").append(nullToEmpty(context.getLastRequestPath())).append("\n");
        sb.append("Request body (snapshot): ").append(abbrev(nullToEmpty(context.getLastRequestBodySnapshot()), 4000)).append("\n");
        if (dryRun()) {
            sb.append("(dry-run) simulated status: ").append(context.getSimulatedStatusCode()).append("\n");
        } else if (context.getResponse() != null) {
            sb.append("Response status: ").append(context.getResponse().statusCode()).append("\n");
            sb.append("Response body (preview): ")
                    .append(abbrev(context.getResponse().asString(), 8000))
                    .append("\n");
        }
        logLine(sb.toString());
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private void logLine(String msg) {
        if (scenario != null) {
            scenario.log(msg);
        }
    }

    private void attachText(String name, String text) {
        if (scenario == null || text == null) {
            return;
        }
        String t = text.length() > 64_000 ? text.substring(0, 64_000) + "\n...truncated..." : text;
        scenario.attach(t.getBytes(StandardCharsets.UTF_8), "text/plain", name);
    }

    private static String abbrev(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\r", "");
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "\n...truncated (" + t.length() + " chars)...";
    }
}
