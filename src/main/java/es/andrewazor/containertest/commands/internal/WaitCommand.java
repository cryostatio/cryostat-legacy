package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;
import javax.inject.Singleton;

import es.andrewazor.containertest.ClientWriter;
import es.andrewazor.containertest.commands.Command;

@Singleton
class WaitCommand implements Command {

    private final ClientWriter cw;

    @Inject WaitCommand(ClientWriter cw) {
        this.cw = cw;
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
        long startTime = System.currentTimeMillis();
        long currentTime = System.currentTimeMillis();
        long targetTime = startTime + 1000 * seconds;

        while (currentTime < targetTime) {
            cw.print(". ");
            currentTime = System.currentTimeMillis();
            Thread.sleep(1000);
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
