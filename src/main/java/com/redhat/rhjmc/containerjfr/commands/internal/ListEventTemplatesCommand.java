/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
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
package com.redhat.rhjmc.containerjfr.commands.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.FlightRecorderException;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.templates.Template;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;

@Singleton
class ListEventTemplatesCommand extends AbstractConnectedCommand implements SerializableCommand {

    private final ClientWriter cw;

    @Inject
    ListEventTemplatesCommand(ClientWriter cw, TargetConnectionManager targetConnectionManager) {
        super(targetConnectionManager);
        this.cw = cw;
    }

    @Override
    public String getName() {
        return "list-event-templates";
    }

    @Override
    public void execute(String[] args) throws Exception {
        targetConnectionManager.executeConnectedTask(
                args[0],
                connection -> {
                    cw.println("Available recording templates:");
                    getTemplates(connection)
                            .forEach(
                                    template ->
                                            cw.println(
                                                    String.format(
                                                            "\t[%s]\t%s:\t%s",
                                                            template.getProvider(),
                                                            template.getName(),
                                                            template.getDescription())));
                    return null;
                });
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            return targetConnectionManager.executeConnectedTask(
                    args[0],
                    connection -> {
                        return new ListOutput<>(getTemplates(connection));
                    });
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 1 || StringUtils.isBlank(args[0])) {
            cw.println("Expected one argument: hostname:port, ip:port, or JMX service URL");
            return false;
        }
        boolean isValidHostId = validateHostId(args[0]);
        if (!isValidHostId) {
            cw.println(String.format("%s is an invalid connection specifier", args[0]));
        }
        return isValidHostId;
    }

    private List<Template> getTemplates(JFRConnection connection) throws FlightRecorderException {
        List<Template> templates = new ArrayList<>(connection.getTemplateService().getTemplates());
        templates.add(AbstractRecordingCommand.ALL_EVENTS_TEMPLATE);
        return Collections.unmodifiableList(templates);
    }
}
