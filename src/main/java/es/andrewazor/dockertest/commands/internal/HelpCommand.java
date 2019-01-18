package es.andrewazor.dockertest.commands.internal;

import es.andrewazor.dockertest.JMCConnection;
import es.andrewazor.dockertest.commands.Command;

class HelpCommand extends AbstractCommand {
    HelpCommand(JMCConnection connection) {
        super(connection);
    }

    @Override
    public String getName() {
        return "help";
    }

    /**
     * No args expected.
     */
    @Override
    public void execute(String[] args) throws Exception {
        System.out.println("Available commands:");
        for (Class<? extends Command> klazz : CommandRegistryImpl.COMMANDS) {
            Command instance = (Command) klazz.getDeclaredConstructor(JMCConnection.class).newInstance(connection);
            System.out.println(String.format("\t%s", instance.getName()));
        }
    }

    @Override
    public boolean validate(String[] args) {
        return true;
    }
}

