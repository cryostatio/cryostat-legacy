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
package com.redhat.rhjmc.containerjfr;

import java.nio.file.Path;
import java.nio.file.Paths;

import javax.inject.Named;
import javax.inject.Singleton;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.redhat.rhjmc.containerjfr.commands.CommandsModule;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.net.web.WebModule;
import com.redhat.rhjmc.containerjfr.platform.PlatformModule;
import com.redhat.rhjmc.containerjfr.sys.SystemModule;
import com.redhat.rhjmc.containerjfr.tui.TuiModule;

import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Module(
        includes = {
            PlatformModule.class,
            WebModule.class,
            SystemModule.class,
            CommandsModule.class,
            TuiModule.class
        })
public abstract class MainModule {
    public static final String RECORDINGS_PATH = "RECORDINGS_PATH";

    @Provides
    @Singleton
    static Logger provideLogger() {
        return Logger.INSTANCE;
    }

    // public since this is useful to use directly in tests
    @Provides
    @Singleton
    public static Gson provideGson() {
        return new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    }

    @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
    @Provides
    @Named(RECORDINGS_PATH)
    static Path provideSavedRecordingsPath() {
        return Paths.get("/", "flightrecordings");
    }
}
