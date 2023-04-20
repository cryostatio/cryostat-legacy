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
package io.cryostat;

import io.cryostat.core.log.Logger;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;

public class VerticleDeployer {

    private final Vertx vertx;
    private final Logger logger;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "vertx is externally mutable and that's fine")
    public VerticleDeployer(Vertx vertx, Logger logger) {
        this.vertx = vertx;
        this.logger = logger;
    }

    public Future deploy(Verticle verticle, boolean worker) {
        String name = verticle.getClass().getName();
        logger.info("Deploying {} Verticle", name);
        DeploymentOptions opts = new DeploymentOptions().setWorker(worker);
        if (opts.isWorker()) {
            // TODO make configurable, this is the same as the default
            opts.setWorkerPoolSize(20);
            opts.setWorkerPoolName(verticle.getClass().getSimpleName() + "-worker");
        }
        return vertx.deployVerticle(verticle, opts)
                .onSuccess(id -> logger.info("Deployed {} Verticle [{}]", name, id))
                .onFailure(
                        t -> {
                            logger.error("FAILED to deploy {} Verticle", name);
                            t.printStackTrace();
                        });
    }
}
