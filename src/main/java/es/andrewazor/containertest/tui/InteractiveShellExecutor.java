package es.andrewazor.containertest.tui;

import java.util.NoSuchElementException;

import dagger.Lazy;
import es.andrewazor.containertest.commands.CommandRegistry;
import es.andrewazor.containertest.commands.internal.ExitCommand;

class InteractiveShellExecutor extends AbstractCommandExecutor {

    private boolean running = true;

    InteractiveShellExecutor(ClientReader cr, ClientWriter cw, Lazy<CommandRegistry> commandRegistry) {
        super(cr, cw, commandRegistry);
    }

    @Override
    public void run(String unused) {
        try (cr) {
            String in;
            do {
                cw.print(connected() ? "> " : "- ");
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

    private boolean connected() {
        return this.connection != null;
    }
}
