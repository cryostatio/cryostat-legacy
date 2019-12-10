package com.redhat.rhjmc.containerjfr.commands;

import com.redhat.rhjmc.containerjfr.commands.internal.CommandsInternalModule;
import com.redhat.rhjmc.containerjfr.commands.internal.ConnectionListenerModule;

import dagger.Module;

@Module(
        includes = {
            CommandsInternalModule.class,
            ConnectionListenerModule.class,
        })
public class CommandsModule {}
