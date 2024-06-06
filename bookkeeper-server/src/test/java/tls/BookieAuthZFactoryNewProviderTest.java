package tls;

import org.apache.bookkeeper.auth.AuthCallbacks;
import org.apache.bookkeeper.auth.BookKeeperPrincipal;
import org.apache.bookkeeper.conf.ServerConfiguration;
import org.apache.bookkeeper.proto.BookieConnectionPeer;
import org.apache.bookkeeper.tls.BookieAuthZFactory;
import org.assertj.core.util.Arrays;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@RunWith(Parameterized.class)
public class BookieAuthZFactoryNewProviderTest {
    private BookieConnectionPeer addr;
    private ServerConfiguration conf;

    private final AuthCallbacks.GenericCallback<Void> completeCb;
    private final boolean expectedException;
    private final BookieAuthZFactory factory;

    public BookieAuthZFactoryNewProviderTest(NewProviderParams newProviderParams) {
        factory = new BookieAuthZFactory();
        assertNotNull(factory, "Factory should not be null after initialization.");
        conf = new ServerConfiguration();
        conf.setAuthorizedRoles("pentester");
        this.addr = newProviderParams.getAddr();
        this.completeCb = newProviderParams.getCompleteCb();
        this.expectedException = newProviderParams.isExpectedException();

    }
    public static class ValidAddr implements BookieConnectionPeer {

        @Override
        public SocketAddress getRemoteAddr() {
            InetAddress ipAddress;
            try {
                ipAddress = InetAddress.getByName("127.0.0.1");
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }

            int port = 3333;
            SocketAddress socketAddress = new InetSocketAddress(ipAddress, port);
            return socketAddress;
        }

        @Override
        public Collection<Object> getProtocolPrincipals() {
            String role = buildCertificate();
            X500Principal X500Principal = new X500Principal(role);
//            X509Certificate certificate = Mockito.mock(X509Certificate.class);
//            certificate.getSubjectX500Principal();
            return Arrays.asList(X500Principal);
        }

        private String buildCertificate() {
            StringBuilder sb = new StringBuilder();
            sb.append("CN=A test for our company");
            sb.append(",");
            sb.append(" OU=0:");
            sb.append("pentester");
            sb.append(", O=ACME Corporation");
            return sb.toString();
        }

        @Override
        public void disconnect() {
        }

        @Override
        public BookKeeperPrincipal getAuthorizedId() {
            return null;
        }

        @Override
        public void setAuthorizedId(BookKeeperPrincipal principal) {

        }

        @Override
        public boolean isSecure() {
            return true;
        }
    }
    public static class InvalidAddr implements BookieConnectionPeer {
        @Override
        public SocketAddress getRemoteAddr() {
            return null; // Assume invalid address
        }

        @Override
        public Collection<Object> getProtocolPrincipals() {
            return Arrays.asList("invalid_role");
        }

        @Override
        public void disconnect() {}

        @Override
        public BookKeeperPrincipal getAuthorizedId() {
            return null;
        }

        @Override
        public void setAuthorizedId(BookKeeperPrincipal principal) {}

        @Override
        public boolean isSecure() {
            return false;
        }
    }
    private static AuthCallbacks.GenericCallback<Void> mockCallbackWithException() {
        AuthCallbacks.GenericCallback<Void> callback = mock(AuthCallbacks.GenericCallback.class);
        doThrow(new RuntimeException("Callback exception")).when(callback).operationComplete(0, null);
        return callback;
    }
    @Parameterized.Parameters
    public static Collection<NewProviderParams> provideReadEntryParameters() {
        List<NewProviderParams> param = new ArrayList<>();
        //TC1 --> Success
        param.add(new NewProviderParams(new ValidAddr(), (rc, result) -> {}, false));
        // TC2 --> Failure with valid address and invalid callback
//        param.add(new NewProviderParams(new ValidAddr(), mockCallbackWithException(), true));
//        // TC3 --> Failure with invalid address and valid callback
//        param.add(new NewProviderParams(new InvalidAddr(), (rc, result) -> {}, true));
//        // TC4 --> Failure with invalid address and invalid callback
//        param.add(new NewProviderParams(new InvalidAddr(), mockCallbackWithException(), true));
        return param;
    }
    @Test
    public void initTest() {
        try {
            factory.init(conf);
        } catch (IOException e) {
            Assert.fail("An exception should not be thrown");
        }
        try{
            factory.newProvider(addr, completeCb);
        }catch (RuntimeException e){
            Assert.assertTrue("new provider failed", expectedException);
        }

    }

    private static final class NewProviderParams {
        private BookieConnectionPeer addr;
        private AuthCallbacks.GenericCallback<Void> completeCb;
        private final boolean expectedException;

        private NewProviderParams(BookieConnectionPeer addr, AuthCallbacks.GenericCallback<Void> completeCb,
                           boolean expectedException) {
            this.addr = addr;
            this.completeCb = completeCb;
            this.expectedException = expectedException;
        }
        public boolean isExpectedException() {
            return expectedException;
        }

        public BookieConnectionPeer getAddr() {
            return addr;
        }

        public AuthCallbacks.GenericCallback<Void> getCompleteCb() {
            return completeCb;
        }

    }
}
