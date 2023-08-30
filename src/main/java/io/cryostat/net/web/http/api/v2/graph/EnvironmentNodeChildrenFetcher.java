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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.EnvironmentNode;

import graphql.schema.DataFetchingEnvironment;

class EnvironmentNodeChildrenFetcher extends AbstractPermissionedDataFetcher<List<AbstractNode>> {

    @Inject
    EnvironmentNodeChildrenFetcher(AuthManager auth) {
        super(auth);
    }

    @Override
    Set<String> applicableContexts() {
        return Set.of("EnvironmentNode");
    }

    @Override
    String name() {
        return "children";
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_TARGET);
    }

    @Override
    boolean blocking() {
        return false;
    }

    @Override
    public List<AbstractNode> getAuthenticated(DataFetchingEnvironment environment)
            throws Exception {
        EnvironmentNode node = environment.getSource();
        return new ArrayList<>(node.getChildren());
    }
}
