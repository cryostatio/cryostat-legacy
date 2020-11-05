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
package com.redhat.rhjmc.containerjfr.net.web.http.api.v2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Function;

import javax.inject.Inject;
import javax.inject.Named;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.security.CertificateValidator;
import com.redhat.rhjmc.containerjfr.net.web.http.AbstractAuthenticatedRequestHandler;
import com.redhat.rhjmc.containerjfr.net.web.http.api.ApiVersion;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;

class CertificatePostHandler extends AbstractAuthenticatedRequestHandler {

    static final String PATH = "certificates";

    private final Environment env;
    private final FileSystem fs;
    private final Logger logger;

    private static final String TRUSTSTORE_DIR = "SSL_TRUSTSTORE_DIR";

    private Function<File, FileOutputStream> outputStreamFunction;
    private CertificateValidator certValidator;

    @Inject
    CertificatePostHandler(
            AuthManager auth,
            Environment env,
            FileSystem fs,
            Logger logger,
            @Named("OutputStreamFunction") Function<File, FileOutputStream> outputStreamFunction,
            CertificateValidator certValidator) {
        super(auth);
        this.env = env;
        this.fs = fs;
        this.logger = logger;
        this.outputStreamFunction = outputStreamFunction;
        this.certValidator = certValidator;
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
    public boolean isAsync() {
        return false;
    }

    @Override
    public boolean isOrdered() {
        return true;
    }

    @Override
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE")
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        FileUpload cert = null;
        for (FileUpload fu : ctx.fileUploads()) {
            if ("cert".equals(fu.name())) {
                cert = fu;
                break;
            }
        }

        if (cert == null) {
            throw new HttpStatusException(
                    400, "A file named \"cert\" was not included in the request");
        }

        Path certPath = fs.pathOf(cert.uploadedFileName());

        if (!env.hasEnv(TRUSTSTORE_DIR)) {
            throw new HttpStatusException(500, "Truststore directory not set");
        }

        String truststoreDir = env.getEnv(TRUSTSTORE_DIR);
        Path filePath = fs.pathOf(truststoreDir, cert.fileName()).normalize();

        if (fs.exists(filePath)) {
            throw new HttpStatusException(409, filePath.toString() + " Certificate already exists");
        }

        try (InputStream fis = fs.newInputStream(certPath);
                FileOutputStream out = outputStreamFunction.apply(filePath.toFile())) {

            Collection<? extends Certificate> certificates = certValidator.parseCertificates(fis);
            Iterator<? extends Certificate> it = certificates.iterator();
            while (it.hasNext()) {
                Certificate certificate = (Certificate) it.next();
                byte[] buf = certificate.getEncoded();
                out.write(buf);
            }
        }

        ctx.response().end("Saved: " + filePath);
    }
}
