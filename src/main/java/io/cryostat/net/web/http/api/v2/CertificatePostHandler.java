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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.Collection;
import java.util.function.Function;

import javax.inject.Inject;
import javax.inject.Named;

import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.CertificateValidator;
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
            Environment env,
            FileSystem fs,
            Gson gson,
            @Named("OutputStreamFunction") Function<File, FileOutputStream> outputStreamFunction,
            CertificateValidator certValidator) {
        super(auth, gson);
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
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public HttpMimeType mimeType() {
        return HttpMimeType.PLAINTEXT;
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
