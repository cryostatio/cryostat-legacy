package es.andrewazor.containertest.commands;

import dagger.Module;
import es.andrewazor.containertest.commands.internal.CommandsInternalModule;
import es.andrewazor.containertest.commands.internal.ConnectionListenerModule;

@Module(includes = {
    CommandsInternalModule.class,
    ConnectionListenerModule.class,
})
public class CommandsModule { }