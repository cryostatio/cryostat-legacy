package itest;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;

public class IntegrationTestUtils {

    public static final int WEB_PORT;

    static {
        WEB_PORT = Integer.valueOf(System.getProperty("containerJfrWebPort"));
    }

    public static CloseableHttpClient createHttpClient() {
        return HttpClients.createMinimal(new BasicHttpClientConnectionManager());
    }
}
