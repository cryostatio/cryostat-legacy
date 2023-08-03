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
        properties.put(
                "jakarta.persistence.jdbc.user", env.getEnv(Variables.JDBC_USERNAME, "cryostat"));
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
    @Singleton
    static EntityManager provideEntityManager(EntityManagerFactory emf) {
        return emf.createEntityManager();
    }
}
