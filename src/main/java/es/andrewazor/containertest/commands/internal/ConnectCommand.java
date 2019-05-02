package es.andrewazor.containertest.commands.internal;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import es.andrewazor.containertest.commands.Command;
import es.andrewazor.containertest.net.ConnectionListener;
import es.andrewazor.containertest.net.JMCConnection;
import es.andrewazor.containertest.net.JMCConnectionToolkit;
import es.andrewazor.containertest.tui.ClientWriter;

@Singleton 
class ConnectCommand implements Command {

    private static final Pattern HOST_PATTERN = Pattern.compile("^([^:\\s]+)(?::(\\d{1,5}))?$");

    private final ClientWriter cw;
    private final Set<ConnectionListener> connectionListeners;
    private final DisconnectCommand disconnect;
    private final JMCConnectionToolkit connectionToolkit;

    @Inject
    ConnectCommand(ClientWriter cw, Set<ConnectionListener> connectionListeners, DisconnectCommand disconnect,
            JMCConnectionToolkit connectionToolkit) {
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
        if (args.length != 1) {
            cw.println("Expected one argument: host name/URL");
            return false;
        }
        boolean hostPatternMatch = HOST_PATTERN.matcher(args[0]).matches();
        if (!hostPatternMatch) {
            cw.println(String.format("%s is an invalid host name/URL", args[0]));
        }
        return hostPatternMatch;
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
        this.disconnect.execute(new String[0]);
        JMCConnection connection = connectionToolkit.connect(host, Integer.parseInt(port));
        connectionListeners.forEach(listener -> listener.connectionChanged(connection));
    }

}