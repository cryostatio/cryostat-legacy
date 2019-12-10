package com.redhat.rhjmc.containerjfr.net.internal.reports;

import java.util.Set;

import com.redhat.rhjmc.containerjfr.core.log.Logger;

import dagger.Module;
import dagger.Provides;

@Module(
        includes = {
            ReportTransformerModule.class,
        })
public abstract class ReportsModule {

    @Provides
    static ReportGenerator provideReportGenerator(
            Logger logger, Set<ReportTransformer> transformers) {
        return new ReportGenerator(logger, transformers);
    }
}
