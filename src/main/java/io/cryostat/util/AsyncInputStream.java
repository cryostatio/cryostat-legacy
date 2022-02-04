/*
 * Copyright The Cryostat Authors
 *
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
 */
package io.cryostat.util;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.impl.InboundBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Objects;

/**
 * @author stw, antimist
 */
public class AsyncInputStream implements ReadStream<Buffer> {

	public static final int DEFAULT_READ_BUFFER_SIZE = 8192;
	private static final Logger log = LoggerFactory.getLogger(AsyncInputStream.class);

	// Based on the inputStream with the real data
	private final ReadableByteChannel ch;
	private final Vertx vertx;
	private final Context context;

	private boolean closed;
	private boolean readInProgress;

	private Handler<Buffer> dataHandler;
	private Handler<Void> endHandler;
	private Handler<Throwable> exceptionHandler;
	private final InboundBuffer<Buffer> queue;

	private int readBufferSize = DEFAULT_READ_BUFFER_SIZE;
	private long readPos;

	/**
	 * Create a new Async InputStream that can we used with a Pump
	 *
	 * @param in
	 */
	public AsyncInputStream(Vertx vertx, Context context, InputStream in) {
		this.vertx = vertx;
		this.context = context;
		this.ch = Channels.newChannel(in);
		this.queue = new InboundBuffer<>(context, 0);
		queue.handler(buff -> {
			if (buff.length() > 0) {
				handleData(buff);
			} else {
				handleEnd();
			}
		});
		queue.drainHandler(v -> {
			doRead();
		});
	}

	public void close() {
		closeInternal(null);
	}

	public void close(Handler<AsyncResult<Void>> handler) {
		closeInternal(handler);
	}

	/*
	 * (non-Javadoc)
	 * @see io.vertx.core.streams.ReadStream#endHandler(io.vertx.core.Handler)
	 */
	@Override
	public synchronized AsyncInputStream endHandler(Handler<Void> endHandler) {
		check();
		this.endHandler = endHandler;
		return this;
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * io.vertx.core.streams.ReadStream#exceptionHandler(io.vertx.core.Handler)
	 */
	@Override
	public synchronized AsyncInputStream exceptionHandler(Handler<Throwable> exceptionHandler) {
		check();
		this.exceptionHandler = exceptionHandler;
		return this;
	}

	/*
	 * (non-Javadoc)
	 * @see io.vertx.core.streams.ReadStream#handler(io.vertx.core.Handler)
	 */
	@Override
	public synchronized AsyncInputStream handler(Handler<Buffer> handler) {
		check();
		this.dataHandler = handler;
		if (this.dataHandler != null && !this.closed) {
			this.doRead();
		} else {
			queue.clear();
		}
		return this;
	}

	/*
	 * (non-Javadoc)
	 * @see io.vertx.core.streams.ReadStream#pause()
	 */
	@Override
	public synchronized AsyncInputStream pause() {
		check();
		queue.pause();
		return this;
	}

	/*
	 * (non-Javadoc)
	 * @see io.vertx.core.streams.ReadStream#resume()
	 */
	@Override
	public synchronized AsyncInputStream resume() {
		check();
		if (!closed) {
			queue.resume();
		}
		return this;
	}

	@Override
	public ReadStream<Buffer> fetch(long amount) {
		queue.fetch(amount);
		return this;
	}

	private void check() {
		if (this.closed) {
			throw new IllegalStateException("Inputstream is closed");
		}
	}

	private void checkContext() {
		if (!vertx.getOrCreateContext().equals(context)) {
			throw new IllegalStateException("AsyncInputStream must only be used in the context that created it, expected: " + this.context
				+ " actual " + vertx.getOrCreateContext());
		}
	}

	private synchronized void closeInternal(Handler<AsyncResult<Void>> handler) {
		check();
		closed = true;
		doClose(handler);
	}

	private void doClose(Handler<AsyncResult<Void>> handler) {

		try {
			ch.close();
			if (handler != null) {
				this.vertx.runOnContext(v -> handler.handle(Future.succeededFuture()));
			}
		} catch (IOException e) {
			if (handler != null) {
				this.vertx.runOnContext(v -> handler.handle(Future.failedFuture(e)));
			}
		}
	}

	public synchronized AsyncInputStream read(Buffer buffer, int offset, long position, int length,
	                                          Handler<AsyncResult<Buffer>> handler) {
		Objects.requireNonNull(buffer, "buffer");
		Objects.requireNonNull(handler, "handler");
		Arguments.require(offset >= 0, "offset must be >= 0");
		Arguments.require(position >= 0, "position must be >= 0");
		Arguments.require(length >= 0, "length must be >= 0");
		check();
		ByteBuffer bb = ByteBuffer.allocate(length);
		doRead(buffer, offset, bb, position, handler);
		return this;
	}

	private void doRead() {
		check();
		doRead(ByteBuffer.allocate(readBufferSize));
	}

	private synchronized void doRead(ByteBuffer bb) {
		if (!readInProgress) {
			readInProgress = true;
			Buffer buff = Buffer.buffer(readBufferSize);
			doRead(buff, 0, bb, readPos, ar -> {
				if (ar.succeeded()) {
					readInProgress = false;
					Buffer buffer = ar.result();
					readPos += buffer.length();
					// Empty buffer represents end of file
					if (queue.write(buffer) && buffer.length() > 0) {
						doRead(bb);
					}
				} else {
					handleException(ar.cause());
				}
			});
		}
	}

	private void doRead(Buffer writeBuff, int offset, ByteBuffer buff, long position, Handler<AsyncResult<Buffer>> handler) {

		// ReadableByteChannel doesn't have a completion handler, so we wrap it into
		// an executeBlocking and use the future there
		vertx.executeBlocking(future -> {
			try {
				Integer bytesRead = ch.read(buff);
				future.complete(bytesRead);
			} catch (Exception e) {
				log.error(e);
				future.fail(e);
			}

		}, res -> {

			if (res.failed()) {
				context.runOnContext((v) -> handler.handle(Future.failedFuture(res.cause())));
			} else {
				// Do the completed check
				Integer bytesRead = (Integer) res.result();
				if (bytesRead == -1) {
					//End of file
					context.runOnContext((v) -> {
						buff.flip();
						writeBuff.setBytes(offset, buff);
						buff.compact();
						handler.handle(Future.succeededFuture(writeBuff));
					});
				} else if (buff.hasRemaining()) {
					long pos = position;
					pos += bytesRead;
					// resubmit
					doRead(writeBuff, offset, buff, pos, handler);
				} else {
					// It's been fully written

					context.runOnContext((v) -> {
						buff.flip();
						writeBuff.setBytes(offset, buff);
						buff.compact();
						handler.handle(Future.succeededFuture(writeBuff));
					});
				}
			}
		});
	}

	private void handleData(Buffer buff) {
		Handler<Buffer> handler;
		synchronized (this) {
			handler = this.dataHandler;
		}
		if (handler != null) {
			checkContext();
			handler.handle(buff);
		}
	}

	private synchronized void handleEnd() {
		Handler<Void> endHandler;
		synchronized (this) {
			dataHandler = null;
			endHandler = this.endHandler;
		}
		if (endHandler != null) {
			checkContext();
			endHandler.handle(null);
		}
	}

	private void handleException(Throwable t) {
		if (exceptionHandler != null && t instanceof Exception) {
			exceptionHandler.handle(t);
		} else {
			log.error("Unhandled exception", t);

		}
	}

}