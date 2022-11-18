/*
 * MIT License

 * Copyright (c) 2021 Cloudonix.io

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
*/
package io.cryostat.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ForkJoinPool;

import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

/**
 * A conversion utility to help move data from a Java classic blocking IO to a Vert.x asynchronous
 * Stream.
 *
 * <p>Use this class to create an {@link OutputStream} that pushes data written to it to a {@link
 * ReadStream} API.
 *
 * <p>The ReadStream handlers will be called on a Vert.x context, and the {@link #close()} method
 * must be called for the ReadStream end handler to be triggered.
 *
 * <p>It is recommended to use this class in the context of a blocking try-with-resources block, to
 * ensure that streams are closed properly. For example:
 *
 * <p><tt>
 *
 * <pre>
 * try (final OutputToReadStream os = new OutputToReadStream(vertx); final InputStream is = getInput()) {
 *   os.pipeTo(someWriteStream);
 *   is.transferTo(os);
 * }
 * </pre>
 *
 * </tt>
 *
 * @author guss77, hareetd, aazores
 * @source https://github.com/cloudonix/vertx-java.io
 */
public class ActiveRecordingOutputToReadStream extends InputStreamToReadStream {

    private final TargetConnectionManager targetConnectionManager;
    private final ConnectionDescriptor connectionDescriptor;

    public ActiveRecordingOutputToReadStream(
            Vertx vertx,
            TargetConnectionManager targetConnectionManager,
            ConnectionDescriptor connectionDescriptor) {
        super(vertx);
        this.targetConnectionManager = targetConnectionManager;
        this.connectionDescriptor = connectionDescriptor;
    }

    @Override
    public Future<Void> pipeFromInput(InputStream source, WriteStream<Buffer> sink)
            throws IOException {
        Promise<Void> promise = Promise.promise();
        pipeTo(sink, promise);
        ForkJoinPool.commonPool()
                .submit(
                        () -> {
                            try (final InputStream is = source;
                                    final OutputStream os = this) {
                                checkConnection();
                                is.transferTo(os);
                                checkConnection();
                            } catch (IOException e) {
                                promise.tryFail(e);
                            }
                        });
        return promise.future();
    }

    /* OutputStream stuff */

    @Override
    public synchronized void write(int b) throws IOException {
        checkConnection();
        super.write(b);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        checkConnection();
        super.write(b, off, len);
    }

    /* Internal implementation */

    private void checkConnection() throws IOException {
        if (isClosed()) throw new IOException("OutputStream is closed");

        if (!targetConnectionManager.markConnectionInUse(connectionDescriptor)) {
            throw new IOException(
                    "Target connection unexpectedly closed while streaming recording");
        }
    }
}
