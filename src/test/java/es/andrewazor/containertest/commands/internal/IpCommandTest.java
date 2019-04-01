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
import es.andrewazor.containertest.TestBase;

@ExtendWith(MockitoExtension.class)
class IpCommandTest extends TestBase {

    private IpCommand command;
    @Mock private NetworkResolver resolver;

    @BeforeEach
    void setup() {
        command = new IpCommand(mockClientWriter, resolver);
    }

    @Test
    void shouldBeNamedIp() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("ip"));
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
    void shouldPrintResolverIp() throws Exception {
        when(resolver.getHostAddress()).thenReturn("192.168.2.1");
        command.execute(new String[0]);
        MatcherAssert.assertThat(stdout(), Matchers.equalTo("\t192.168.2.1\n"));
    }

}