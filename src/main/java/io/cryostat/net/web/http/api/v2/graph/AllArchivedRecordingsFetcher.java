/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.cryostat.net.web.http.api.v2.graph;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.api.v2.graph.ArchivedRecordingsFetcher.AggregateInfo;
import io.cryostat.net.web.http.api.v2.graph.ArchivedRecordingsFetcher.Archived;
import io.cryostat.net.web.http.api.v2.graph.labels.LabelSelectorMatcher;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingMetadataManager.SecurityContext;
import io.cryostat.rules.ArchivedRecordingInfo;

import graphql.schema.DataFetchingEnvironment;

class AllArchivedRecordingsFetcher extends AbstractPermissionedDataFetcher<Archived> {

    private final RecordingArchiveHelper archiveHelper;
    private final Logger logger;

    @Inject
    AllArchivedRecordingsFetcher(
            AuthManager auth,
            CredentialsManager credentialsManager,
            RecordingArchiveHelper archiveHelper,
            Logger logger) {
        super(auth, credentialsManager);
        this.archiveHelper = archiveHelper;
        this.logger = logger;
    }

    @Override
    Set<String> applicableContexts() {
        return Set.of("Query");
    }

    @Override
    String name() {
        return "archivedRecordings";
    }

    @Override
    SecurityContext securityContext(DataFetchingEnvironment environment) {
        // FIXME what to do here? all users should be allowed to make this request, but should only
        // see recordings in the result where they have permissions to that specific recording and
        // its source target. Maybe we need to use the default context here, but within
        // getAuthenticated call the authManager directly to validate the token against each
        // specific recording and its stored context, and filter out the ones that fail
        return SecurityContext.DEFAULT;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_RECORDING);
    }

    @Override
    Archived getAuthenticated(DataFetchingEnvironment environment) throws Exception {
        FilterInput filter = FilterInput.from(environment);
        List<ArchivedRecordingInfo> recordings = new ArrayList<>();
        if (filter.contains(FilterInput.Key.SOURCE_TARGET)) {
            String targetId = filter.get(FilterInput.Key.SOURCE_TARGET);
            try {
                recordings = archiveHelper.getRecordings(targetId).get();
            } catch (ExecutionException e) {
                logger.warn(
                        "Failed to fetch archived recordings for target {}, msg: {}",
                        targetId,
                        e.getMessage());
                recordings = List.of();
            }
        } else {
            recordings = archiveHelper.getRecordings().get();
        }
        if (filter.contains(FilterInput.Key.NAME)) {
            String recordingName = filter.get(FilterInput.Key.NAME);
            recordings =
                    recordings.stream()
                            .filter(r -> Objects.equals(r.getName(), recordingName))
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
}
