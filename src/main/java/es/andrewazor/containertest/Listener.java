package es.andrewazor.containertest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Listener implements Runnable {

    private static final Pattern FIB_PATTERN = Pattern.compile("fib\\s+(\\d+)");
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
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
            ) {
                String inputLine;
                while ((inputLine = br.readLine()) != null) {
                    System.out.println(String.format("MSG: %s", inputLine));
                    if (inputLine.matches(FIB_PATTERN.pattern())) {
                        Matcher m = FIB_PATTERN.matcher(inputLine);
                        m.find();
                        int num = Integer.parseInt(m.group(1));
                        String result = String.format("fib(%d): %d", num, fib(num));
                        System.out.println(result);
                        bw.write(result);
                        bw.newLine();
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private int fib(int num) {
        if (num == 0) {
            return 0;
        }
        if (num == 1) {
            return 1;
        }
        return fib(num-1) + fib(num-2);
    }
}
