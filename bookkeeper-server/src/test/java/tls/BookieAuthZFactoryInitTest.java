package tls;

import org.apache.bookkeeper.conf.ServerConfiguration;
import org.apache.bookkeeper.tls.BookieAuthZFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class BookieAuthZFactoryInitTest {
    private ServerConfiguration conf;
    private final boolean expectedException;
    private final BookieAuthZFactory factory;
    private String role;
    private boolean isMocked;

    public BookieAuthZFactoryInitTest(InitParams initParams) {
        factory = new BookieAuthZFactory();
        assertNotNull(factory, "Factory should not be null after initialization.");
        this.role = initParams.Role();
        this.expectedException = initParams.isExpectedException();
        this.isMocked = initParams.isMocked();
    }

    @Parameterized.Parameters
    public static Collection<InitParams> provideInitParameters() {
        List<InitParams> param = new ArrayList<>();
        //TC1 --> Success
        param.add(new InitParams("test_role1", false, false));
        //TC2 --> Success
        param.add(new InitParams("test_role1,test_role2", false, false));
        //TC3 --> Failure
        param.add(new InitParams("", true, false));
        //TC4 --> Failure (NullPointerException)
        param.add(new InitParams(null ,true, true));
        //TC5 --> Failure
        param.add(new InitParams("", true, true));
        return param;
    }
    @Test
    public void initTest() {
        if(isMocked){
            if(role == null){
                conf = Mockito.mock(ServerConfiguration.class);
                when(conf.getAuthorizedRoles()).thenReturn(null);
            }else{
                conf = Mockito.mock(ServerConfiguration.class);
                when(conf.getAuthorizedRoles()).thenReturn(new String[0]);}
        }else{
            conf = new ServerConfiguration();
            // Regular case
            conf.setAuthorizedRoles(role);
        }
        // Debug output
        System.out.println("Authorized roles: " + Arrays.toString(conf.getAuthorizedRoles()));
        try{
         factory.init(conf);
        }catch (IOException | RuntimeException e){
            Assert.assertTrue("conf is null or empty", expectedException);
        }

    }

    private static final class InitParams {

        private String role;
        private final boolean expectedException;
        private final boolean isMocked;

        private InitParams(String role,
                           boolean expectedException, boolean isMocked) {
            this.role = role;
            this.expectedException = expectedException;
            this.isMocked = isMocked;
        }
        public boolean isMocked(){
            return isMocked;
        }
        public boolean isExpectedException() {
            return expectedException;
        }
        public String Role() {
            return role;
        }
    }
}
