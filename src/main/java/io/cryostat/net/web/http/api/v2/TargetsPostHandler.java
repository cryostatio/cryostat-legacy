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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.ServiceRef.AnnotationKey;
import io.cryostat.platform.internal.CustomTargetPlatformClient;
import io.cryostat.recordings.JvmIdHelper;
import io.cryostat.recordings.JvmIdHelper.JvmIdGetException;
import io.cryostat.util.URIUtil;

import com.google.gson.Gson;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;

class TargetsPostHandler extends AbstractV2RequestHandler<ServiceRef> {

    static final String PATH = "targets";

    private final DiscoveryStorage storage;
    private final JvmIdHelper jvmIdHelper;
    private final CustomTargetPlatformClient customTargetPlatformClient;
    private final Logger logger;

    @Inject
    TargetsPostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            Gson gson,
            DiscoveryStorage storage,
            JvmIdHelper jvmIdHelper,
            CustomTargetPlatformClient customTargetPlatformClient,
            Logger logger) {
        super(auth, credentialsManager, gson);
        this.storage = storage;
        this.jvmIdHelper = jvmIdHelper;
        this.customTargetPlatformClient = customTargetPlatformClient;
        this.logger = logger;
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
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.CREATE_TARGET);
    }

    @Override
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.JSON);
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
    public List<HttpMimeType> consumes() {
        return List.of(HttpMimeType.MULTIPART_FORM, HttpMimeType.URLENCODED_FORM);
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
            for (ServiceRef serviceRef : storage.listDiscoverableServices()) {
                if (Objects.equals(uri, serviceRef.getServiceUri())) {
                    throw new ApiException(400, "Duplicate connectUrl");
                }
            }

            String username = attrs.get("username");
            String password = attrs.get("password");
            Optional<Credentials> credentials =
                    StringUtils.isBlank(username) || StringUtils.isBlank(password)
                            ? Optional.empty()
                            : Optional.of(new Credentials(username, password));

            MultiMap queries = params.getQueryParams();
            boolean dryRun =
                    StringUtils.isNotBlank(queries.get("dryrun"))
                            && Boolean.TRUE.equals(Boolean.valueOf(queries.get("dryrun")));

            String jvmId = jvmIdHelper.getJvmId(uri.toString(), !dryRun, credentials);
            ServiceRef serviceRef = new ServiceRef(jvmId, uri, alias);

            Map<AnnotationKey, String> cryostatAnnotations = new HashMap<>();
            for (AnnotationKey ak : AnnotationKey.values()) {
                // TODO is there a good way to determine this prefix from the structure of the
                // ServiceRef's serialized form?
                String formKey = "annotations.cryostat." + ak.name();
                if (attrs.contains(formKey)) {
                    cryostatAnnotations.put(ak, attrs.get(formKey));
                }
            }
            cryostatAnnotations.put(AnnotationKey.REALM, CustomTargetPlatformClient.REALM);
            serviceRef.setCryostatAnnotations(cryostatAnnotations);

            if (!dryRun) {
                boolean v = customTargetPlatformClient.addTarget(serviceRef);
                if (!v) {
                    throw new ApiException(400, "Duplicate connectUrl");
                }
            }
            return new IntermediateResponse<ServiceRef>().body(serviceRef);
        } catch (JvmIdGetException e) {
            if (AbstractAuthenticatedRequestHandler.isJmxAuthFailure(e)) {
                throw new ApiException(406, "Credentials Not Acceptable", e);
            }
            if (AbstractAuthenticatedRequestHandler.isUnknownTargetFailure(e)) {
                throw new ApiException(404, "Target Not Found", e);
            }
            if (AbstractAuthenticatedRequestHandler.isJmxSslFailure(e)) {
                throw new ApiException(502, "Target SSL Untrusted", e);
            }
            if (AbstractAuthenticatedRequestHandler.isServiceTypeFailure(e)) {
                throw new ApiException(504, "Non-JMX Port", e);
            }
            throw new ApiException(500, "Internal Error", e);
        } catch (URISyntaxException use) {
            throw new ApiException(400, "Invalid connectUrl", use);
        } catch (IOException ioe) {
            throw new ApiException(500, "Internal Error", ioe);
        }
    }
}
