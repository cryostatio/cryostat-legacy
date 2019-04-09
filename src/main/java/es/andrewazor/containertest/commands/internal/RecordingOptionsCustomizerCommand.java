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
        unsetMatcher.find();
        unsetOption(unsetMatcher.group(1));
    }

    private void setOption(String option, String value) {
        Optional<OptionKey> key = OptionKey.fromOptionName(option);
        key.ifPresent(k -> customizer.set(k, value));
    }

    private void unsetOption(String option) {
        Optional<OptionKey> key = OptionKey.fromOptionName(option);
        key.ifPresent(customizer::unset);
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