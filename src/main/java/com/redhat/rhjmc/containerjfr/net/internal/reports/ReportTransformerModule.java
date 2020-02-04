package com.redhat.rhjmc.containerjfr.net.internal.reports;

import java.util.Set;

import com.redhat.rhjmc.containerjfr.core.reports.ReportTransformer;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;

@Module
public abstract class ReportTransformerModule {

    @Provides
    @ElementsIntoSet
    static Set<ReportTransformer> provideReportTransformers() {
        return Set.of();
    }
}
