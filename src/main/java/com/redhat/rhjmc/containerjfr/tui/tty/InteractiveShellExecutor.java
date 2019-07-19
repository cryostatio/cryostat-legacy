package com.redhat.rhjmc.containerjfr.tui.tty;

import java.util.NoSuchElementException;

import com.redhat.rhjmc.containerjfr.commands.CommandRegistry;
import com.redhat.rhjmc.containerjfr.commands.internal.ExitCommand;
import com.redhat.rhjmc.containerjfr.core.net.JMCConnection;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.ConnectionListener;
import com.redhat.rhjmc.containerjfr.tui.AbstractCommandExecutor;
import dagger.Lazy;

public class InteractiveShellExecutor extends AbstractCommandExecutor implements ConnectionListener {

    private boolean running = true;
    private boolean connected = false;

    public InteractiveShellExecutor(ClientReader cr, ClientWriter cw, Lazy<CommandRegistry> commandRegistry) {
        super(cr, cw, commandRegistry);
    }

    @Override
    public void connectionChanged(JMCConnection connection) {
        this.connected = connection != null;
    }

    @Override
    public void run(String unused) {
        try (cr) {
            String in;
            do {
                cw.print(connected ? "> " : "- ");
                try {
                    in = cr.readLine();
                    if (in == null) {
                        in = ExitCommand.NAME;
                    }
                } catch (NoSuchElementException e) {
                    in = ExitCommand.NAME;
                }
                handleCommand(in.trim());
            } while (running);
        } catch (Exception e) {
            cw.println(e);
        }
    }

    protected void handleCommand(String cmd) throws Exception {
        if (cmd.toLowerCase().equals(ExitCommand.NAME.toLowerCase())) {
            handleExit();
        }
        executeCommandLine(cmd);
    }

    protected void handleExit() throws Exception {
        running = false;
    }
}
