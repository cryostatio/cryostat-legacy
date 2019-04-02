package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;

import es.andrewazor.containertest.NetworkResolver;
import es.andrewazor.containertest.commands.Command;
import es.andrewazor.containertest.tui.ClientWriter;

class HostnameCommand implements Command {

    private final ClientWriter cw;
    private final NetworkResolver resolver;

    @Inject HostnameCommand(ClientWriter cw, NetworkResolver resolver) {
        this.cw = cw;
        this.resolver = resolver;
    }

    @Override
    public String getName() {
        return "hostname";
    }

    @Override
    public boolean validate(String[] args) {
        return args.length == 0;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void execute(String[] args) throws Exception {
        cw.println(String.format("\t%s", resolver.getHostName()));
    }
}