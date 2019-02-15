package es.andrewazor.containertest.commands.internal;

import es.andrewazor.containertest.JMCConnection;
import es.andrewazor.containertest.commands.Command;

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
        validateConnection();
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

