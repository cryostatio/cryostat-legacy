package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;
import javax.inject.Singleton;

import es.andrewazor.containertest.commands.Command;

@Singleton
class WaitCommand implements Command {

    @Inject WaitCommand() { }

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
            System.out.print(". ");
            currentTime = System.currentTimeMillis();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) { }
        }
        System.out.println();
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 1) {
            System.out.println("Expected one argument");
            return false;
        }

        if (!args[0].matches("\\d+")) {
            System.out.println(String.format("%s is an invalid integer", args[0]));
        }

        return true;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

}
