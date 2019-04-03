package es.andrewazor.containertest.commands.internal;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import es.andrewazor.containertest.commands.Command;
import es.andrewazor.containertest.sys.Clock;
import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class WaitCommand implements Command {

    private final ClientWriter cw;
    private final Clock clock;

    @Inject WaitCommand(ClientWriter cw, Clock clock) {
        this.cw = cw;
        this.clock = clock;
    }

    @Override
    public String getName() {
        return "wait";
    }

    /**
     * One arg expected. Given a recording name, this will slowly spinlock on recording completion.
     */
    @Override
    public void execute(String[] args) throws Exception {
        int seconds = Integer.parseInt(args[0]);
        long startTime = clock.getWallTime();
        long currentTime = startTime;
        long targetTime = startTime + TimeUnit.SECONDS.toMillis(seconds);

        while (currentTime < targetTime) {
            cw.print(". ");
            currentTime = clock.getWallTime();
            clock.sleep(TimeUnit.SECONDS, 1);
        }
        cw.println();
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 1) {
            cw.println("Expected one argument");
            return false;
        }

        if (!args[0].matches("\\d+")) {
            cw.println(String.format("%s is an invalid integer", args[0]));
            return false;
        }

        return true;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

}
