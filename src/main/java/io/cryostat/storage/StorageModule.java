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
package io.cryostat.storage;

import java.util.Properties;

import javax.inject.Singleton;
import javax.naming.ConfigurationException;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import io.cryostat.configuration.Variables;
import io.cryostat.core.sys.Environment;

import dagger.Module;
import dagger.Provides;
import org.apache.commons.lang3.StringUtils;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.hibernate5.encryptor.HibernatePBEEncryptorRegistry;

@Module
public abstract class StorageModule {

    @Provides
    @Singleton
    static EntityManagerFactory provideEntityManagerFactory(Environment env) {
        Properties properties = new Properties();
        properties.put(
                "jakarta.persistence.jdbc.driver",
                env.getEnv(Variables.JDBC_DRIVER, "org.h2.Driver"));
        properties.put(
                "jakarta.persistence.jdbc.url",
                env.getEnv(
                        Variables.JDBC_URL,
                        "jdbc:h2:mem:cryostat;DB_CLOSE_DELAY=-1;INIT=create domain if not exists"
                                + " jsonb as varchar"));
        properties.put("jakarta.persistence.jdbc.user", env.getEnv(Variables.JDBC_USERNAME, "sa"));
        properties.put(
                "jakarta.persistence.jdbc.password", env.getEnv(Variables.JDBC_PASSWORD, ""));
        properties.put(
                "hibernate.dialect",
                env.getEnv(Variables.HIBERNATE_DIALECT, "org.hibernate.dialect.H2Dialect"));
        properties.put("hibernate.hbm2ddl.auto", env.getEnv(Variables.HBM2DDL, "create"));
        if (env.hasEnv(Variables.LOG_QUERIES)) {
            properties.put("hibernate.show_sql", "true");
            properties.put("hibernate.format_sql", "true");
            properties.put("hibernate.use_sql_comments", "true");
        }

        // TODO not directly related, maybe extract
        StandardPBEStringEncryptor strongEncryptor = new StandardPBEStringEncryptor();
        strongEncryptor.setProviderName("BC" /* BouncyCastle */);
        strongEncryptor.setAlgorithm("PBEWITHSHA256AND128BITAES-CBC-BC");
        String pw = env.getEnv(Variables.JMX_CREDENTIALS_DB_PASSWORD);
        if (StringUtils.isBlank(pw)) {
            throw new RuntimeException(
                    new ConfigurationException(
                            String.format(
                                    "Environment variable %s must be set and non-blank",
                                    Variables.JMX_CREDENTIALS_DB_PASSWORD)));
        }
        strongEncryptor.setPassword(pw);
        HibernatePBEEncryptorRegistry registry = HibernatePBEEncryptorRegistry.getInstance();
        registry.registerPBEStringEncryptor("strongHibernateStringEncryptor", strongEncryptor);

        return Persistence.createEntityManagerFactory("io.cryostat", properties);
    }

    @Provides
    static EntityManager provideEntityManager(EntityManagerFactory emf) {
        return emf.createEntityManager();
    }
}
