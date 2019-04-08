package es.andrewazor.containertest.commands.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import es.andrewazor.containertest.commands.internal.RecordingOptionsCustomizer.OptionKey;
import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class RecordingOptionsCustomizerCommand extends AbstractConnectedCommand {

    private static final Pattern OPTIONS_PATTERN = Pattern.compile("^([\\w]+)=([\\w\\.-_]+)$", Pattern.MULTILINE);
    private static final Pattern UNSET_PATTERN = Pattern.compile("^-([\\w]+)$", Pattern.MULTILINE);

    private final ClientWriter cw;
    private final RecordingOptionsCustomizer customizer;

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

        Matcher optionsMatcher = OPTIONS_PATTERN.matcher(options);
        if (optionsMatcher.find()) {
            String option = optionsMatcher.group(1);
            String value = optionsMatcher.group(2);
            setOption(option, value);
            return;
        }

        Matcher unsetMatcher = UNSET_PATTERN.matcher(options);
        if (unsetMatcher.find()) {
            unsetOption(unsetMatcher.group(1));
        }
    }

    private void setOption(String option, String value) {
        // TODO Implement a programmatic switch over the OptionKey type, rather than this manual branching
        if (option.equals("destinationCompressed")) {
            customizer.destinationCompressed(Boolean.parseBoolean(value));
        } else if (option.equals("maxAge")) {
            // TODO allow specification of unit suffixes (ex 30 is 30s, but also 5m, 1h, 2d, etc)
            customizer.maxAge(Long.parseLong(value));
        } else if (option.equals("maxSize")) {
            // TODO allow specification of unit suffixes (ex 512k, 4M, etc)
            customizer.maxSize(Long.parseLong(value));
        } else if (option.equals("toDisk")) {
            customizer.toDisk(Boolean.parseBoolean(value));
        } else if (option.equals("destinationFile")) {
            // TODO validation of file path
            customizer.destinationFile(value);
        } else {
            throw new UnsupportedOperationException(option);
        }
    }

    private void unsetOption(String option) {
        // TODO Implement a programmatic switch over the OptionKey type, rather than this manual branching
        if (option.equals("destinationCompressed")) {
            customizer.unset(OptionKey.DESTINATION_COMPRESSED);
        } else if (option.equals("maxAge")) {
            // TODO allow specification of unit suffixes (ex 30 is 30s, but also 5m, 1h, 2d, etc)
            customizer.unset(OptionKey.MAX_AGE);
        } else if (option.equals("maxSize")) {
            // TODO allow specification of unit suffixes (ex 512k, 4M, etc)
            customizer.unset(OptionKey.MAX_SIZE);
        } else if (option.equals("toDisk")) {
            customizer.unset(OptionKey.TO_DISK);
        } else if (option.equals("destinationFile")) {
            // TODO validation of file path
            customizer.unset(OptionKey.DESTINATION_FILE);
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

        // TODO validation should check known OptionKeys
        if (!OPTIONS_PATTERN.matcher(options).find() && !UNSET_PATTERN.matcher(options).find()) {
            cw.println(String.format("%s is an invalid option string", options));
            return false;
        }

        return true;
    }

}