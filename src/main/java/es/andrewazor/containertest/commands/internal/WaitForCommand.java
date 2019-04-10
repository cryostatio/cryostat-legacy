package es.andrewazor.containertest.commands.internal;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.containertest.sys.Clock;
import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class WaitForCommand extends AbstractConnectedCommand {

    protected final ClientWriter cw;
    protected final Clock clock;

    @Inject WaitForCommand(ClientWriter cw, Clock clock) {
        this.cw = cw;
        this.clock = clock;
    }

    @Override
    public String getName() {
        return "wait-for";
    }

    /**
     * One arg expected. Given a recording name, this will slowly spinlock on recording completion.
     */
    @Override
    public void execute(String[] args) throws Exception {
        String name = args[0];
        Optional<IRecordingDescriptor> d = getDescriptorByName(name);
        if (!d.isPresent()) {
            cw.println(String.format("Recording with name \"%s\" not found in target JVM", name));
            return;
        }
        IRecordingDescriptor descriptor = d.get();

        if (descriptor.isContinuous() && !descriptor.getState().equals(IRecordingDescriptor.RecordingState.STOPPED)) {
            cw.println(String.format("Recording \"%s\" is continuous, refusing to wait", name));
            return;
        }

        long recordingStart = descriptor.getDataStartTime().longValue();
        long recordingEnd = descriptor.getDataEndTime().longValue();
        long recordingLength = recordingEnd - recordingStart;
        int lastDots = 0;
        boolean progressFlag = false;
        while (!descriptor.getState().equals(IRecordingDescriptor.RecordingState.STOPPED)) {
            long recordingElapsed = getConnection().getApproximateServerTime(clock) - recordingStart;
            double elapsedProportion = ((double) recordingElapsed) / ((double) recordingLength);
            int currentDots = (int) Math.ceil(10 * elapsedProportion);
            if (currentDots > lastDots) {
                for (int i = 0; i < 2 * currentDots; i++) {
                    cw.print('\b');
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < currentDots; i++) {
                    sb.append(". ");
                }
                cw.print(sb.toString().trim());
                lastDots = currentDots;
            } else {
                progressFlag = !progressFlag;
                if (progressFlag) {
                    cw.print('\b');
                } else {
                    cw.print('.');
                }
            }
            clock.sleep(TimeUnit.SECONDS, 1);
            descriptor = getDescriptorByName(name).get();
        }
        cw.println();
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 1) {
            cw.println("Expected one argument");
            return false;
        }

        if (!validateRecordingName(args[0])) {
            cw.println(String.format("%s is an invalid recording name", args[0]));
            return false;
        }

        return true;
    }
}
