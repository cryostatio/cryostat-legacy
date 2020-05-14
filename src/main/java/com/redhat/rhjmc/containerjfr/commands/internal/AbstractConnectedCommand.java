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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;

import javax.management.remote.JMXServiceURL;

import com.redhat.rhjmc.containerjfr.commands.Command;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnectionToolkit;
import com.redhat.rhjmc.containerjfr.net.ConnectionListener;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

abstract class AbstractConnectedCommand implements Command, ConnectionListener {

    protected final JFRConnectionToolkit jfrConnectionToolkit;
    // maintain a short-lived cache of connections to allow Commands to nest ConnectedTasks
    // without having to manage connection reuse
    private final Map<String, JFRConnection> activeConnections = new HashMap<>();

    AbstractConnectedCommand(JFRConnectionToolkit jfrConnectionToolkit) {
        this.jfrConnectionToolkit = jfrConnectionToolkit;
    }

    @Override
    public final void connectionChanged(JFRConnection connection) {
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    protected Optional<IRecordingDescriptor> getDescriptorByName(String hostId, String name) throws Exception {
            return executeConnectedTask(hostId, connection -> {
                return connection.getService().getAvailableRecordings().stream()
                    .filter(recording -> recording.getName().equals(name))
                    .findFirst();
            });
    }

    protected <T> T executeConnectedTask(String hostId, ConnectedTask<T> task) throws Exception {
        try {
            if (activeConnections.containsKey(hostId)) {
                return task.execute(activeConnections.get(hostId));
            } else {
                try (JFRConnection connection = attemptConnect(hostId)) {
                    return task.execute(connection);
                }
            }
        } finally {
            activeConnections.remove(hostId);
        }
    }

    // TODO refactor, this is duplicated in WebServer
    private JFRConnection attemptConnect(String hostId) throws Exception {
        try {
            return attemptConnectAsJMXServiceURL(hostId);
        } catch (Exception e) {
            return attemptConnectAsHostPortPair(hostId);
        }
    }

    private JFRConnection attemptConnectAsJMXServiceURL(String url) throws Exception {
        return jfrConnectionToolkit.connect(new JMXServiceURL(url));
    }

    private JFRConnection attemptConnectAsHostPortPair(String s) throws Exception {
        Matcher m = HOST_PORT_PAIR_PATTERN.matcher(s);
        if (!m.find()) {
            return null;
        }
        String host = m.group(1);
        String port = m.group(2);
        if (port == null) {
            port = "9091";
        }
        return jfrConnectionToolkit.connect(host, Integer.parseInt(port));
    }

    @SuppressWarnings("serial")
    static class JMXConnectionException extends Exception {
        JMXConnectionException() {
            super("No active JMX connection");
        }
    }

    interface ConnectedTask<T> {
        T execute(JFRConnection connection) throws Exception;
    }
}
