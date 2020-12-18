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
package com.redhat.rhjmc.containerjfr.net;

import java.net.MalformedURLException;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.remote.JMXServiceURL;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.github.benmanes.caffeine.cache.Scheduler;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.Credentials;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnectionToolkit;
import dagger.Lazy;

public class TargetConnectionManager
        implements RemovalListener<ConnectionDescriptor, JFRConnection> {

    public static final Pattern HOST_PORT_PAIR_PATTERN =
            Pattern.compile("^([^:\\s]+)(?::(\\d{1,5}))?$");

    static final Duration DEFAULT_TTL = Duration.ofSeconds(90);

    private final Lazy<JFRConnectionToolkit> jfrConnectionToolkit;
    private final Logger logger;

    private final LoadingCache<ConnectionDescriptor, JFRConnection> connections;

    TargetConnectionManager(
            Lazy<JFRConnectionToolkit> jfrConnectionToolkit, Duration ttl, Logger logger) {
        this.jfrConnectionToolkit = jfrConnectionToolkit;
        this.logger = logger;

        this.connections =
                Caffeine.newBuilder()
                        .scheduler(Scheduler.systemScheduler())
                        .expireAfterAccess(ttl)
                        .removalListener(this)
                        .build(this::connect);
    }

    @Override
    public void onRemoval(
            ConnectionDescriptor descriptor, JFRConnection connection, RemovalCause cause) {
        if (descriptor == null) {
            logger.warn("Connection eviction triggered with null descriptor");
            return;
        }
        if (connection == null) {
            logger.warn("Connection eviction triggered with null connection");
            return;
        }
        logger.info(String.format("Removing cached connection for %s", descriptor.getTargetId()));
        connection.close();
    }

    public <T> T executeConnectedTask(
            ConnectionDescriptor connectionDescriptor, ConnectedTask<T> task) throws Exception {
        return task.execute(connections.get(connectionDescriptor));
    }

    /**
     * Returns a new JFRConnection to the specified Target. This does not do any connection reuse or
     * other management, so clients are responsible for cleaning up the connection when they are
     * finished with it. When possible, clients should use executeConnectedTask instead, which does
     * perform automatic cleanup when the provided task has been completed.
     */
    public JFRConnection connect(ConnectionDescriptor connectionDescriptor) throws Exception {
        try {
            return attemptConnectAsJMXServiceURL(connectionDescriptor);
        } catch (MalformedURLException mue) {
            return attemptConnectAsHostPortPair(connectionDescriptor);
        }
    }

    private JFRConnection attemptConnectAsJMXServiceURL(ConnectionDescriptor connectionDescriptor)
            throws Exception {
        return connect(
                new JMXServiceURL(connectionDescriptor.getTargetId()),
                connectionDescriptor.getCredentials());
    }

    private JFRConnection attemptConnectAsHostPortPair(ConnectionDescriptor connectionDescriptor)
            throws Exception {
        String s = connectionDescriptor.getTargetId();
        Matcher m = HOST_PORT_PAIR_PATTERN.matcher(s);
        if (!m.find()) {
            throw new MalformedURLException(s);
        }
        String host = m.group(1);
        String port = m.group(2);
        if (port == null) {
            port = "9091";
        }
        return connect(
                jfrConnectionToolkit.get().createServiceURL(host, Integer.parseInt(port)),
                connectionDescriptor.getCredentials());
    }

    private JFRConnection connect(JMXServiceURL url, Optional<Credentials> credentials)
            throws Exception {
        logger.info("Creating connection for {}", url.toString());
        return jfrConnectionToolkit.get().connect(url, credentials.orElse(null));
    }

    public interface ConnectedTask<T> {
        T execute(JFRConnection connection) throws Exception;
    }
}
