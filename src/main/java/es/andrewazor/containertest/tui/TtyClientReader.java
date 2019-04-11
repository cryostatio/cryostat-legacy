package es.andrewazor.containertest.tui;

import java.io.IOException;
import java.util.Scanner;

class TtyClientReader implements ClientReader {
    private final Scanner scanner = new Scanner(System.in, "utf-8");

    @Override
    public void close() throws IOException {
        scanner.close();
    }

    @Override
    public String readLine() {
        return scanner.nextLine();
    }
}