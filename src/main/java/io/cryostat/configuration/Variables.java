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

public final class Variables {
    private Variables() {}

    // jfr-datasource, cryostat-grafana-dashboard
    public static final String GRAFANA_DATASOURCE_ENV = "GRAFANA_DATASOURCE_URL";
    public static final String GRAFANA_DASHBOARD_ENV = "GRAFANA_DASHBOARD_URL";
    public static final String GRAFANA_DASHBOARD_EXT_ENV = "GRAFANA_DASHBOARD_EXT_URL";

    // report generation
    public static final String REPORT_GENERATOR_ENV = "CRYOSTAT_REPORT_GENERATOR";
    public static final String SUBPROCESS_MAX_HEAP_ENV = "CRYOSTAT_REPORT_GENERATION_MAX_HEAP";
    public static final String ACTIVE_REPORTS_CACHE_EXPIRY_ENV =
            "CRYOSTAT_ACTIVE_REPORTS_CACHE_EXPIRY_SECONDS";
    public static final String ACTIVE_REPORTS_CACHE_REFRESH_ENV =
            "CRYOSTAT_ACTIVE_REPORTS_CACHE_REFRESH_SECONDS";

    // agent configuration
    public static final String PUSH_MAX_FILES_ENV = "CRYOSTAT_PUSH_MAX_FILES";

    // SSL configuration
    public static final String DISABLE_SSL = "CRYOSTAT_DISABLE_SSL";
    public static final String KEYSTORE_PATH_ENV = "KEYSTORE_PATH";
    public static final String KEYSTORE_PASS_ENV = "KEYSTORE_PASS";
    public static final String KEY_PATH_ENV = "KEY_PATH";
    public static final String CERT_PATH_ENV = "CERT_PATH";

    // platform configuration
    public static final String PLATFORM_STRATEGY_ENV_VAR = "CRYOSTAT_PLATFORM";
    public static final String AUTH_MANAGER_ENV_VAR = "CRYOSTAT_AUTH_MANAGER";
    public static final String DISABLE_BUILTIN_DISCOVERY = "CRYOSTAT_DISABLE_BUILTIN_DISCOVERY";
    public static final String DISCOVERY_PING_PERIOD_MS = "CRYOSTAT_DISCOVERY_PING_PERIOD";
    public static final String K8S_NAMESPACES = "CRYOSTAT_K8S_NAMESPACES";
    public static final String VERTX_POOL_SIZE = "CRYOSTAT_VERTX_POOL_SIZE";

    // webserver configuration
    public static final String WEBSERVER_HOST = "CRYOSTAT_WEB_HOST";
    public static final String WEBSERVER_PORT = "CRYOSTAT_WEB_PORT";
    public static final String WEBSERVER_PORT_EXT = "CRYOSTAT_EXT_WEB_PORT";
    public static final String WEBSERVER_SSL_PROXIED = "CRYOSTAT_SSL_PROXIED";
    public static final String WEBSERVER_ALLOW_UNTRUSTED_SSL = "CRYOSTAT_ALLOW_UNTRUSTED_SSL";
    public static final String MAX_CONNECTIONS_ENV_VAR = "CRYOSTAT_MAX_WS_CONNECTIONS";
    public static final String ENABLE_CORS_ENV = "CRYOSTAT_CORS_ORIGIN";
    public static final String HTTP_REQUEST_TIMEOUT = "CRYOSTAT_HTTP_REQUEST_TIMEOUT";
    public static final String DEV_MODE = "CRYOSTAT_DEV_MODE";

    // JMX connections configuration
    public static final String TARGET_MAX_CONCURRENT_CONNECTIONS =
            "CRYOSTAT_TARGET_MAX_CONCURRENT_CONNECTIONS";
    public static final String TARGET_CACHE_TTL = "CRYOSTAT_TARGET_CACHE_TTL";
    public static final String JMX_CONNECTION_TIMEOUT = "CRYOSTAT_JMX_CONNECTION_TIMEOUT_SECONDS";

    // paths configuration
    public static final String ARCHIVE_PATH = "CRYOSTAT_ARCHIVE_PATH";
    public static final String CONFIG_PATH = "CRYOSTAT_CONFIG_PATH";

    // database configuration
    public static final String JDBC_DRIVER = "CRYOSTAT_JDBC_DRIVER";
    public static final String JDBC_URL = "CRYOSTAT_JDBC_URL";
    public static final String JDBC_USERNAME = "CRYOSTAT_JDBC_USERNAME";
    public static final String JDBC_PASSWORD = "CRYOSTAT_JDBC_PASSWORD";
    public static final String JMX_CREDENTIALS_DB_PASSWORD = "CRYOSTAT_JMX_CREDENTIALS_DB_PASSWORD";
    public static final String HIBERNATE_DIALECT = "CRYOSTAT_HIBERNATE_DIALECT";
    public static final String HBM2DDL = "CRYOSTAT_HBM2DDL";
    public static final String LOG_QUERIES = "CRYOSTAT_LOG_DB_QUERIES";
}
