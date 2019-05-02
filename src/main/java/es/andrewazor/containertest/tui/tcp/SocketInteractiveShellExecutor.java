package es.andrewazor.containertest.tui.tcp;

import dagger.Lazy;
import es.andrewazor.containertest.commands.CommandRegistry;
import es.andrewazor.containertest.tui.ClientReader;
import es.andrewazor.containertest.tui.ClientWriter;
import es.andrewazor.containertest.tui.tty.InteractiveShellExecutor;

class SocketInteractiveShellExecutor extends InteractiveShellExecutor {
    SocketInteractiveShellExecutor(ClientReader cr, ClientWriter cw, Lazy<CommandRegistry> commandRegistry) {
        super(cr, cw, commandRegistry);
    }

    @Override
    protected void handleExit() throws Exception {
        cr.close();
    }
}
