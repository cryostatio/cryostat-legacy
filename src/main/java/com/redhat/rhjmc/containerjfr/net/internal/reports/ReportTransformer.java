package com.redhat.rhjmc.containerjfr.net.internal.reports;

import java.util.function.Consumer;

import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public interface ReportTransformer extends Consumer<Elements>, Comparable<ReportTransformer> {
    default int priority() {
        return 0;
    }

    default int compareTo(ReportTransformer o) {
        return priority() - o.priority();
    }

    default void transform(Document doc) {
        Elements els = doc.select(selector());
        if (!els.isEmpty()) {
            accept(els);
        }
    }

    String selector();
}
