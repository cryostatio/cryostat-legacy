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
package io.cryostat.net.web.http.api.v2;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import javax.inject.Inject;
import javax.inject.Named;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.CertificateValidator;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.FileUpload;

class CertificatePostHandler extends AbstractV2RequestHandler<Path> {

    static final String PATH = "certificates";

    private final Environment env;
    private final FileSystem fs;

    private static final String TRUSTSTORE_DIR = "SSL_TRUSTSTORE_DIR";

    private Function<File, FileOutputStream> outputStreamFunction;
    private CertificateValidator certValidator;

    @Inject
    CertificatePostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            Environment env,
            FileSystem fs,
            Gson gson,
            @Named("OutputStreamFunction") Function<File, FileOutputStream> outputStreamFunction,
            CertificateValidator certValidator) {
        super(auth, credentialsManager, gson);
        this.env = env;
        this.fs = fs;
        this.outputStreamFunction = outputStreamFunction;
        this.certValidator = certValidator;
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
        return EnumSet.of(ResourceAction.CREATE_CERTIFICATE);
    }

    @Override
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.PLAINTEXT);
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
    public IntermediateResponse<Path> handle(RequestParameters params) throws ApiException {
        FileUpload cert = null;
        for (FileUpload fu : params.getFileUploads()) {
            if ("cert".equals(fu.name())) {
                cert = fu;
                break;
            }
        }

        if (cert == null) {
            throw new ApiException(400, "A file named \"cert\" was not included in the request");
        }

        Path certPath = fs.pathOf(cert.uploadedFileName());

        if (!env.hasEnv(TRUSTSTORE_DIR)) {
            throw new ApiException(500, "Truststore directory not set");
        }

        String truststoreDir = env.getEnv(TRUSTSTORE_DIR);
        Path filePath = fs.pathOf(truststoreDir, cert.fileName()).normalize();

        if (fs.exists(filePath)) {
            throw new ApiException(409, filePath.toString() + " Certificate already exists");
        }

        try (InputStream fis = fs.newInputStream(certPath)) {
            Collection<? extends Certificate> certificates = certValidator.parseCertificates(fis);

            if (certificates.isEmpty()) {
                throw new FileNotFoundException("No certificates found");
            }

            try (FileOutputStream out = outputStreamFunction.apply(filePath.toFile())) {

                for (Certificate certificate : certificates) {
                    byte[] buf = certificate.getEncoded();
                    out.write(buf);
                }
            }

        } catch (FileNotFoundException e) {
            throw new ApiException(400, e.getMessage(), e);
        } catch (IOException ioe) {
            throw new ApiException(500, ioe.getMessage(), ioe);
        } catch (CertificateEncodingException cee) {
            throw new ApiException(500, cee.getMessage(), cee);
        } catch (Exception e) {
            throw new ApiException(500, e.getMessage(), e);
        }

        return new IntermediateResponse<Path>().body(filePath);
    }
}
