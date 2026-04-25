package com.qacopilot.support;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.TestRunFinished;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Cucumber plugin: flushes {@link QaCopilotHtmlReport} when the test run completes.
 * <p>Register in {@code cucumber.properties}, for example:
 * {@code cucumber.plugin=pretty, com.qacopilot.support.QaCopilotHtmlReportPlugin:build/reports/qacopilot-api}
 */
public final class QaCopilotHtmlReportPlugin implements ConcurrentEventListener {

    private final Path outputDir;

    @SuppressWarnings("unused")
    public QaCopilotHtmlReportPlugin(String arg) {
        String dir = arg == null || arg.isBlank() ? "build/reports/qacopilot-api" : arg.strip();
        this.outputDir = Paths.get(dir).toAbsolutePath().normalize();
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestRunFinished.class, event -> flush());
    }

    private void flush() {
        try {
            QaCopilotHtmlReport.writeTo(outputDir);
            System.out.println("QA CoPilot HTML report: " + outputDir.resolve("report.html").toUri());
        } catch (Exception e) {
            System.err.println("QA CoPilot: failed to write HTML report: " + e.getMessage());
        }
    }
}
