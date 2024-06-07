package tls.util;

import org.apache.bookkeeper.auth.BookKeeperPrincipal;
import org.apache.bookkeeper.proto.BookieConnectionPeer;
import org.mockito.Mockito;
import tls.BookieAuthZFactoryNewProviderTest;

import javax.security.auth.x500.X500Principal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.util.*;


public class Addr implements BookieConnectionPeer{
    private final EnumSet<BookieAuthZFactoryNewProviderTest.TypeInstance> typeInstances;
    private BookKeeperPrincipal principalBK = new BookKeeperPrincipal("John");

    public Addr(EnumSet<BookieAuthZFactoryNewProviderTest.TypeInstance> typeInstances) {
        this.typeInstances = typeInstances;
    }
    @Override
    public SocketAddress getRemoteAddr() {
        InetAddress ipAddress;

        try {
            ipAddress = getInetAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }


        int port = 3333;
        return new InetSocketAddress(ipAddress, port);
    }
    private InetAddress getInetAddress() throws UnknownHostException {
        if (typeInstances.contains(BookieAuthZFactoryNewProviderTest.TypeInstance.VALID_IP)) {
            return InetAddress.getByName("127.0.0.1");
        } else if (typeInstances.contains(BookieAuthZFactoryNewProviderTest.TypeInstance.WRONG_IP)) {
            return InetAddress.getByName("invalid.local");
        } else {
            throw new IllegalArgumentException("Unknown typeInstance: " + typeInstances);
        }
    }

    @Override
    public Collection<Object> getProtocolPrincipals() {
        List<Object> certs = new ArrayList<>();
        String role;
        if (typeInstances.contains(BookieAuthZFactoryNewProviderTest.TypeInstance.VALID_CERT)) {

            role = buildValidCertificate();

        } else if (typeInstances.contains(BookieAuthZFactoryNewProviderTest.TypeInstance.WRONG_CERT)) {
            role = buildInvalidCertificate();

        }else if(typeInstances.contains(BookieAuthZFactoryNewProviderTest.TypeInstance.EMPTY_CERT)) {
            return Collections.emptyList();
        }else if(typeInstances.contains(BookieAuthZFactoryNewProviderTest.TypeInstance.NO_ROLE_CERT)) {
            role = "";
        }else if (typeInstances.contains(BookieAuthZFactoryNewProviderTest.TypeInstance.FAKE_CERT)) {
            return Arrays.asList(new FakeCertificate());
        }else {
            throw new IllegalArgumentException("Unknown typeInstance: " + typeInstances);
        }
        X500Principal X500Principal = new X500Principal(role);
        X509Certificate certificate = Mockito.mock(X509Certificate.class);
        Mockito.when(certificate.getSubjectX500Principal())
                .thenReturn(X500Principal);
        certs.add(certificate);
        return certs;
    }

    private String buildInvalidCertificate() {
        StringBuilder sb = new StringBuilder();
        sb.append("CN=Unknown");
        sb.append(",");
        sb.append(" OU=0:");
        sb.append("ev1l");
        sb.append(", O=Unknown Corporation");
        return sb.toString();
    }

    private String buildValidCertificate() {
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
        return principalBK;
    }

    @Override
    public void setAuthorizedId(BookKeeperPrincipal principal) {
        principalBK = principal;
    }

    @Override
    public boolean isSecure() {
        return typeInstances.contains(BookieAuthZFactoryNewProviderTest.TypeInstance.SECURE_ADDR);
    }

    public EnumSet<BookieAuthZFactoryNewProviderTest.TypeInstance> getTypeInstances() {
        return typeInstances;
    }
}


