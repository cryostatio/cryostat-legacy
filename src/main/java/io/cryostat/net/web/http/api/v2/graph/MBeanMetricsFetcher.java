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
package io.cryostat.net.web.http.api.v2.graph;

import java.util.EnumSet;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.MBeanMetrics;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.discovery.TargetNode;

import graphql.schema.DataFetchingEnvironment;

public class MBeanMetricsFetcher extends AbstractPermissionedDataFetcher<MBeanMetrics> {

    private final TargetConnectionManager tcm;
    private final CredentialsManager credentialsManager;
    private final Logger logger;

    @Inject
    MBeanMetricsFetcher(
            AuthManager auth,
            TargetConnectionManager tcm,
            CredentialsManager credentialsManager,
            Logger logger) {
        super(auth);
        this.tcm = tcm;
        this.credentialsManager = credentialsManager;
        this.logger = logger;
    }

    @Override
    Set<String> applicableContexts() {
        return Set.of("TargetNode");
    }

    @Override
    String name() {
        return "mbeanMetrics";
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_TARGET, ResourceAction.READ_CREDENTIALS);
    }

    @Override
    public MBeanMetrics getAuthenticated(DataFetchingEnvironment environment) throws Exception {
        TargetNode source = (TargetNode) environment.getSource();
        ServiceRef target = source.getTarget();
        String targetId = target.getServiceUri().toString();
        ConnectionDescriptor cd =
                new ConnectionDescriptor(targetId, credentialsManager.getCredentials(target));
        try {
            return tcm.executeConnectedTask(cd, conn -> conn.getMBeanMetrics());
        } catch (Exception e) {
            logger.warn(e);
            return null;
        }
    }
}
