package es.andrewazor.containertest;

import java.io.IOException;
import java.util.Scanner;

public class TtyClientReader implements ClientReader {
    private final Scanner scanner = new Scanner(System.in);

    @Override
    public void close() throws IOException {
        scanner.close();
    }

    @Override
    public String readLine() {
        return scanner.nextLine();
    }
}