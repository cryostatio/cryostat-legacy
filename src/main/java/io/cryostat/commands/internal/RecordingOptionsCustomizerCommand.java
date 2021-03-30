/*-
 * #%L
 * Cryostat
 * %%
 * Copyright (C) 2020 - 2021 Cryostat
 * %%
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * #L%
 */
package io.cryostat.commands.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.cryostat.commands.Command;
import io.cryostat.core.RecordingOptionsCustomizer;
import io.cryostat.core.RecordingOptionsCustomizer.OptionKey;
import io.cryostat.core.tui.ClientWriter;

@Singleton
class RecordingOptionsCustomizerCommand implements Command {

    private static final Pattern OPTIONS_PATTERN =
            Pattern.compile("^([\\w]+)=([\\w\\.-_]+)$", Pattern.MULTILINE);
    private static final Pattern UNSET_PATTERN = Pattern.compile("^-([\\w]+)$", Pattern.MULTILINE);

    private final ClientWriter cw;
    private final RecordingOptionsCustomizer customizer;

    @Inject
    RecordingOptionsCustomizerCommand(ClientWriter cw, RecordingOptionsCustomizer customizer) {
        this.cw = cw;
        this.customizer = customizer;
    }

    @Override
    public String getName() {
        return "recording-option";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Output<?> execute(String[] args) {
        try {
            String options = args[0];

            Matcher optionsMatcher = OPTIONS_PATTERN.matcher(options);
            if (optionsMatcher.find()) {
                String option = optionsMatcher.group(1);
                String value = optionsMatcher.group(2);
                OptionKey.fromOptionName(option).ifPresent(k -> customizer.set(k, value));
                return new SuccessOutput();
            }

            Matcher unsetMatcher = UNSET_PATTERN.matcher(options);
            unsetMatcher.find();
            OptionKey.fromOptionName(unsetMatcher.group(1)).ifPresent(customizer::unset);
            return new SuccessOutput();
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    @Override
    public void validate(String[] args) throws FailedValidationException {
        if (args.length != 1) {
            String errorMessage = "Expected one argument: recording option name";
            cw.println(errorMessage);
            throw new FailedValidationException(errorMessage);
        }

        if (!validateNoNullArgs(args)) {
            String errorMessage = "One or more arguments were null";
            cw.println(errorMessage);
            throw new FailedValidationException(errorMessage);
        }

        String options = args[0];

        Matcher optionsMatcher = OPTIONS_PATTERN.matcher(options);
        boolean optionsMatch = optionsMatcher.find();
        Matcher unsetMatcher = UNSET_PATTERN.matcher(options);
        boolean unsetMatch = unsetMatcher.find();
        if (!optionsMatch && !unsetMatch) {
            String errorMessage = String.format("%s is an invalid option string", options);
            cw.println(errorMessage);
            throw new FailedValidationException(errorMessage);
        }

        String option = (optionsMatch ? optionsMatcher : unsetMatcher).group(1);
        boolean recognizedOption = OptionKey.fromOptionName(option).isPresent();
        if (!recognizedOption) {
            String errorMessage =
                    String.format("%s is an unrecognized or unsupported option", option);
            cw.println(errorMessage);
            throw new FailedValidationException(errorMessage);
        }
    }
}
