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
    public static final String REPORT_STATS_PATH = "CRYOSTAT_REPORT_STATS_PATH";
    public static final String ACTIVE_REPORTS_CACHE_EXPIRY_ENV =
            "CRYOSTAT_ACTIVE_REPORTS_CACHE_EXPIRY_SECONDS";
    public static final String ACTIVE_REPORTS_CACHE_REFRESH_ENV =
            "CRYOSTAT_ACTIVE_REPORTS_CACHE_REFRESH_SECONDS";

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
