/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.net.web.http.api.v2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.function.Function;

import javax.inject.Named;
import javax.inject.Singleton;

import io.cryostat.net.security.CertificateValidator;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.v2.graph.GraphModule;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module(includes = {GraphModule.class})
public abstract class HttpApiV2Module {
    @Binds
    @IntoSet
    abstract RequestHandler bindProbeTemplateUploadHandler(ProbeTemplateUploadHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindProbeTemplateUploadBodyHandler(
            ProbeTemplateUploadBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindProbeTemplateDeleteHandler(ProbeTemplateDeleteHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetProbePostHandler(TargetProbePostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetProbeDeleteHandler(TargetProbeDeleteHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetProbesGetHandler(TargetProbesGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindAuthPostHandler(AuthPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindLogoutPostHandler(LogoutPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindApiGetHandler(ApiGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetsPostHandler(TargetsPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetsPostBodyHandler(TargetsPostBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetDeleteHandler(TargetDeleteHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetSnapshotPostHandler(TargetSnapshotPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindCertificatePostHandler(CertificatePostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetRecordingOptionsListGetHandler(
            TargetRecordingOptionsListGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetEventsGetHandler(TargetEventsGetHandler handler);

    @Provides
    @Singleton
    @Named("OutputStreamFunction")
    static Function<File, FileOutputStream> provideOutputStreamFunction() throws RuntimeException {
        return (File file) -> {
            try {
                return new FileOutputStream(file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Provides
    static CertificateValidator provideCertificateValidator() {
        return new CertificateValidator();
    }

    @Binds
    @IntoSet
    abstract RequestHandler bindCertificatePostBodyHandler(CertificatePostBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetCredentialsPostHandler(TargetCredentialsPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetCredentialsPostBodyHandler(
            TargetCredentialsPostBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetCredentialsDeleteHandler(
            TargetCredentialsDeleteHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRulesGetHandler(RulesGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRuleGetHandler(RuleGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRuleDeleteHandler(RuleDeleteHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRulePatchBodyHandler(RulePatchBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRulePatchHandler(RulePatchHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRulesPostHandler(RulesPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRulesPostBodyHandler(RulesPostBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindDiscoveryGetHandler(DiscoveryGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindDiscoveryRegistrationCheckHandler(
            DiscoveryRegistrationCheckHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindDiscoveryRegistrationHandler(DiscoveryRegistrationHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindDiscoveryRegistrationBodyHandler(
            DiscoveryRegistrationBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindDiscoveryDeregistrationHandler(
            DiscoveryDeregistrationHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindDiscoveryPostHandler(DiscoveryPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindDiscoveryPostBodyHandler(DiscoveryPostBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindAuthTokenPostHandler(AuthTokenPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindAuthTokenPostBodyHandler(AuthTokenPostBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetRecordingGetHandler(TargetRecordingGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetReportGetHandler(TargetReportGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetTemplateGetHandler(TargetTemplateGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRecordingGetHandler(RecordingGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindReportGetHandler(ReportGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetCredentialsGetHandler(TargetCredentialsGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindCredentialsGetHandler(CredentialsGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindCredentialGetHandler(CredentialGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindCredentialDeleteHandler(CredentialDeleteHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindCredentialsPostHandler(CredentialsPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindCredentialsPostBodyHandler(CredentialsPostBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindMBeanMetricsGetHandler(MBeanMetricsGetHandler handler);
}
