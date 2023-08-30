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
import io.cryostat.net.web.http.api.v2.graph.ArchivedRecordingsFetcher.Archived;
import io.cryostat.net.web.http.api.v2.graph.RecordingsFetcher.Recordings;
import io.cryostat.net.web.http.api.v2.graph.labels.LabelSelectorMatcher;
import io.cryostat.rules.ArchivedRecordingInfo;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import graphql.schema.DataFetchingEnvironment;

@SuppressFBWarnings(
        value = "URF_UNREAD_FIELD",
        justification =
                "The Archived and AggregateInfo fields are serialized and returned to the client by"
                        + " the GraphQL engine")
class ArchivedRecordingsFetcher extends AbstractPermissionedDataFetcher<Archived> {

    @Inject
    ArchivedRecordingsFetcher(AuthManager auth) {
        super(auth);
    }

    @Override
    Set<String> applicableContexts() {
        return Set.of("Recordings");
    }

    @Override
    String name() {
        return "archived";
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_RECORDING);
    }

    @Override
    boolean blocking() {
        return false;
    }

    public Archived getAuthenticated(DataFetchingEnvironment environment) throws Exception {
        Recordings source = environment.getSource();
        FilterInput filter = FilterInput.from(environment);
        List<ArchivedRecordingInfo> recordings = new ArrayList<>(source.archived);
        if (filter.contains(FilterInput.Key.NAME)) {
            String recordingName = filter.get(FilterInput.Key.NAME);
            recordings =
                    recordings.stream()
                            .filter(r -> Objects.equals(r.getName(), recordingName))
                            .collect(Collectors.toList());
        }
        if (filter.contains(FilterInput.Key.NAMES)) {
            List<String> names = filter.get(FilterInput.Key.NAMES);
            recordings =
                    recordings.stream()
                            .filter(r -> names.contains(r.getName()))
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
        if (filter.contains(FilterInput.Key.SIZE_GE)) {
            long fileSize = filter.get(FilterInput.Key.SIZE_GE);
            recordings =
                    recordings.stream()
                            .filter(r -> r.getSize() >= fileSize)
                            .collect(Collectors.toList());
        }
        if (filter.contains(FilterInput.Key.SIZE_LE)) {
            long fileSize = filter.get(FilterInput.Key.SIZE_LE);
            recordings =
                    recordings.stream()
                            .filter(r -> r.getSize() <= fileSize)
                            .collect(Collectors.toList());
        }
        if (filter.contains(FilterInput.Key.ARCHIVED_TIME_AFTER)) {
            long startTime = filter.get(FilterInput.Key.ARCHIVED_TIME_AFTER);
            recordings =
                    recordings.stream()
                            .filter(r -> r.getArchivedTime() >= startTime)
                            .collect(Collectors.toList());
        }
        if (filter.contains(FilterInput.Key.ARCHIVED_TIME_BEFORE)) {
            long endTime = filter.get(FilterInput.Key.ARCHIVED_TIME_BEFORE);
            recordings =
                    recordings.stream()
                            .filter(r -> r.getArchivedTime() <= endTime)
                            .collect(Collectors.toList());
        }

        Archived archived = new Archived();
        AggregateInfo aggregate = new AggregateInfo();
        archived.data = recordings;
        aggregate.count = archived.data.size();
        aggregate.size = archived.data.stream().mapToLong(ArchivedRecordingInfo::getSize).sum();
        archived.aggregate = aggregate;

        return archived;
    }

    static class Archived {
        List<ArchivedRecordingInfo> data;
        AggregateInfo aggregate;
    }

    static class AggregateInfo {
        long count;
        long size;
    }
}
