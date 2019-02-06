package es.andrewazor.containertest.commands.internal;

import java.net.InetAddress;

import es.andrewazor.containertest.JMCConnection;

class IpCommand extends AbstractCommand {
    IpCommand(JMCConnection connection) {
        super(connection);
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
    public void execute(String[] args) throws Exception {
        System.out.println(InetAddress.getLocalHost().getHostAddress());
    }
}