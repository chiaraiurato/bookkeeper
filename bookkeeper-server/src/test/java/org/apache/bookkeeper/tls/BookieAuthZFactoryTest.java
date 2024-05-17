package org.apache.bookkeeper.tls;

import static org.mockito.Mockito.when;

import org.apache.bookkeeper.auth.BookieAuthProvider;
import org.apache.bookkeeper.common.util.ReflectionUtils;
import org.apache.bookkeeper.conf.ServerConfiguration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;


/**
 * Unit Tests for BookieAuthZFactory.
 */
@RunWith(MockitoJUnitRunner.class)
public class BookieAuthZFactoryTest {

    @InjectMocks
    private BookieAuthZFactory bookieAuthZFactory;

    @Mock
    private ServerConfiguration mockedServerConfig;

    @Before
    public void setup() {

        String factoryClassName = BookieAuthZFactory.class.getName();
        bookieAuthZFactory = (BookieAuthZFactory) ReflectionUtils.newInstance(factoryClassName,
                BookieAuthProvider.Factory.class);

        when(mockedServerConfig.getAuthorizedRoles()).thenReturn(new String[]{"role1"});
    }

    @Test
    public void testBookieAuthZInitSuccess() {
        try {
            bookieAuthZFactory.init(mockedServerConfig);
            Assert.assertTrue(true);
        } catch (IOException e) {
            Assert.fail("Supposed to initialize correctly due to mock");
        }
    }
}