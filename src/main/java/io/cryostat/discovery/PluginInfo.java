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
package io.cryostat.discovery;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

@Entity
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class PluginInfo {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(unique = false, nullable = false)
    private String realm;

    @Column(unique = true)
    @Convert(converter = UriConverter.class)
    private URI callback;

    @Type(type = "jsonb")
    @Column(nullable = false, columnDefinition = "jsonb")
    private String subtree;

    PluginInfo() {}

    PluginInfo(String realm, URI callback, String subtree) {
        this.realm = Objects.requireNonNull(realm, "realm");
        this.callback = callback;
        this.subtree = Objects.requireNonNull(subtree, "subtree");
    }

    public UUID getId() {
        return id;
    }

    public String getRealm() {
        return realm;
    }

    public URI getCallback() {
        return callback;
    }

    public String getSubtree() {
        return subtree;
    }

    public void setId(UUID id) {
        this.id = Objects.requireNonNull(id);
    }

    public void setRealm(String realm) {
        this.realm = Objects.requireNonNull(realm);
    }

    public void setCallback(URI callback) {
        this.callback = callback;
    }

    public void setSubtree(String subtree) {
        this.subtree = Objects.requireNonNull(subtree);
    }

    @Override
    public int hashCode() {
        return Objects.hash(callback, id, realm, subtree);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        PluginInfo other = (PluginInfo) obj;
        return Objects.equals(callback, other.callback)
                && Objects.equals(id, other.id)
                && Objects.equals(realm, other.realm)
                && Objects.equals(subtree, other.subtree);
    }
}
