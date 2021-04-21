/*-
 * #%L
 * Cryostat
 * %%
 * Copyright (C) 2020 - 2021 The Cryostat Authors
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
package io.cryostat.tui;

import javax.inject.Singleton;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

import io.cryostat.ExecutionMode;
import io.cryostat.core.tui.ClientReader;
import io.cryostat.core.tui.ClientWriter;
import io.cryostat.tui.tcp.TcpModule;
import io.cryostat.tui.tty.TtyModule;
import io.cryostat.tui.ws.WsModule;

@Module(includes = {TcpModule.class, TtyModule.class, WsModule.class})
public abstract class TuiModule {
    @Provides
    @Singleton
    static CommandExecutor provideCommandExecutor(
            ExecutionMode mode,
            @ConnectionMode(ExecutionMode.BATCH) Lazy<CommandExecutor> batchExecutor,
            @ConnectionMode(ExecutionMode.INTERACTIVE) Lazy<CommandExecutor> interactiveExecutor,
            @ConnectionMode(ExecutionMode.WEBSOCKET) Lazy<CommandExecutor> webSocketExecutor,
            @ConnectionMode(ExecutionMode.SOCKET) Lazy<CommandExecutor> socketExecutor) {
        switch (mode) {
            case BATCH:
                return batchExecutor.get();
            case INTERACTIVE:
                return interactiveExecutor.get();
            case WEBSOCKET:
                return webSocketExecutor.get();
            case SOCKET:
                return socketExecutor.get();
            default:
                throw new RuntimeException(
                        String.format("Unimplemented execution mode: %s", mode.toString()));
        }
    }

    @Provides
    @Singleton
    static ClientReader provideClientReader(
            ExecutionMode mode,
            @ConnectionMode(ExecutionMode.BATCH) Lazy<ClientReader> batchReader,
            @ConnectionMode(ExecutionMode.INTERACTIVE) Lazy<ClientReader> interactiveReader,
            @ConnectionMode(ExecutionMode.WEBSOCKET) Lazy<ClientReader> webSocketReader,
            @ConnectionMode(ExecutionMode.SOCKET) Lazy<ClientReader> socketReader) {
        switch (mode) {
            case BATCH:
                return batchReader.get();
            case INTERACTIVE:
                return interactiveReader.get();
            case WEBSOCKET:
                return webSocketReader.get();
            case SOCKET:
                return socketReader.get();
            default:
                throw new RuntimeException(
                        String.format("Unimplemented execution mode: %s", mode.toString()));
        }
    }

    @Provides
    @Singleton
    static ClientWriter provideClientWriter(
            ExecutionMode mode,
            @ConnectionMode(ExecutionMode.BATCH) Lazy<ClientWriter> batchWriter,
            @ConnectionMode(ExecutionMode.INTERACTIVE) Lazy<ClientWriter> interactiveWriter,
            @ConnectionMode(ExecutionMode.WEBSOCKET) Lazy<ClientWriter> webSocketWriter,
            @ConnectionMode(ExecutionMode.SOCKET) Lazy<ClientWriter> socketWriter) {
        switch (mode) {
            case BATCH:
                return batchWriter.get();
            case INTERACTIVE:
                return interactiveWriter.get();
            case SOCKET:
                return socketWriter.get();
            case WEBSOCKET:
                return webSocketWriter.get();
            default:
                throw new RuntimeException(
                        String.format("Unimplemented execution mode: %s", mode.toString()));
        }
    }
}
