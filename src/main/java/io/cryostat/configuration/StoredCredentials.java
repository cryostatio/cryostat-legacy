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
package io.cryostat.configuration;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import io.cryostat.core.net.Credentials;

import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.jasypt.hibernate5.type.EncryptedStringType;

@Entity
@TypeDef(
        name = "encryptedString",
        typeClass = EncryptedStringType.class,
        parameters = {
            @Parameter(name = "encryptorRegisteredName", value = "strongHibernateStringEncryptor")
        })
public class StoredCredentials {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(updatable = false)
    private int id;

    @Column(unique = true, nullable = false)
    private String matchExpression;

    @Column(unique = false, nullable = false)
    private String username;

    @Column(unique = false, nullable = false)
    @Type(type = "encryptedString")
    private String password;

    StoredCredentials() {}

    StoredCredentials(int id, String matchExpression, Credentials credentials) {
        this.id = id;
        this.matchExpression = matchExpression;
        this.username = credentials.getUsername();
        this.password = credentials.getPassword();
    }

    StoredCredentials(String matchExpression, Credentials credentials) {
        this(0, matchExpression, credentials);
    }

    public String getMatchExpression() {
        return this.matchExpression;
    }

    public int getId() {
        return id;
    }

    public Credentials getCredentials() {
        return new Credentials(username, password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchExpression, username, password);
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
        StoredCredentials other = (StoredCredentials) obj;
        return Objects.equals(matchExpression, other.matchExpression)
                && Objects.equals(username, other.username)
                && Objects.equals(password, other.password);
    }
}
