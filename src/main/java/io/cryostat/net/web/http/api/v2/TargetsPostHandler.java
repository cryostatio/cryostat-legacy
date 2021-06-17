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
package io.cryostat.net.web.http.api.v2;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

import javax.inject.Inject;

import io.cryostat.net.AuthManager;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.internal.CustomTargetPlatformClient;
import io.cryostat.util.URIUtil;

import com.google.gson.Gson;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;

class TargetsPostHandler extends AbstractV2RequestHandler<ServiceRef> {

    static final String PATH = "targets";

    private final PlatformClient platformClient;
    private final CustomTargetPlatformClient customTargetPlatformClient;

    @Inject
    TargetsPostHandler(
            AuthManager auth,
            Gson gson,
            PlatformClient platformClient,
            CustomTargetPlatformClient customTargetPlatformClient) {
        super(auth, gson);
        this.platformClient = platformClient;
        this.customTargetPlatformClient = customTargetPlatformClient;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.POST;
    }

    @Override
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public HttpMimeType mimeType() {
        return HttpMimeType.JSON;
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public boolean isOrdered() {
        return true;
    }

    @Override
    public IntermediateResponse<ServiceRef> handle(RequestParameters params) throws ApiException {
        try {
            MultiMap attrs = params.getFormAttributes();
            String connectUrl = attrs.get("connectUrl");
            if (StringUtils.isBlank(connectUrl)) {
                throw new ApiException(400, "\"connectUrl\" form parameter must be provided");
            }
            String alias = attrs.get("alias");
            if (StringUtils.isBlank(alias)) {
                throw new ApiException(400, "\"alias\" form parameter must be provided");
            }
            URI uri = URIUtil.createAbsolute(connectUrl);
            for (ServiceRef serviceRef : platformClient.listDiscoverableServices()) {
                if (Objects.equals(uri, serviceRef.getServiceUri())) {
                    throw new ApiException(400, "Duplicate connectUrl");
                }
            }
            // TODO form should allow client to populate various ServiceRef.AnnotationKey properties
            ServiceRef serviceRef = new ServiceRef(uri, alias);
            boolean v = customTargetPlatformClient.addTarget(serviceRef);
            if (!v) {
                throw new ApiException(400, "Duplicate connectUrl");
            }
            return new IntermediateResponse<ServiceRef>().body(serviceRef);
        } catch (URISyntaxException use) {
            throw new ApiException(400, "Invalid connectUrl", use);
        } catch (IOException ioe) {
            throw new ApiException(500, "Internal Error", ioe);
        }
    }
}
