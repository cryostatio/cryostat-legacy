package es.andrewazor.containertest.tui;

import java.util.NoSuchElementException;

import dagger.Lazy;
import es.andrewazor.containertest.commands.CommandRegistry;
import es.andrewazor.containertest.commands.internal.ExitCommand;

class InteractiveShellExecutor extends AbstractCommandExecutor {
    InteractiveShellExecutor(ClientReader cr, ClientWriter cw, Lazy<CommandRegistry> commandRegistry) {
        super(cr, cw, commandRegistry);
    }

    @Override
    public void run(String[] unused) {
        try (cr) {
            String in;
            do {
                cw.print(connected() ? "> " : "- ");
                try {
                    in = cr.readLine();
                    if (in == null) {
                        in = ExitCommand.NAME;
                    }
                    in = in.trim();
                } catch (NoSuchElementException e) {
                    in = ExitCommand.NAME;
                }
                executeCommandLine(in);
            } while (!in.toLowerCase().equals(ExitCommand.NAME.toLowerCase()));
        } catch (Exception e) {
            cw.println(e);
        }
    }

    private boolean connected() {
        return this.connection != null;
    }
}
