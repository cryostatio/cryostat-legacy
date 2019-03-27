package es.andrewazor.containertest.commands.internal;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import es.andrewazor.containertest.ConnectionListener;
import es.andrewazor.containertest.JMCConnection;
import es.andrewazor.containertest.JMCConnectionToolkit;
import es.andrewazor.containertest.commands.Command;

@Singleton 
class ConnectCommand implements Command {

    private static final Pattern HOST_PATTERN = Pattern.compile("^([^:\\s]+)(?::(\\d{1,5}))?$");

    private final Set<ConnectionListener> connectionListeners;
    private final JMCConnectionToolkit connectionToolkit;

    @Inject
    ConnectCommand(Set<ConnectionListener> connectionListeners, JMCConnectionToolkit connectionToolkit) {
        this.connectionListeners = connectionListeners;
        this.connectionToolkit = connectionToolkit;
    }

    @Override
    public String getName() {
        return "connect";
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 1) {
            return false;
        }
        return HOST_PATTERN.matcher(args[0]).matches();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void execute(String[] args) throws Exception {
        Matcher m = HOST_PATTERN.matcher(args[0]);
        m.find();
        String host = m.group(1);
        String port = m.group(2);
        if (port == null) {
            port = "9091";
        }
        JMCConnection connection = connectionToolkit.connect(host, Integer.parseInt(port));
        connectionListeners.forEach(listener -> listener.connectionChanged(connection));
    }

}