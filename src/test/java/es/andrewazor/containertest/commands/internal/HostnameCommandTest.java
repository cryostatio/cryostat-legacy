package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.andrewazor.containertest.NetworkResolver;
import es.andrewazor.containertest.StdoutTest;

@ExtendWith(MockitoExtension.class)
class HostnameCommandTest extends StdoutTest {

    private HostnameCommand command;
    @Mock private NetworkResolver resolver;

    @BeforeEach
    void setup() {
        command = new HostnameCommand(resolver);
    }

    @Test
    void shouldBeNamedHostname() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("hostname"));
    }

    @Test
    void shouldExpectNoArgs() {
        assertTrue(command.validate(new String[0]));
    }

    @Test
    void shouldNotExpectArgs() {
        assertFalse(command.validate(new String[1]));
    }

    @Test
    void shouldBeAvailable() {
        assertTrue(command.isAvailable());
    }

    @Test
    void shouldPrintResolverHostname() throws Exception {
        when(resolver.getHostName()).thenReturn("foo-host");
        command.execute(new String[0]);
        MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo("\tfoo-host\n"));
    }

}