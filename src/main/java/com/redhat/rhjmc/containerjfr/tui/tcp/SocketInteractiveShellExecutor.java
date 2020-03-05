package com.redhat.rhjmc.containerjfr.tui.tcp;

import com.redhat.rhjmc.containerjfr.commands.CommandRegistry;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.tui.tty.InteractiveShellExecutor;
import dagger.Lazy;

class SocketInteractiveShellExecutor extends InteractiveShellExecutor {
    SocketInteractiveShellExecutor(
            ClientReader cr, ClientWriter cw, Lazy<CommandRegistry> commandRegistry) {
        super(cr, cw, commandRegistry);
    }

    @Override
    protected void handleExit() throws Exception {
        cr.close();
    }
}
