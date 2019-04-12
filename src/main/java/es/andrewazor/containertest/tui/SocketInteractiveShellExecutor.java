package es.andrewazor.containertest.tui;

import dagger.Lazy;
import es.andrewazor.containertest.commands.CommandRegistry;

class SocketInteractiveShellExecutor extends InteractiveShellExecutor {
    SocketInteractiveShellExecutor(ClientReader cr, ClientWriter cw, Lazy<CommandRegistry> commandRegistry) {
        super(cr, cw, commandRegistry);
    }

    @Override
    protected void handleExit() throws Exception {
        cr.close();
    }

}
