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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.api.v2.graph.ActiveRecordingsFetcher.Active;
import io.cryostat.net.web.http.api.v2.graph.RecordingsFetcher.Recordings;
import io.cryostat.net.web.http.api.v2.graph.labels.LabelSelectorMatcher;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import graphql.schema.DataFetchingEnvironment;

@SuppressFBWarnings(
        value = "URF_UNREAD_FIELD",
        justification =
                "The Active and AggregateInfo fields are serialized and returned to the client by"
                        + " the GraphQL engine")
class ActiveRecordingsFetcher extends AbstractPermissionedDataFetcher<Active> {

    @Inject
    ActiveRecordingsFetcher(AuthManager auth) {
        super(auth);
    }

    @Override
    Set<String> applicableContexts() {
        return Set.of("Recordings");
    }

    @Override
    String name() {
        return "active";
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_RECORDING, ResourceAction.READ_TARGET);
    }

    @Override
    boolean blocking() {
        return false;
    }

    @Override
    public Active getAuthenticated(DataFetchingEnvironment environment) throws Exception {
        Recordings source = environment.getSource();
        List<GraphRecordingDescriptor> recordings = new ArrayList<>(source.active);

        FilterInput filter = FilterInput.from(environment);
        if (filter.contains(FilterInput.Key.NAME)) {
            String recordingName = filter.get(FilterInput.Key.NAME);
            recordings =
                    recordings.stream()
                            .filter(r -> Objects.equals(r.getName(), recordingName))
                            .collect(Collectors.toList());
        }
        if (filter.contains(FilterInput.Key.NAMES)) {
            List<String> recordingNames = filter.get(FilterInput.Key.NAMES);
            recordings =
                    recordings.stream()
                            .filter(r -> recordingNames.contains(r.getName()))
                            .collect(Collectors.toList());
        }

        if (filter.contains(FilterInput.Key.LABELS)) {
            List<String> labels = filter.get(FilterInput.Key.LABELS);
            for (String label : labels) {
                recordings =
                        recordings.stream()
                                .filter(
                                        r ->
                                                LabelSelectorMatcher.parse(label)
                                                        .test(r.getMetadata().getLabels()))
                                .collect(Collectors.toList());
            }
        }
        if (filter.contains(FilterInput.Key.STATE)) {
            String state = filter.get(FilterInput.Key.STATE);
            recordings =
                    recordings.stream()
                            .filter(r -> Objects.equals(r.getState().name(), state))
                            .collect(Collectors.toList());
        }
        if (filter.contains(FilterInput.Key.CONTINUOUS)) {
            boolean continuous = filter.get(FilterInput.Key.CONTINUOUS);
            recordings =
                    recordings.stream()
                            .filter(r -> Objects.equals(r.isContinuous(), continuous))
                            .collect(Collectors.toList());
        }
        if (filter.contains(FilterInput.Key.TO_DISK)) {
            boolean toDisk = filter.get(FilterInput.Key.TO_DISK);
            recordings =
                    recordings.stream()
                            .filter(r -> Objects.equals(r.getToDisk(), toDisk))
                            .collect(Collectors.toList());
        }
        if (filter.contains(FilterInput.Key.DURATION_GE)) {
            long duration = filter.get(FilterInput.Key.DURATION_GE);
            recordings =
                    recordings.stream()
                            .filter(r -> r.getDuration() >= duration)
                            .collect(Collectors.toList());
        }
        if (filter.contains(FilterInput.Key.DURATION_LE)) {
            long duration = filter.get(FilterInput.Key.DURATION_LE);
            recordings =
                    recordings.stream()
                            .filter(r -> r.getDuration() <= duration)
                            .collect(Collectors.toList());
        }
        if (filter.contains(FilterInput.Key.START_TIME_BEFORE)) {
            long startTime = filter.get(FilterInput.Key.START_TIME_BEFORE);
            recordings =
                    recordings.stream()
                            .filter(r -> r.getStartTime() <= startTime)
                            .collect(Collectors.toList());
        }
        if (filter.contains(FilterInput.Key.START_TIME_AFTER)) {
            long startTime = filter.get(FilterInput.Key.START_TIME_AFTER);
            recordings =
                    recordings.stream()
                            .filter(r -> r.getStartTime() >= startTime)
                            .collect(Collectors.toList());
        }

        Active active = new Active();
        AggregateInfo aggregate = new AggregateInfo();
        active.data = recordings;
        aggregate.count = Long.valueOf(active.data.size());
        active.aggregate = aggregate;

        return active;
    }

    static class Active {
        List<GraphRecordingDescriptor> data;
        AggregateInfo aggregate;
    }

    static class AggregateInfo {
        long count;
    }
}
