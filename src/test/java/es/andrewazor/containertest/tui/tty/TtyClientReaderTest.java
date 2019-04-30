package es.andrewazor.containertest.tui.tty;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import es.andrewazor.containertest.tui.tty.TtyClientReader;

@ExtendWith(MockitoExtension.class)
class TtyClientReaderTest {

    TtyClientReader clientReader;
    InputStream origIn;
    InputStream is;

    @BeforeEach
    void setup() {
        origIn = System.in;
        is = spy(new ByteArrayInputStream(new String("foo\n").getBytes()));
        System.setIn(is);

        clientReader = new TtyClientReader();
    }

    @AfterEach
    void teardown() {
        System.setIn(origIn);
    }

    @Test
    void testClose() throws IOException {
        verifyZeroInteractions(is);
        clientReader.close();
        verify(is).close();
        verifyNoMoreInteractions(is);
    }

    @Test
    void testReadLine() {
        String res = clientReader.readLine();
        MatcherAssert.assertThat(res, Matchers.equalTo("foo"));
    }

}