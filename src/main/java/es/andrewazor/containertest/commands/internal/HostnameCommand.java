package es.andrewazor.containertest.commands.internal;

import es.andrewazor.containertest.NetworkResolver;
import es.andrewazor.containertest.commands.Command;

class HostnameCommand implements Command {

    @Override
    public String getName() {
        return "hostname";
    }

    @Override
    public boolean validate(String[] args) {
        return args.length == 0;
    }

    @Override
    public void execute(String[] args) throws Exception {
        System.out.println(String.format("\t%s", new NetworkResolver().getHostName()));
    }
}