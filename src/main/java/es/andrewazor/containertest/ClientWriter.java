package es.andrewazor.containertest;

public interface ClientWriter {
    void print(String s);
    default void print(char c) {
        print(new String(new char[]{c}));
    }
    default void println(String s) {
        print(s + '\n');
    }
    default void println() {
        print("\n");
    }
}