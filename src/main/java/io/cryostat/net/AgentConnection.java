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
package io.cryostat.net;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.ReflectionException;
import javax.management.remote.JMXServiceURL;

import org.openjdk.jmc.rjmx.ConnectionException;
import org.openjdk.jmc.rjmx.IConnectionHandle;
import org.openjdk.jmc.rjmx.ServiceNotAvailableException;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.CryostatFlightRecorderService;
import io.cryostat.core.net.IDException;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.net.MBeanMetrics;
import io.cryostat.core.sys.Clock;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.core.templates.MergedTemplateService;
import io.cryostat.core.templates.TemplateService;
import io.cryostat.recordings.JvmIdHelper;

import org.apache.commons.lang3.exception.ExceptionUtils;

public class AgentConnection implements JFRConnection {

    private final AgentClient client;
    private final JvmIdHelper idHelper;
    private final FileSystem fs;
    private final Environment env;
    private final Logger logger;

    AgentConnection(
            AgentClient client,
            JvmIdHelper idHelper,
            FileSystem fs,
            Environment env,
            Logger logger) {
        this.client = client;
        this.idHelper = idHelper;
        this.fs = fs;
        this.env = env;
        this.logger = logger;
    }

    @Override
    public void close() throws Exception {}

    @Override
    public void connect() throws ConnectionException {
        try {
            CompletableFuture<Boolean> f = client.ping().toCompletionStage().toCompletableFuture();
            Boolean resp = f.get();
            if (!Boolean.TRUE.equals(resp)) {
                throw new ConnectionException("Connection failed");
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new ConnectionException(ExceptionUtils.getMessage(e));
        }
    }

    @Override
    public void disconnect() {}

    public URI getUri() {
        return client.getUri();
    }

    @Override
    public long getApproximateServerTime(Clock clock) {
        return clock.now().toEpochMilli();
    }

    @Override
    public IConnectionHandle getHandle() throws ConnectionException, IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getHost() {
        return getUri().getHost();
    }

    @Override
    public JMXServiceURL getJMXURL() throws IOException {
        if (getUri().getScheme().startsWith("http")) {
            throw new UnsupportedOperationException();
        }
        return new JMXServiceURL(getUri().toString());
    }

    @Override
    public String getJvmId() throws IDException, IOException {
        // this should have already been populated when the agent published itself to the Discovery
        // API. If not, then this will fail, but we were in a bad state to begin with.
        return idHelper.getJvmId(getUri().toString());
    }

    @Override
    public int getPort() {
        return getUri().getPort();
    }

    @Override
    public CryostatFlightRecorderService getService()
            throws ConnectionException, IOException, ServiceNotAvailableException {
        return new AgentJFRService(client, (MergedTemplateService) getTemplateService(), logger);
    }

    @Override
    public TemplateService getTemplateService() {
        return new MergedTemplateService(this, fs, env);
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public MBeanMetrics getMBeanMetrics()
            throws ConnectionException,
                    IOException,
                    InstanceNotFoundException,
                    IntrospectionException,
                    ReflectionException {
        try {
            return client.mbeanMetrics().toCompletionStage().toCompletableFuture().get();
        } catch (ExecutionException | InterruptedException e) {
            throw new IOException(e);
        }
    }

    public static class Factory {
        private final AgentClient.Factory clientFactory;
        private final JvmIdHelper idHelper;
        private final FileSystem fs;
        private final Environment env;
        private final Logger logger;

        Factory(
                AgentClient.Factory clientFactory,
                JvmIdHelper idHelper,
                FileSystem fs,
                Environment env,
                Logger logger) {
            this.clientFactory = clientFactory;
            this.idHelper = idHelper;
            this.fs = fs;
            this.env = env;
            this.logger = logger;
        }

        AgentConnection createConnection(URI agentUri) {
            return new AgentConnection(clientFactory.create(agentUri), idHelper, fs, env, logger);
        }
    }
}
