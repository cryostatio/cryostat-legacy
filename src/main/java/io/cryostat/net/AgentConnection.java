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
package io.cryostat.net;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.ReflectionException;
import javax.management.remote.JMXServiceURL;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.rjmx.ConnectionException;
import org.openjdk.jmc.rjmx.IConnectionHandle;
import org.openjdk.jmc.rjmx.ServiceNotAvailableException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import io.cryostat.core.FlightRecorderException;
import io.cryostat.core.net.IDException;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.net.MBeanMetrics;
import io.cryostat.core.net.MemoryMetrics;
import io.cryostat.core.net.OperatingSystemMetrics;
import io.cryostat.core.net.RuntimeMetrics;
import io.cryostat.core.net.ThreadMetrics;
import io.cryostat.core.sys.Clock;
import io.cryostat.core.templates.Template;
import io.cryostat.core.templates.TemplateService;
import io.cryostat.core.templates.TemplateType;
import io.cryostat.recordings.JvmIdHelper;
import io.vertx.ext.web.client.WebClient;
import org.jsoup.nodes.Document;

class AgentConnection implements JFRConnection {

    private final URI agentUri;
    private final long httpTimeout;
    private final WebClient webClient;
    private final Clock clock;
    private final JvmIdHelper idHelper;

    AgentConnection(
            URI agentUri,
            long httpTimeoutSeconds,
            WebClient webClient,
            Clock clock,
            JvmIdHelper idHelper) {
        this.agentUri = agentUri;
        this.httpTimeout = httpTimeoutSeconds;
        this.webClient = webClient;
        this.clock = clock;
        this.idHelper = idHelper;
    }

    @Override
    public void close() throws Exception {}

    @Override
    public void connect() throws ConnectionException {
        // TODO test connection by pinging agent callback
    }

    @Override
    public void disconnect() {}

    @Override
    public long getApproximateServerTime(Clock arg0) {
        return clock.now().toEpochMilli();
    }

    @Override
    public IConnectionHandle getHandle() throws ConnectionException, IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getHost() {
        return agentUri.getHost();
    }

    @Override
    public JMXServiceURL getJMXURL() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getJvmId() throws IDException, IOException {
        // this should have already been populated when the agent published itself to the Discovery
        // API. If not, then this will fail, but we were in a bad state to begin with.
        return idHelper.getJvmId(agentUri.toString());
    }

    @Override
    public int getPort() {
        return agentUri.getPort();
    }

    @Override
    public IFlightRecorderService getService()
            throws ConnectionException, IOException, ServiceNotAvailableException {
        return new AgentJFRService(httpTimeout, webClient);
    }

    @Override
    public TemplateService getTemplateService() {
        return new TemplateService() {

            @Override
            public Optional<IConstrainedMap<EventOptionID>> getEvents(
                    String name, TemplateType type) throws FlightRecorderException {
                return Optional.empty();
            }

            @Override
            public List<Template> getTemplates() throws FlightRecorderException {
                return List.of();
            }

            @Override
            public Optional<Document> getXml(String name, TemplateType type)
                    throws FlightRecorderException {
                return Optional.empty();
            }
        };
    }

    @Override
    public boolean isConnected() {
        // TODO Auto-generated method stub
        return true;
    }

    @Override
    public MBeanMetrics getMBeanMetrics()
            throws ConnectionException, IOException, InstanceNotFoundException,
                    IntrospectionException, ReflectionException {
        if (!isConnected()) {
            connect();
        }

        // TODO: implement http requests to agent to get metrics
        RuntimeMetrics runtime = new RuntimeMetrics(Collections.emptyMap());
        MemoryMetrics memory = new MemoryMetrics(Collections.emptyMap());
        ThreadMetrics threads = new ThreadMetrics(Collections.emptyMap());
        OperatingSystemMetrics os = new OperatingSystemMetrics(Collections.emptyMap());

        return new MBeanMetrics(runtime, memory, threads, os);
    }
}
