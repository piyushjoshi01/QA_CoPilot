package com.qacopilot.support;

import io.cucumber.java.Scenario;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Accumulates one row per Cucumber scenario and writes a standalone HTML report on test run finish
 * (see {@link QaCopilotHtmlReportPlugin}).
 */
public final class QaCopilotHtmlReport {

    private static final List<Row> ROWS = new ArrayList<>();
    private static final Object LOCK = new Object();
    private static final Pattern PATH_PARAM = Pattern.compile("\\{([^}]+)}");
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss zzz", Locale.US);

    private QaCopilotHtmlReport() {
    }

    public static void record(Scenario scenario, ApiTestContext ctx) {
        synchronized (LOCK) {
            ROWS.add(buildRow(scenario, ctx));
        }
    }

    public static void writeTo(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path html = outputDir.resolve("report.html");
        Files.writeString(html, buildDocument(), StandardCharsets.UTF_8);
    }

    /** Test hook. */
    static void clear() {
        synchronized (LOCK) {
            ROWS.clear();
        }
    }

    private static Row buildRow(Scenario scenario, ApiTestContext ctx) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("qacopilot.dryRun", "false"));
        String method = nz(ctx.getLastHttpMethod());
        String rawPath = nz(ctx.getLastRequestPath());
        String pathOnly = rawPath;
        String queryPart = "";
        int qIdx = rawPath.indexOf('?');
        if (qIdx >= 0) {
            pathOnly = rawPath.substring(0, qIdx);
            queryPart = rawPath.substring(qIdx + 1);
        }
        String fullUrl = nz(ctx.getLastFullUrl());
        if (fullUrl.isEmpty() && !rawPath.isEmpty()) {
            fullUrl = buildFullUrl(ctx.getBaseUrl(), rawPath);
        }
        List<String> pathPlaceholders = extractPathParams(pathOnly);
        Integer expectedStatus = ctx.getExpectedHttpStatus();
        Integer actualStatus =
                ctx.getResponse() != null
                        ? ctx.getResponse().statusCode()
                        : (ctx.getLastHttpMethod() != null ? ctx.getSimulatedStatusCode() : null);
        Boolean statusMatch =
                expectedStatus != null && actualStatus != null
                        ? expectedStatus.equals(actualStatus)
                        : null;
        String actualBody =
                ctx.getResponse() != null ? ctx.getResponse().asString() : "";
        String actualPreview = abbrev(actualBody, 16_000);
        String expectedFrag = ctx.getExpectedBodyFragment();
        Boolean bodyMatch =
                expectedFrag != null && !expectedFrag.isEmpty()
                        ? actualBody.contains(expectedFrag)
                        : null;

        return new Row(
                scenario.getName(),
                scenario.getUri().toString(),
                scenario.getStatus().name(),
                dryRun,
                nz(ctx.getBaseUrl()),
                method,
                fullUrl,
                rawPath,
                pathOnly,
                queryPart,
                pathPlaceholders,
                ctx.getLastRequestBodySnapshot(),
                expectedStatus,
                actualStatus,
                statusMatch,
                expectedFrag,
                actualPreview,
                bodyMatch
        );
    }

    public static String buildFullUrl(String base, String path) {
        if (path == null || path.isBlank()) {
            return nz(base);
        }
        String b = base == null ? "" : base.strip();
        if (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        String p = path.strip();
        if (!p.startsWith("/") && !p.contains("://")) {
            p = "/" + p;
        }
        return b + p;
    }

    private static List<String> extractPathParams(String path) {
        List<String> out = new ArrayList<>();
        if (path == null) {
            return out;
        }
        Matcher m = PATH_PARAM.matcher(path);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static String abbrev(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\r", "");
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "\n… truncated (" + t.length() + " chars) …";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String esc(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String buildDocument() {
        StringBuilder toc = new StringBuilder();
        StringBuilder cards = new StringBuilder();
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        int other = 0;
        int i = 1;
        synchronized (LOCK) {
            for (Row r : ROWS) {
                switch (r.status.toUpperCase(Locale.ROOT)) {
                    case "PASSED" -> passed++;
                    case "FAILED" -> failed++;
                    case "SKIPPED", "PENDING", "UNDEFINED" -> skipped++;
                    default -> other++;
                }
                toc.append("<li><a href=\"#scenario-")
                        .append(i)
                        .append("\">")
                        .append(esc(abbrev(r.scenarioName, 72)))
                        .append("</a> <span class=\"toc-mini\">")
                        .append(esc(r.status))
                        .append("</span></li>\n");
                cards.append(renderCard(i++, r));
            }
        }
        int total = passed + failed + skipped + other;
        String when = ZonedDateTime.now(ZoneId.systemDefault()).format(TS);
        String dryNote =
                Boolean.parseBoolean(System.getProperty("qacopilot.dryRun", "false"))
                        ? "<span class=\"pill pill-warn\">Dry run</span> (no HTTP; statuses simulated where steps define them)"
                        : "<span class=\"pill pill-ok\">Live HTTP</span>";

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>QA CoPilot — API test report</title>
                  <link rel="preconnect" href="https://fonts.googleapis.com"/>
                  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin/>
                  <link href="https://fonts.googleapis.com/css2?family=DM+Sans:ital,opsz,wght@0,9..40,400;0,9..40,500;0,9..40,600;0,9..40,700;1,9..40,400&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet"/>
                  <style>
                    :root {
                      --bg: #eceff4;
                      --surface: #ffffff;
                      --text: #0f172a;
                      --muted: #64748b;
                      --border: #cbd5e1;
                      --accent: #1d4ed8;
                      --accent-2: #3b82f6;
                      --ok: #047857;
                      --ok-soft: #d1fae5;
                      --fail: #b91c1c;
                      --fail-soft: #fee2e2;
                      --warn: #b45309;
                      --warn-soft: #fef3c7;
                      --mono: "JetBrains Mono", ui-monospace, monospace;
                      --sans: "DM Sans", system-ui, -apple-system, sans-serif;
                      --shadow: 0 1px 3px rgba(15,23,42,.08), 0 4px 12px rgba(15,23,42,.06);
                      --radius: 14px;
                    }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      font-family: var(--sans);
                      background: var(--bg);
                      color: var(--text);
                      line-height: 1.55;
                      font-size: 15px;
                    }
                    .top {
                      background: linear-gradient(125deg, #0f172a 0%, #1e3a8a 42%, #2563eb 100%);
                      color: #f8fafc;
                      padding: 2rem 1.5rem 2.25rem;
                      box-shadow: var(--shadow);
                    }
                    .top-inner { max-width: 1120px; margin: 0 auto; }
                    .top h1 {
                      margin: 0 0 .4rem;
                      font-size: 1.65rem;
                      font-weight: 700;
                      letter-spacing: -.03em;
                    }
                    .top .sub { margin: 0 0 1.25rem; opacity: .88; font-size: .95rem; max-width: 52rem; }
                    .stats {
                      display: flex;
                      flex-wrap: wrap;
                      gap: .65rem;
                      align-items: center;
                    }
                    .stat {
                      background: rgba(255,255,255,.12);
                      border: 1px solid rgba(255,255,255,.2);
                      border-radius: 10px;
                      padding: .5rem .85rem;
                      font-size: .85rem;
                    }
                    .stat b { font-size: 1.05rem; font-weight: 700; }
                    .stat-passed b { color: #6ee7b7; }
                    .stat-failed b { color: #fca5a5; }
                    .stat-skip b { color: #fcd34d; }
                    .meta-bar {
                      max-width: 1120px;
                      margin: -0.75rem auto 0;
                      padding: 0 1.25rem;
                      position: relative;
                      z-index: 2;
                    }
                    .meta-card {
                      background: var(--surface);
                      border-radius: var(--radius);
                      padding: 1rem 1.25rem;
                      box-shadow: var(--shadow);
                      border: 1px solid var(--border);
                      display: flex;
                      flex-wrap: wrap;
                      gap: .75rem 1.5rem;
                      align-items: center;
                      font-size: .88rem;
                      color: var(--muted);
                    }
                    .meta-card strong { color: var(--text); }
                    .pill {
                      display: inline-block;
                      padding: .2rem .55rem;
                      border-radius: 6px;
                      font-size: .72rem;
                      font-weight: 600;
                      text-transform: uppercase;
                      letter-spacing: .04em;
                    }
                    .pill-ok { background: var(--ok-soft); color: var(--ok); }
                    .pill-warn { background: var(--warn-soft); color: var(--warn); }
                    main { max-width: 1120px; margin: 0 auto; padding: 1.75rem 1.25rem 3rem; }
                    .layout { display: grid; grid-template-columns: 240px 1fr; gap: 1.5rem; align-items: start; }
                    @media (max-width: 900px) {
                      .layout { grid-template-columns: 1fr; }
                      nav.toc { position: static !important; }
                    }
                    nav.toc {
                      position: sticky;
                      top: 1rem;
                      background: var(--surface);
                      border: 1px solid var(--border);
                      border-radius: var(--radius);
                      padding: 1rem 1rem .85rem;
                      box-shadow: var(--shadow);
                      font-size: .82rem;
                    }
                    nav.toc h2 {
                      margin: 0 0 .65rem;
                      font-size: .72rem;
                      text-transform: uppercase;
                      letter-spacing: .08em;
                      color: var(--muted);
                      font-weight: 600;
                    }
                    nav.toc ol { margin: 0; padding-left: 1.1rem; }
                    nav.toc li { margin: .35rem 0; line-height: 1.35; }
                    nav.toc a { color: var(--accent); text-decoration: none; font-weight: 500; }
                    nav.toc a:hover { text-decoration: underline; }
                    .toc-mini {
                      display: block;
                      font-size: .68rem;
                      color: var(--muted);
                      font-weight: 500;
                      margin-top: .1rem;
                    }
                    .card {
                      background: var(--surface);
                      border: 1px solid var(--border);
                      border-radius: var(--radius);
                      margin-bottom: 1.35rem;
                      overflow: hidden;
                      box-shadow: var(--shadow);
                    }
                    .card-head {
                      display: flex;
                      flex-wrap: wrap;
                      justify-content: space-between;
                      gap: .75rem 1rem;
                      padding: 1.1rem 1.35rem;
                      background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
                      border-bottom: 1px solid var(--border);
                    }
                    .card-head .title { font-weight: 700; font-size: 1.05rem; color: #0f172a; }
                    .card-head .meta { font-size: .78rem; color: var(--muted); word-break: break-all; margin-top: .25rem; }
                    .badge {
                      padding: .3rem .75rem;
                      border-radius: 999px;
                      font-size: .7rem;
                      font-weight: 700;
                      text-transform: uppercase;
                      letter-spacing: .06em;
                      align-self: flex-start;
                    }
                    .badge-passed { background: var(--ok-soft); color: var(--ok); }
                    .badge-failed { background: var(--fail-soft); color: var(--fail); }
                    .badge-skipped, .badge-pending, .badge-undefined { background: var(--warn-soft); color: var(--warn); }
                    .badge-default { background: #e2e8f0; color: #475569; }
                    .section-title {
                      font-size: .72rem;
                      text-transform: uppercase;
                      letter-spacing: .1em;
                      color: var(--muted);
                      font-weight: 700;
                      margin: 1.15rem 0 .5rem;
                      padding-bottom: .35rem;
                      border-bottom: 2px solid #e2e8f0;
                    }
                    .section-title:first-child { margin-top: .35rem; }
                    .card-body { padding: 0 1.35rem 1.25rem; }
                    .kv-row {
                      display: grid;
                      grid-template-columns: 168px 1fr;
                      gap: .5rem 1rem;
                      padding: .55rem 0;
                      border-bottom: 1px solid #f1f5f9;
                      align-items: start;
                      font-size: .9rem;
                    }
                    .kv-row .k { font-weight: 600; color: var(--muted); }
                    .kv-row .v { word-break: break-word; }
                    pre.body {
                      margin: 0;
                      padding: 1rem 1.1rem;
                      background: #0f172a;
                      color: #e2e8f0;
                      font-family: var(--mono);
                      font-size: .78rem;
                      line-height: 1.5;
                      border-radius: 10px;
                      overflow-x: auto;
                      white-space: pre-wrap;
                      max-height: 32rem;
                      border: 1px solid #1e293b;
                    }
                    .empty { color: var(--muted); font-style: italic; }
                    .method {
                      display: inline-block;
                      font-family: var(--mono);
                      font-weight: 700;
                      font-size: .78rem;
                      padding: .2rem .5rem;
                      border-radius: 6px;
                      letter-spacing: .02em;
                    }
                    .method-GET { background: #dbeafe; color: #1d4ed8; }
                    .method-POST { background: #d1fae5; color: #047857; }
                    .method-PUT { background: #fef3c7; color: #b45309; }
                    .method-PATCH { background: #ede9fe; color: #5b21b6; }
                    .method-DELETE { background: #fee2e2; color: #b91c1c; }
                    .method-default { background: #e2e8f0; color: #334155; }
                    .compare {
                      display: grid;
                      grid-template-columns: 1fr 1fr;
                      gap: .75rem;
                      margin-top: .35rem;
                    }
                    @media (max-width: 640px) { .compare { grid-template-columns: 1fr; } }
                    .compare-box {
                      border: 1px solid var(--border);
                      border-radius: 10px;
                      padding: .75rem .9rem;
                      background: #fafafa;
                    }
                    .compare-box h4 {
                      margin: 0 0 .4rem;
                      font-size: .68rem;
                      text-transform: uppercase;
                      letter-spacing: .08em;
                      color: var(--muted);
                      font-weight: 700;
                    }
                    .compare-box .val {
                      font-family: var(--mono);
                      font-size: .95rem;
                      font-weight: 600;
                      color: #0f172a;
                    }
                    .match-pill {
                      display: inline-flex;
                      align-items: center;
                      gap: .35rem;
                      margin-top: .5rem;
                      font-size: .78rem;
                      font-weight: 600;
                      padding: .25rem .55rem;
                      border-radius: 6px;
                    }
                    .match-yes { background: var(--ok-soft); color: var(--ok); }
                    .match-no { background: var(--fail-soft); color: var(--fail); }
                    .match-na { background: #f1f5f9; color: var(--muted); }
                    footer {
                      text-align: center;
                      padding: 1.5rem 1rem;
                      font-size: .8rem;
                      color: var(--muted);
                      border-top: 1px solid var(--border);
                      background: #f8fafc;
                    }
                    .param-list { margin: .25rem 0 0; padding-left: 1.25rem; }
                    .param-list li { margin: .25rem 0; }
                    @media print {
                      nav.toc { display: none; }
                      .layout { grid-template-columns: 1fr; }
                      .card { break-inside: avoid; }
                      body { background: #fff; }
                    }
                  </style>
                </head>
                <body>
                  <div class="top">
                    <div class="top-inner">
                      <h1>API test report</h1>
                      <p class="sub">Structured view of each scenario: full URL, path &amp; query parameters, request body, and expected vs actual outcomes.</p>
                      <div class="stats">
                        <span class="stat"><b>"""
                + total
                + """
                </b> scenarios</span>
                        <span class="stat stat-passed"><b>"""
                + passed
                + """
                </b> passed</span>
                        <span class="stat stat-failed"><b>"""
                + failed
                + """
                </b> failed</span>
                        <span class="stat stat-skip"><b>"""
                + skipped
                + """
                </b> skipped</span>
                      </div>
                    </div>
                  </div>
                  <div class="meta-bar">
                    <div class="meta-card">
                      <span>Generated <strong>"""
                + esc(when)
                + """
                </strong></span>
                      <span>"""
                + dryNote
                + """
                </span>
                    </div>
                  </div>
                  <main>
                    <div class="layout">
                      <nav class="toc" aria-label="Scenarios">
                        <h2>Scenarios</h2>
                        <ol>
                """
                + toc
                + """
                        </ol>
                      </nav>
                      <div class="cards">
                """
                + cards
                + """
                      </div>
                    </div>
                  </main>
                  <footer>QA CoPilot test framework — maintain this file with your build artifacts or publish as a CI artifact.</footer>
                </body>
                </html>
                """;
    }

    private static String renderCard(int index, Row r) {
        String badgeClass =
                switch (r.status.toUpperCase(Locale.ROOT)) {
                    case "PASSED" -> "badge-passed";
                    case "FAILED" -> "badge-failed";
                    case "SKIPPED", "PENDING", "UNDEFINED" -> "badge-skipped";
                    default -> "badge-default";
                };
        String pathParamsHtml =
                r.pathParams.isEmpty()
                        ? "<span class=\"empty\">None — no <code>{…}</code> segments in path</span>"
                        : "<ul class=\"param-list\">"
                                + String.join(
                                        "",
                                        r.pathParams.stream()
                                                .map(
                                                        name ->
                                                                "<li><code>"
                                                                        + esc(name)
                                                                        + "</code> <span class=\"empty\">(template; RestAssured uses a placeholder)</span></li>"
                                                )
                                                .toList()
                                )
                                + "</ul>";
        String queryHtml =
                r.queryString.isEmpty()
                        ? "<span class=\"empty\">None</span>"
                        : "<pre class=\"body\" style=\"max-height:8rem;\">"
                                + esc(r.queryString)
                                + "</pre>";

        String reqBody =
                r.requestBody == null || r.requestBody.isBlank()
                        ? "<span class=\"empty\">No request body</span>"
                        : "<pre class=\"body\">" + esc(r.requestBody) + "</pre>";

        String statusCompare = renderStatusCompare(r);
        String bodySection = renderBodyCompare(r);

        return "<article class=\"card\" id=\"scenario-"
                + index
                + "\">"
                + "<div class=\"card-head\"><div><div class=\"title\">"
                + esc(r.scenarioName)
                + "</div>"
                + "<div class=\"meta\">"
                + esc(r.uri)
                + "</div></div>"
                + "<span class=\"badge "
                + badgeClass
                + "\">"
                + esc(r.status)
                + "</span></div>"
                + "<div class=\"card-body\">"
                + "<div class=\"section-title\">Request</div>"
                + kv(
                        "Execution mode",
                        r.dryRun
                                ? "<span class=\"pill pill-warn\">Dry run</span> no network call"
                                : "<span class=\"pill pill-ok\">Live HTTP</span>"
                )
                + kv("Base URL", r.baseUrl.isEmpty() ? "<span class=\"empty\">—</span>" : "<code>" + esc(r.baseUrl) + "</code>")
                + kv("HTTP method", methodPill(r.httpMethod))
                + kv("Full URL", r.fullUrl.isEmpty() ? "<span class=\"empty\">—</span>" : "<code>" + esc(r.fullUrl) + "</code>")
                + kv("Path template", r.pathTemplate.isEmpty() ? "<span class=\"empty\">—</span>" : "<code>" + esc(r.pathTemplate) + "</code>")
                + "<div class=\"section-title\">Parameters</div>"
                + kv("Path parameters", pathParamsHtml)
                + kv("Query string", queryHtml)
                + "<div class=\"section-title\">Request body</div>"
                + reqBody
                + "<div class=\"section-title\">Expected vs actual</div>"
                + statusCompare
                + bodySection
                + "</div></article>\n";
    }

    private static String renderStatusCompare(Row r) {
        String exp = r.expectedStatus == null ? "—" : String.valueOf(r.expectedStatus);
        String act = r.actualStatus == null ? "—" : String.valueOf(r.actualStatus);
        String matchHtml;
        if (r.statusMatch == null) {
            matchHtml =
                    "<span class=\"match-pill match-na\">N/A — missing expected or actual status in steps</span>";
        } else if (r.statusMatch) {
            matchHtml = "<span class=\"match-pill match-yes\">Match — HTTP status aligns with expectation</span>";
        } else {
            matchHtml =
                    "<span class=\"match-pill match-no\">Mismatch — expected "
                            + esc(exp)
                            + ", got "
                            + esc(act)
                            + "</span>";
        }
        return "<div class=\"compare\">"
                + "<div class=\"compare-box\"><h4>Expected status</h4><div class=\"val\">"
                + esc(exp)
                + "</div></div>"
                + "<div class=\"compare-box\"><h4>Actual status</h4><div class=\"val\">"
                + esc(act)
                + "</div></div></div>"
                + matchHtml;
    }

    private static String renderBodyCompare(Row r) {
        String expBlock =
                r.expectedBodyFragment == null || r.expectedBodyFragment.isEmpty()
                        ? "<span class=\"empty\">No <code>response body as text contains</code> assertion in this scenario</span>"
                        : "<pre class=\"body\" style=\"max-height:12rem;\">"
                                + esc(r.expectedBodyFragment)
                                + "</pre>";
        String actBlock =
                r.actualBodyPreview == null || r.actualBodyPreview.isEmpty()
                        ? "<span class=\"empty\">No response body (dry run or request not executed)</span>"
                        : "<pre class=\"body\">" + esc(r.actualBodyPreview) + "</pre>";
        String matchHtml;
        if (r.bodyMatch == null) {
            matchHtml =
                    "<span class=\"match-pill match-na\">Body substring check not applicable</span>";
        } else if (r.bodyMatch) {
            matchHtml = "<span class=\"match-pill match-yes\">Match — response contains expected substring</span>";
        } else {
            matchHtml =
                    "<span class=\"match-pill match-no\">Mismatch — response does not contain the expected substring</span>";
        }
        return "<div style=\"margin-top:.5rem;\"><h4 style=\"margin:0 0 .35rem;font-size:.72rem;text-transform:uppercase;letter-spacing:.08em;color:var(--muted);\">Expected body fragment</h4>"
                + expBlock
                + "</div>"
                + "<div style=\"margin-top:.85rem;\"><h4 style=\"margin:0 0 .35rem;font-size:.72rem;text-transform:uppercase;letter-spacing:.08em;color:var(--muted);\">Actual response body</h4>"
                + actBlock
                + "</div>"
                + matchHtml;
    }

    private static String kv(String label, String valueHtml) {
        return "<div class=\"kv-row\"><div class=\"k\">"
                + esc(label)
                + "</div><div class=\"v\">"
                + valueHtml
                + "</div></div>";
    }

    private static String methodPill(String m) {
        if (m == null || m.isEmpty()) {
            return "<span class=\"empty\">—</span>";
        }
        String u = m.toUpperCase(Locale.ROOT);
        String cls =
                switch (u) {
                    case "GET" -> "method-GET";
                    case "POST" -> "method-POST";
                    case "PUT" -> "method-PUT";
                    case "PATCH" -> "method-PATCH";
                    case "DELETE" -> "method-DELETE";
                    default -> "method-default";
                };
        return "<span class=\"method " + cls + "\">" + esc(u) + "</span>";
    }

    private record Row(
            String scenarioName,
            String uri,
            String status,
            boolean dryRun,
            String baseUrl,
            String httpMethod,
            String fullUrl,
            String pathRaw,
            String pathTemplate,
            String queryString,
            List<String> pathParams,
            String requestBody,
            Integer expectedStatus,
            Integer actualStatus,
            Boolean statusMatch,
            String expectedBodyFragment,
            String actualBodyPreview,
            Boolean bodyMatch
    ) {}
}
