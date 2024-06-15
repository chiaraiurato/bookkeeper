package tls;

import org.apache.bookkeeper.auth.AuthCallbacks;
import org.apache.bookkeeper.auth.BookieAuthProvider;
import org.apache.bookkeeper.client.api.BKException;
import org.apache.bookkeeper.conf.ServerConfiguration;
import org.apache.bookkeeper.tls.BookieAuthZFactory;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;
import tls.util.MyAddr;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(Parameterized.class)
public class BookieAuthZFactoryNewProviderTest {
    private final MyAddr myAddr;
    private final ServerConfiguration conf;

    private final AuthCallbacks.GenericCallback<Void> completeCb;
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
        this.myAddr = newProviderParams.getAddr();
        this.completeCb = newProviderParams.getCompleteCb();

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
        MyAddr validInstance = new MyAddr(EnumSet.of(TypeInstance.VALID_IP, TypeInstance.VALID_CERT, TypeInstance.SECURE_ADDR));
        MyAddr wrongIpInstance = new MyAddr(EnumSet.of(TypeInstance.WRONG_IP, TypeInstance.VALID_CERT, TypeInstance.SECURE_ADDR));
        MyAddr wrongCertInstance = new MyAddr(EnumSet.of(TypeInstance.VALID_IP, TypeInstance.WRONG_CERT, TypeInstance.SECURE_ADDR));
        MyAddr notSecureInstance = new MyAddr(EnumSet.of(TypeInstance.VALID_IP, TypeInstance.VALID_CERT));
        MyAddr emptyCertInstance = new MyAddr(EnumSet.of(TypeInstance.VALID_IP, TypeInstance.EMPTY_CERT, TypeInstance.SECURE_ADDR));
        MyAddr noRoleCertInstance = new MyAddr(EnumSet.of(TypeInstance.VALID_IP, TypeInstance.NO_ROLE_CERT, TypeInstance.SECURE_ADDR));
        MyAddr fakeCertInstance = new MyAddr(EnumSet.of(TypeInstance.VALID_IP, TypeInstance.FAKE_CERT, TypeInstance.SECURE_ADDR));

        //TC1 --> Success
        param.add(new NewProviderParams(validInstance, mockValidCallback()));
        // TC2 --> Failure because of wrong ip
        param.add(new NewProviderParams(wrongIpInstance, mockValidCallback()));
        // TC3 --> Failure with invalid address and valid callback
        param.add(new NewProviderParams(validInstance, mockInvalidCallback()));
        // TC4 --> Failure for null addr
        param.add(new NewProviderParams(null, mockValidCallback()));
        //TC5 --> Failure for null cb
        param.add(new NewProviderParams(validInstance, null));
        //JACOCO IMPROVE COVERAGE
        //TC6 --> Failure for wrong cert
        param.add(new NewProviderParams(wrongCertInstance, mockValidCallback()));
        //TC7 -->Failure for empty cert
        param.add(new NewProviderParams(emptyCertInstance, mockValidCallback()));
        //TC8 -->Failure for empty role in cert
        param.add(new NewProviderParams(noRoleCertInstance, mockValidCallback()));
        //TC9 -->Failure for not secure addr
        param.add(new NewProviderParams(notSecureInstance, mockValidCallback()));
        //TC10 -->Failure for fake cert
        param.add(new NewProviderParams(fakeCertInstance,mockValidCallback()));
        //TC11 -->Failure invalid cb && empty
        param.add(new NewProviderParams(emptyCertInstance, mockInvalidCallback()));
        //TC12 -->Failure invalid cb && wrong role
        param.add(new NewProviderParams(wrongCertInstance, mockInvalidCallback()));
        //TC13 -->Failure invalid cb && no role
        param.add(new NewProviderParams(noRoleCertInstance, mockInvalidCallback()));
        return param;
    }
    @Test
    public void newProviderTest() {
        try {
            factory.init(conf);
        } catch (IOException e) {
            Assert.fail("An exception should not be thrown");
        }
        BookieAuthProvider provider = factory.newProvider(myAddr, completeCb);
        if(myAddr == null || completeCb ==null){
            Assertions.assertThrows(NullPointerException.class, provider::onProtocolUpgrade);
        }else {
            provider.onProtocolUpgrade();
            EnumSet<TypeInstance> requiredInstances = EnumSet.of(TypeInstance.VALID_IP, TypeInstance.VALID_CERT, TypeInstance.SECURE_ADDR);
            System.out.println(myAddr.getTypeInstances());
            if (myAddr.getTypeInstances().containsAll(requiredInstances)) {
                Assert.assertEquals(BKException.Code.OK, authCode);
                Assert.assertEquals(conf.getAuthorizedRoles()[0], myAddr.getAuthorizedId().getName());
                Assert.assertNotNull("the authorized id should be not null", myAddr.getAuthorizedId());
            } else if (myAddr.getTypeInstances().contains(TypeInstance.WRONG_IP)) {
                Assertions.assertThrows(RuntimeException.class, myAddr::getRemoteAddr);
            } else {
                verify(completeCb).operationComplete(eq(BKException.Code.UnauthorizedAccessException), isNull());
                Assert.assertEquals(BKException.Code.UnauthorizedAccessException, authCode);
            }
        }


    }

    private static final class NewProviderParams {
        private MyAddr myAddr;
        private AuthCallbacks.GenericCallback<Void> completeCb;

        private NewProviderParams(MyAddr myAddr, AuthCallbacks.GenericCallback<Void> completeCb) {
            this.myAddr = myAddr;
            this.completeCb = completeCb;
        }
        public MyAddr getAddr() {
            return myAddr;
        }

        public AuthCallbacks.GenericCallback<Void> getCompleteCb() {
            return completeCb;
        }

    }
}
