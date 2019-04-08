package es.andrewazor.containertest.commands.internal;

import java.util.Optional;
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
        Optional<OptionKey> key = OptionKey.fromOptionName(option);
        key.ifPresent(k -> {
            // TODO Implement a programmatic switch over the OptionKey type, rather than
            // this manual branching
            if (k.equals(OptionKey.MAX_AGE)) {
                // TODO allow specification of unit suffixes (ex 30 is 30s, but also 5m, 1h, 2d,
                // etc)
                customizer.maxAge(Long.parseLong(value));
            } else if (k.equals(OptionKey.MAX_SIZE)) {
                // TODO allow specification of unit suffixes (ex 512k, 4M, etc)
                customizer.maxSize(Long.parseLong(value));
            } else if (k.equals(OptionKey.TO_DISK)) {
                customizer.toDisk(Boolean.parseBoolean(value));
            }
        });
        key.orElseThrow(() -> new UnsupportedOperationException(option));
    }

    private void unsetOption(String option) {
        Optional<OptionKey> key = OptionKey.fromOptionName(option);
        key.ifPresent(customizer::unset);
        key.orElseThrow(() -> new UnsupportedOperationException(option));
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 1) {
            return false;
        }
        String options = args[0];

        Matcher optionsMatcher = OPTIONS_PATTERN.matcher(options);
        boolean optionsMatch = optionsMatcher.find();
        Matcher unsetMatcher = UNSET_PATTERN.matcher(options);
        boolean unsetMatch = unsetMatcher.find();
        if (!optionsMatch && !unsetMatch) {
            cw.println(String.format("%s is an invalid option string", options));
            return false;
        }

        String option = (optionsMatch ? optionsMatcher : unsetMatcher).group(1);
        boolean recognizedOption = OptionKey.fromOptionName(option)
            .isPresent();
        if (!recognizedOption) {
            cw.println(String.format("%s is an unrecognized or unsupported option", option));
            return false;
        }

        return true;
    }

}