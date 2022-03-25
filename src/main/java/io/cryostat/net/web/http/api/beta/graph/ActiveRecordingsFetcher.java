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
package io.cryostat.net.web.http.api.beta.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.inject.Inject;

import io.cryostat.net.web.http.api.beta.graph.RecordingsFetcher.Recordings;
import io.cryostat.net.web.http.api.beta.graph.labels.LabelSelectorMatcher;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

class ActiveRecordingsFetcher implements DataFetcher<List<GraphRecordingDescriptor>> {

    @Inject
    ActiveRecordingsFetcher() {}

    public List<GraphRecordingDescriptor> get(DataFetchingEnvironment environment)
            throws Exception {
        Recordings source = environment.getSource();
        List<GraphRecordingDescriptor> result = new ArrayList<>(source.active);

        FilterInput filter = FilterInput.from(environment);
        if (filter.contains(FilterInput.Key.NAME)) {
            String recordingName = filter.get(FilterInput.Key.NAME);
            result =
                    result.stream()
                            .filter(r -> Objects.equals(r.getName(), recordingName))
                            .collect(Collectors.toList());
        }
        if (filter.contains(FilterInput.Key.LABELS)) {
            String labels = filter.get(FilterInput.Key.LABELS);
            result =
                    result.stream()
                            .filter(
                                    r ->
                                            LabelSelectorMatcher.parse(labels)
                                                    .test(r.getMetadata().getLabels()))
                            .collect(Collectors.toList());
        }
        if (filter.contains(FilterInput.Key.STATE)) {
            String state = filter.get(FilterInput.Key.STATE);
            result =
                    result.stream()
                            .filter(r -> Objects.equals(r.getState().name(), state))
                            .collect(Collectors.toList());
        }
        if (filter.contains(FilterInput.Key.CONTINUOUS)) {
            boolean continuous = filter.get(FilterInput.Key.CONTINUOUS);
            result =
                    result.stream()
                            .filter(r -> Objects.equals(r.isContinuous(), continuous))
                            .collect(Collectors.toList());
        }
        if (filter.contains(FilterInput.Key.TO_DISK)) {
            boolean toDisk = filter.get(FilterInput.Key.TO_DISK);
            result =
                    result.stream()
                            .filter(r -> Objects.equals(r.getToDisk(), toDisk))
                            .collect(Collectors.toList());
        }
        if (filter.contains(FilterInput.Key.DURATION_GE)) {
            long duration = filter.get(FilterInput.Key.DURATION_GE);
            result =
                    result.stream()
                            .filter(r -> r.getDuration() >= duration)
                            .collect(Collectors.toList());
        }
        if (filter.contains(FilterInput.Key.DURATION_LE)) {
            long duration = filter.get(FilterInput.Key.DURATION_LE);
            result =
                    result.stream()
                            .filter(r -> r.getDuration() <= duration)
                            .collect(Collectors.toList());
        }
        if (filter.contains(FilterInput.Key.START_TIME_BEFORE)) {
            long startTime = filter.get(FilterInput.Key.START_TIME_BEFORE);
            result =
                    result.stream()
                            .filter(r -> r.getStartTime() <= startTime)
                            .collect(Collectors.toList());
        }
        if (filter.contains(FilterInput.Key.START_TIME_AFTER)) {
            long startTime = filter.get(FilterInput.Key.START_TIME_AFTER);
            result =
                    result.stream()
                            .filter(r -> r.getStartTime() >= startTime)
                            .collect(Collectors.toList());
        }

        return result;
    }
}
