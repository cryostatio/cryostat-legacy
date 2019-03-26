package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;

import es.andrewazor.containertest.NetworkResolver;
import es.andrewazor.containertest.commands.Command;

class IpCommand implements Command {

    private final NetworkResolver resolver;

    @Inject IpCommand(NetworkResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public String getName() {
        return "ip";
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
        System.out.println(String.format("\t%s", resolver.getHostAddress()));
    }
}