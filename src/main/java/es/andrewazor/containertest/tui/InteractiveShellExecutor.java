package es.andrewazor.containertest.tui;

import java.util.NoSuchElementException;

import org.apache.commons.lang3.exception.ExceptionUtils;

import dagger.Lazy;
import es.andrewazor.containertest.commands.CommandRegistry;
import es.andrewazor.containertest.commands.internal.ExitCommand;

class InteractiveShellExecutor extends AbstractCommandExecutor {
    InteractiveShellExecutor(ClientReader cr, ClientWriter cw, Lazy<CommandRegistry> commandRegistry) {
        super(cr, cw, commandRegistry);
    }

    @Override
    public void run(String[] args) {
        try (cr) {
            String in;
            do {
                cw.print(connected() ? "> " : "- ");
                try {
                    in = cr.readLine().trim();
                } catch (NoSuchElementException e) {
                    in = ExitCommand.NAME;
                }
                executeCommandLine(in);
            } while (!in.toLowerCase().equals(ExitCommand.NAME.toLowerCase()));
        } catch (Exception e) {
            cw.println(ExceptionUtils.getStackTrace(e));
        }
    }

    private boolean connected() {
        return this.connection != null;
    }
}
