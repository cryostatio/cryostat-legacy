package com.redhat.rhjmc.containerjfr.net.internal.reports;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.TreeSet;

import com.redhat.rhjmc.containerjfr.core.log.Logger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openjdk.jmc.flightrecorder.CouldNotLoadRecordingException;
import org.openjdk.jmc.flightrecorder.rules.report.html.JfrHtmlRulesReport;

public class ReportGenerator {

    private final Logger logger;
    private final Set<ReportTransformer> transformers;

    ReportGenerator(Logger logger, Set<ReportTransformer> transformers) {
        this.logger = logger;
        this.transformers = new TreeSet<>(transformers);
    }

    public String generateReport(InputStream recording) throws IOException, CouldNotLoadRecordingException {
        String report = JfrHtmlRulesReport.createReport(recording);
        if (!transformers.isEmpty()) {
            try {
                Document document = Jsoup.parse(report);
                transformers.forEach(t -> t.transform(document));
                return document.outerHtml();
            } catch (Exception e) {
                logger.warn(e);
            }
        }
        return report;
    }
}
