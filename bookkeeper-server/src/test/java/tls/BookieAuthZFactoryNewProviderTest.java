package tls;

import org.apache.bookkeeper.auth.AuthCallbacks;
import org.apache.bookkeeper.auth.BookieAuthProvider;
import org.apache.bookkeeper.client.api.BKException;
import org.apache.bookkeeper.conf.ServerConfiguration;
import org.apache.bookkeeper.tls.BookieAuthZFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;
import tls.util.Addr;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@RunWith(Parameterized.class)
public class BookieAuthZFactoryNewProviderTest {
    private Addr addr;
    private ServerConfiguration conf;

    private final AuthCallbacks.GenericCallback<Void> completeCb;
    private final boolean expectedException;
    private final BookieAuthZFactory factory;


    private static int authCode;
    public enum TypeInstance{
        /** VALID INSTANCE MEANS THAT ALL THREE CONDITIONS MUST BE SATISFIED**/
        VALID_IP,
        VALID_CERT,
        SECURE_ADDR,
        /** NOT VALID INSTANCE**/
        WRONG_IP,
        //NOT_SECURE_ADDR, this is not useful
        EMPTY_CERT,
        FAKE_CERT,
        NO_ROLE_CERT,
        WRONG_CERT,

    }
    public BookieAuthZFactoryNewProviderTest(NewProviderParams newProviderParams) {
        factory = new BookieAuthZFactory();
        assertNotNull(factory, "Factory should not be null after initialization.");
        conf = new ServerConfiguration();
        conf.setAuthorizedRoles("pentester");
        this.addr = newProviderParams.getAddr();
        this.completeCb = newProviderParams.getCompleteCb();
        this.expectedException = newProviderParams.isExpectedException();

    }


    private static AuthCallbacks.GenericCallback<Void> mockInvalidCallback() {
        AuthCallbacks.GenericCallback<Void> callback = mock(AuthCallbacks.GenericCallback.class);
        doThrow(new RuntimeException("Callback exception")).when(callback).operationComplete(0, null);
        return callback;
    }
    private static AuthCallbacks.GenericCallback<Void> mockValidCallback(){
        AuthCallbacks.GenericCallback<Void> callback = mock(AuthCallbacks.GenericCallback.class);
        Mockito.doAnswer(invocation -> {
            // Access the arguments passed to the method
            authCode = invocation.getArgument(0);
            return null;
        }).when(callback).operationComplete(any(int.class), any());
        return callback;
    }
    @Parameterized.Parameters
    public static Collection<NewProviderParams> provideReadEntryParameters()  {
        List<NewProviderParams> param = new ArrayList<>();

        // Create personalized instance of BookieConnectionPeer
        Addr validInstance = new Addr(EnumSet.of(TypeInstance.VALID_IP, TypeInstance.VALID_CERT, TypeInstance.SECURE_ADDR));
        Addr wrongIpInstance = new Addr(EnumSet.of(TypeInstance.WRONG_IP, TypeInstance.VALID_CERT, TypeInstance.SECURE_ADDR));
        Addr wrongCertInstance = new Addr(EnumSet.of(TypeInstance.VALID_IP, TypeInstance.WRONG_CERT, TypeInstance.SECURE_ADDR));
        Addr notSecureInstance = new Addr(EnumSet.of(TypeInstance.VALID_IP, TypeInstance.VALID_CERT));
        Addr emptyCertInstance = new Addr(EnumSet.of(TypeInstance.VALID_IP, TypeInstance.EMPTY_CERT, TypeInstance.SECURE_ADDR));
        Addr noRoleCertInstance = new Addr(EnumSet.of(TypeInstance.VALID_IP, TypeInstance.NO_ROLE_CERT, TypeInstance.SECURE_ADDR));
        Addr fakeCertInstance = new Addr(EnumSet.of(TypeInstance.VALID_IP, TypeInstance.FAKE_CERT, TypeInstance.SECURE_ADDR));

        //TC1 --> Success
        param.add(new NewProviderParams(validInstance, mockValidCallback(), false));
        // TC2 --> Failure because of wrong ip
        param.add(new NewProviderParams(wrongIpInstance, mockValidCallback(), true));
        // TC3 --> Failure with invalid address and valid callback
        param.add(new NewProviderParams(validInstance, mockInvalidCallback(), true));
        // TC4 --> Failure for null addr
        param.add(new NewProviderParams(null, mockValidCallback(), true));
        //TC5 --> Failure for null cb
        param.add(new NewProviderParams(validInstance, null, true));
        //JACOCO IMPROVE COVERAGE
        //TC6 --> Failure for wrong cert
        param.add(new NewProviderParams(wrongCertInstance, mockValidCallback(), false));
        //TC7 -->Failure for empty cert
        param.add(new NewProviderParams(emptyCertInstance, mockValidCallback(), false));
        //TC8 -->Failure for empty role in cert
        param.add(new NewProviderParams(noRoleCertInstance, mockValidCallback(), false));
        //TC9 -->Failure for not secure addr
        param.add(new NewProviderParams(notSecureInstance, mockValidCallback(), false));
        //TC10 -->Failure for fake cert
        param.add(new NewProviderParams(fakeCertInstance,mockValidCallback(),false));
        return param;
    }
    @Test
    public void newProviderTest() {
        try {
            factory.init(conf);
        } catch (IOException e) {
            Assert.fail("An exception should not be thrown");
        }
        try{
            BookieAuthProvider provider = factory.newProvider(addr, completeCb);
            provider.onProtocolUpgrade();
            EnumSet<TypeInstance> requiredInstances = EnumSet.of(TypeInstance.VALID_IP, TypeInstance.VALID_CERT, TypeInstance.SECURE_ADDR);
            System.out.println(addr.getTypeInstances());
            if (addr.getTypeInstances().containsAll(requiredInstances)) {
                Assert.assertEquals(BKException.Code.OK, authCode);
                Assert.assertEquals(conf.getAuthorizedRoles()[0], addr.getAuthorizedId().getName());
            }else if (addr.getTypeInstances().contains(TypeInstance.WRONG_IP)) {
                Assertions.assertThrows(RuntimeException.class, () -> {
                    addr.getRemoteAddr();
                });
            }else{
                Assert.assertEquals(BKException.Code.UnauthorizedAccessException, authCode);
            }

        }catch (Exception e){
            e.printStackTrace();
            Assert.assertTrue("The created new provider failed", expectedException);
        }

    }

    private static final class NewProviderParams {
        private Addr addr;
        private AuthCallbacks.GenericCallback<Void> completeCb;
        private final boolean expectedException;

        private NewProviderParams(Addr addr, AuthCallbacks.GenericCallback<Void> completeCb,
                           boolean expectedException) {
            this.addr = addr;
            this.completeCb = completeCb;
            this.expectedException = expectedException;
        }
        public boolean isExpectedException() {
            return expectedException;
        }

        public Addr getAddr() {
            return addr;
        }

        public AuthCallbacks.GenericCallback<Void> getCompleteCb() {
            return completeCb;
        }

    }
}
