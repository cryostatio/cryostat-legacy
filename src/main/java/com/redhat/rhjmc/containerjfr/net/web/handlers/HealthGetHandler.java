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
package com.redhat.rhjmc.containerjfr.net.web.handlers;

import java.io.IOException;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import com.google.gson.Gson;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.net.web.HttpMimeType;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

class HealthGetHandler implements RequestHandler {

    static final String GRAFANA_DATASOURCE_ENV = "GRAFANA_DATASOURCE_URL";
    static final String GRAFANA_DASHBOARD_ENV = "GRAFANA_DASHBOARD_URL";

    private final Provider<CloseableHttpClient> httpClientProvider;
    private final Environment env;
    private final Gson gson;
    private final Logger logger;

    @Inject
    HealthGetHandler(
            Provider<CloseableHttpClient> httpClientProvider,
            Environment env,
            Gson gson,
            Logger logger) {
        this.httpClientProvider = httpClientProvider;
        this.env = env;
        this.gson = gson;
        this.logger = logger;
    }

    @Override
    public String path() {
        return "/health";
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.GET;
    }

    // try-with-resources generates a "redundant" nullcheck in bytecode
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE")
    @Override
    public void handle(RoutingContext ctx) {
        boolean datasourceAvailable = false;
        boolean dashboardAvailable = false;

        if (this.env.hasEnv(GRAFANA_DATASOURCE_ENV)) {
            try (CloseableHttpClient httpClient = httpClientProvider.get();
                    CloseableHttpResponse response =
                            httpClient.execute(
                                    new HttpGet(this.env.getEnv(GRAFANA_DATASOURCE_ENV))); ) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    datasourceAvailable = true;
                }
            } catch (IOException e) {
                logger.warn(e);
            }
        }

        if (this.env.hasEnv(GRAFANA_DASHBOARD_ENV)) {
            String url = this.env.getEnv(GRAFANA_DASHBOARD_ENV) + "/api/health";
            try (CloseableHttpClient httpClient = httpClientProvider.get();
                    CloseableHttpResponse response = httpClient.execute(new HttpGet(url)); ) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    dashboardAvailable = true;
                }
            } catch (IOException e) {
                logger.warn(e);
            }
        }

        ctx.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime())
                .end(
                        gson.toJson(
                                Map.of(
                                        "dashboardAvailable",
                                        dashboardAvailable,
                                        "datasourceAvailable",
                                        datasourceAvailable)));
    }
}
