import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

class Listener implements Runnable {

    private ServerSocket ss = null;
    public static void main(String[] args) {
        new Thread(new Listener()).start();
    }

    @Override
    public void run() {
        try {
            ss = new ServerSocket(9090);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        while (true) {
            try (
                Socket s = ss.accept();
                BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
            ) {
                String inputLine;
                while ((inputLine = br.readLine()) != null) {
                    System.out.println(String.format("MSG: %s", inputLine));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
