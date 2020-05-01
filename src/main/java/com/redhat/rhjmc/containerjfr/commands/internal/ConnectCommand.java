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

import java.net.MalformedURLException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.management.remote.JMXServiceURL;

import org.apache.commons.lang3.StringUtils;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnectionToolkit;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.ConnectionListener;

@Singleton
class ConnectCommand implements SerializableCommand {

    private static final Pattern HOST_PATTERN = Pattern.compile("^([^:\\s]+)(?::(\\d{1,5}))?$");

    private final ClientWriter cw;
    private final Set<ConnectionListener> connectionListeners;
    private final DisconnectCommand disconnect;
    private final JFRConnectionToolkit connectionToolkit;

    @Inject
    ConnectCommand(
            ClientWriter cw,
            Set<ConnectionListener> connectionListeners,
            DisconnectCommand disconnect,
            JFRConnectionToolkit connectionToolkit) {
        this.cw = cw;
        this.connectionListeners = connectionListeners;
        this.disconnect = disconnect;
        this.connectionToolkit = connectionToolkit;
    }

    @Override
    public String getName() {
        return "connect";
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 1 || StringUtils.isBlank(args[0])) {
            cw.println("Expected one argument: hostname:port, ip:port, or JMX service URL");
            return false;
        }
        boolean jmxServiceUrlMatch = true;
        try {
            new JMXServiceURL(args[0]);
        } catch (MalformedURLException e) {
            jmxServiceUrlMatch = false;
        }
        boolean hostPatternMatch = HOST_PATTERN.matcher(args[0]).matches();
        if (!jmxServiceUrlMatch && !hostPatternMatch) {
            cw.println(String.format("%s is an invalid connection specifier", args[0]));
        }
        return jmxServiceUrlMatch || hostPatternMatch;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void execute(String[] args) throws Exception {
        JFRConnection connection;

        connection = attemptConnectAsHostPortPair(args[0]);
        if (connection == null) {
            connection = attemptConnectAsJMXServiceURL(args[0]);
        }

        if (connection != null) {
            final JFRConnection fc = connection;
            connectionListeners.forEach(listener -> listener.connectionChanged(fc));
        }
    }

    private JFRConnection attemptConnectAsJMXServiceURL(String url) throws Exception {
        return connectionToolkit.connect(new JMXServiceURL(url));
    }

    private JFRConnection attemptConnectAsHostPortPair(String s) throws Exception {
        Matcher m = HOST_PATTERN.matcher(s);
        if (!m.find()) {
            return null;
        }
        String host = m.group(1);
        String port = m.group(2);
        if (port == null) {
            port = "9091";
        }
        this.disconnect.execute(new String[0]);
        return connectionToolkit.connect(host, Integer.parseInt(port));
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            execute(args);
            return new StringOutput(args[0]);
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }
}
