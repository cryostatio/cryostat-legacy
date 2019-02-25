package es.andrewazor.containertest.commands;

import java.util.Set;

import dagger.Module;
import dagger.Provides;
import es.andrewazor.containertest.commands.internal.CommandsInternalModule;
import es.andrewazor.containertest.commands.internal.ConnectionListenerModule;

@Module(includes = { CommandsInternalModule.class, ConnectionListenerModule.class })
public class CommandsModule {
    @Provides CommandRegistry provideCommandRegistry(Set<Command> commands) {
        return new CommandRegistry(commands);
    }
}