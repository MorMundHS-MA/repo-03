package chat.server;

import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import chat.server.Service;
import services.common.AuthenticationProvider;

import javax.ws.rs.core.Response;

public class TestService {
    public TestService() throws Exception {
        Config.init(new String[]{});
    }

    @Test
    public void successfulNewMessage() {
        AuthenticationProvider auth = mock(AuthenticationProvider.class);
        when(auth.authenticateUser("valid", "validUser")).thenReturn(true);
        StorageProviderMongoDB provider = mock(StorageProviderMongoDB.class);
        when(provider.addMessage(any(), any())).thenReturn(1);

        Service service = new Service(provider, auth);

        Response res = service.send(
                "{'to':'user1', 'from':'validUser', 'date':'2019-12-01T00:31:43+0000', 'text': 'Hello', 'token': 'valid'}"
                        .replace('\'', '"'));
        assertEquals(Response.Status.CREATED.getStatusCode(), res.getStatus());
    }

    @Test
    public void authenticationError() {
        AuthenticationProvider auth = mock(AuthenticationProvider.class);
        when(auth.authenticateUser("valid", "validUser")).thenReturn(true);
        StorageProviderMongoDB provider = mock(StorageProviderMongoDB.class);
        when(provider.addMessage(any(), any())).thenReturn(1);

        Service service = new Service(provider, auth);

        Response res = service.send(
                "{'to':'user1', 'from':'notAllowed', 'date':'2019-12-01T00:31:43+0000', 'text': 'Hello', 'token': 'valid'}"
                        .replace('\'', '"'));
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), res.getStatus());
    }

    @Test
    public void malformedJson() {
        AuthenticationProvider auth = mock(AuthenticationProvider.class);
        when(auth.authenticateUser("valid", "validUser")).thenReturn(true);
        StorageProviderMongoDB provider = mock(StorageProviderMongoDB.class);
        when(provider.addMessage(any(), any())).thenReturn(1);

        Service service = new Service(provider, auth);

        Response res = service.send("{NotJson:nope}");
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), res.getStatus());
    }

    @Test
    public void missingSender() {
        AuthenticationProvider auth = mock(AuthenticationProvider.class);
        when(auth.authenticateUser("valid", "validUser")).thenReturn(true);
        StorageProviderMongoDB provider = mock(StorageProviderMongoDB.class);
        when(provider.addMessage(any(), any())).thenReturn(1);

        Service service = new Service(provider, auth);

        Response res = service.send(
                "{'to':'user1', 'date':'2019-12-01T00:31:43+0000', 'text': 'Hello', 'token': 'valid'}"
                        .replace('\'', '"'));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), res.getStatus());
    }

    @Test
    public void storageProviderRejectsMessage() {
        AuthenticationProvider auth = mock(AuthenticationProvider.class);
        when(auth.authenticateUser("valid", "validUser")).thenReturn(true);
        StorageProviderMongoDB provider = mock(StorageProviderMongoDB.class);
        when(provider.addMessage(any(), any())).thenReturn(-1);

        Service service = new Service(provider, auth);

        Response res = service.send(
                "{'to':'user1', 'from':'validUser', 'date':'2019-12-01T00:31:43+0000', 'text': 'Hello', 'token': 'valid'}"
                        .replace('\'', '"'));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), res.getStatus());
    }
}
