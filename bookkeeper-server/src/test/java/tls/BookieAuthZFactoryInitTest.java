package tls;

import org.apache.bookkeeper.conf.ServerConfiguration;
import org.apache.bookkeeper.tls.BookieAuthZFactory;
import org.junit.Assert;
import org.junit.Test;
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
    String role;

    public BookieAuthZFactoryInitTest(InitParams initParams) {
        factory = new BookieAuthZFactory();
        assertNotNull(factory, "Factory should not be null after initialization.");
        this.role = initParams.Role();
        this.expectedException = initParams.isExpectedException();
    }

    @Parameterized.Parameters
    public static Collection<InitParams> provideReadEntryParameters() {
        List<InitParams> param = new ArrayList<>();
        //TC1 --> Success
        param.add(new InitParams("test_role1", false));
        //TC2 --> Success
        param.add(new InitParams("test_role1,test_role2", false));
        //TC3 --> Failure
        param.add(new InitParams("", true));
        //TC4 --> Failure (NullPointerException)
        param.add(new InitParams(null ,true));
        return param;
    }
    @Test
    public void initTest() {

        if (role == null && expectedException) {
            // Special case: mock ServerConfiguration to return null for getAuthorizedRoles()
            conf = Mockito.mock(ServerConfiguration.class);
            when(conf.getAuthorizedRoles()).thenReturn(null);
        } else {
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

        private InitParams(String role,
                           boolean expectedException) {
            this.role = role;
            this.expectedException = expectedException;
        }
        public boolean isExpectedException() {
            return expectedException;
        }
        public String Role() {
            return role;
        }
    }
}
