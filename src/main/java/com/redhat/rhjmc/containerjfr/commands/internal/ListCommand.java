/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
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
 * #L%
 */
package com.redhat.rhjmc.containerjfr.commands.internal;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.ArrayUtils;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.web.WebServer;

/** @deprecated Use HTTP GET /api/v1/targets/:targetId/recordings */
@Deprecated
@Singleton
class ListCommand extends AbstractConnectedCommand implements SerializableCommand {

    private final ClientWriter cw;
    private final WebServer exporter;

    @Inject
    ListCommand(
            ClientWriter cw, TargetConnectionManager targetConnectionManager, WebServer exporter) {
        super(targetConnectionManager);
        this.cw = cw;
        this.exporter = exporter;
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public void execute(String[] args) throws Exception {
        targetConnectionManager.executeConnectedTask(
                args[0],
                connection -> {
                    cw.println("Available recordings:");
                    Collection<IRecordingDescriptor> recordings =
                            connection.getService().getAvailableRecordings();
                    if (recordings.isEmpty()) {
                        cw.println("\tNone");
                    }
                    for (IRecordingDescriptor recording : recordings) {
                        HyperlinkedSerializableRecordingDescriptor descriptor =
                                new HyperlinkedSerializableRecordingDescriptor(
                                        recording,
                                        exporter.getDownloadURL(connection, recording.getName()),
                                        exporter.getReportURL(connection, recording.getName()));
                        cw.println(toString(descriptor));
                    }
                    return null;
                });
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            return targetConnectionManager.executeConnectedTask(
                    args[0],
                    connection -> {
                        List<IRecordingDescriptor> origDescriptors =
                                connection.getService().getAvailableRecordings();
                        List<HyperlinkedSerializableRecordingDescriptor> descriptors =
                                new ArrayList<>(origDescriptors.size());
                        for (IRecordingDescriptor desc : origDescriptors) {
                            descriptors.add(
                                    new HyperlinkedSerializableRecordingDescriptor(
                                            desc,
                                            exporter.getDownloadURL(connection, desc.getName()),
                                            exporter.getReportURL(connection, desc.getName())));
                        }
                        return new ListOutput<>(descriptors);
                    });
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 1) {
            cw.println("Expected one argument: hostname:port, ip:port, or JMX service URL");
            return false;
        }
        boolean isValidTargetId = validateTargetId(args[0]);
        if (!isValidTargetId) {
            cw.println(String.format("%s is an invalid connection specifier", args[0]));
        }
        return isValidTargetId;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private static String toString(HyperlinkedSerializableRecordingDescriptor descriptor)
            throws Exception {
        StringBuilder sb = new StringBuilder();
        Method[] methods =
                ArrayUtils.addAll(
                        descriptor.getClass().getSuperclass().getDeclaredMethods(),
                        descriptor.getClass().getDeclaredMethods());
        for (Method m : methods) {
            if (m.getParameterTypes().length == 0
                    && (m.getName().startsWith("get") || m.getName().startsWith("is"))) {
                sb.append("\t");
                sb.append(m.getName());

                sb.append("\t\t");
                sb.append(m.invoke(descriptor));

                sb.append("\n");
            }
        }

        return sb.toString();
    }
}
