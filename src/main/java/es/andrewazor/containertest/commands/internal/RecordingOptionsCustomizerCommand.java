package es.andrewazor.containertest.commands.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class RecordingOptionsCustomizerCommand extends AbstractConnectedCommand {

    private static final Pattern OPTIONS_PATTERN = Pattern.compile("^([\\w]+)=([\\w]+)$", Pattern.MULTILINE);

    private ClientWriter cw;
    private RecordingOptionsCustomizer customizer;

    @Inject RecordingOptionsCustomizerCommand(ClientWriter cw, RecordingOptionsCustomizer customizer) {
        this.cw = cw;
        this.customizer = customizer;
    }

    @Override
    public String getName() {
        return "recording-option";
    }

    @Override
    public void execute(String[] args) throws Exception {
        String options = args[0];
        Matcher m = OPTIONS_PATTERN.matcher(options);
        m.find();
        String option = m.group(1);
        String value = m.group(2);

        // Implement a programmatic switch over the OptionKey type, rather than this manual branching
        if (option.equals("destinationCompressed")) {
            customizer.destinationCompressed(Boolean.parseBoolean(value));
        } else if (option.equals("maxAge")) {
            customizer.maxAge(Long.parseLong(value));
        } else if (option.equals("maxSize")) {
            customizer.maxSize(Long.parseLong(value));
        } else if (option.equals("toDisk")) {
            customizer.toDisk(Boolean.parseBoolean(value));
        } else {
            throw new UnsupportedOperationException(option);
        }
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 1) {
            return false;
        }
        String options = args[0];

        if (!OPTIONS_PATTERN.matcher(options).find()) {
            cw.println(String.format("%s is an invalid events pattern", options));
            return false;
        }

        return true;
    }

}