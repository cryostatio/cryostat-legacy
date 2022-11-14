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
package io.cryostat.net.openshift;

import java.lang.reflect.Type;

import io.cryostat.net.AuthManager;
import io.cryostat.net.security.SecurityContext;
import io.cryostat.util.PluggableJsonDeserializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dagger.Lazy;

class OpenShiftSecurityContextDeserializer extends PluggableJsonDeserializer<SecurityContext> {

    private final Lazy<AuthManager> auth;
    private final Lazy<String> namespace;

    OpenShiftSecurityContextDeserializer(Lazy<AuthManager> auth, Lazy<String> namespace) {
        super(SecurityContext.class);
        this.auth = auth;
        this.namespace = namespace;
    }

    @Override
    public SecurityContext deserialize(
            JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        if (!(auth.get() instanceof OpenShiftAuthManager)) {
            // FIXME actually deserialize, don't make this assumption
            return SecurityContext.DEFAULT;
        }
        JsonObject obj = json.getAsJsonObject();
        String ns;
        if (obj.has(OpenShiftSecurityContext.KEY_NAMESPACE)) {
            ns = obj.get(OpenShiftSecurityContext.KEY_NAMESPACE).getAsString();
        } else {
            // FIXME don't override with a default like this, the namespace should always be
            // specified somewhere before it was serialized
            ns = namespace.get();
        }
        return new OpenShiftSecurityContext(ns);
    }
}
