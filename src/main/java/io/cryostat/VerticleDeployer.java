/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    private final int poolSize;
    private final Logger logger;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "vertx is externally mutable and that's fine")
    public VerticleDeployer(Vertx vertx, int poolSize, Logger logger) {
        this.vertx = vertx;
        this.poolSize = poolSize;
        this.logger = logger;
    }

    public Future deploy(Verticle verticle, boolean worker) {
        String name = verticle.getClass().getName();
        DeploymentOptions deploymentOptions = new DeploymentOptions();
        deploymentOptions.setWorker(worker);
        if (deploymentOptions.isWorker()) {
            deploymentOptions.setWorkerPoolName(name + "-worker");
            deploymentOptions.setWorkerPoolSize(poolSize);
        }
        logger.info(
                "Deploying {} Verticle with options: {}",
                name,
                deploymentOptions.toJson().encodePrettily());
        return vertx.deployVerticle(verticle, deploymentOptions)
                .onSuccess(id -> logger.info("Deployed {} Verticle [{}]", name, id))
                .onFailure(
                        t -> {
                            logger.error("FAILED to deploy {} Verticle", name);
                            t.printStackTrace();
                        });
    }
}
