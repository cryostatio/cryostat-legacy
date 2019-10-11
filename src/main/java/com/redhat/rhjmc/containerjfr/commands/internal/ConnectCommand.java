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
    ConnectCommand(ClientWriter cw, Set<ConnectionListener> connectionListeners, DisconnectCommand disconnect,
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
