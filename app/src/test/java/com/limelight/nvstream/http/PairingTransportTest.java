package com.limelight.nvstream.http;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PairingTransportTest {
    @Test
    public void lostResponseDoesNotReplayStateChangingPairingRequest() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
            server.start();
            NvHTTP http = client(server);
            assertThrows(IOException.class,
                    () -> http.executePairingCommand("clientchallenge=TEST", true));
            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals("TEST", request.getRequestUrl().queryParameter("clientchallenge"));
            assertEquals(1, server.getRequestCount());
        }
    }

    @Test
    public void pairingDoesNotFollowRedirectAndResendCertificateOrChallenge() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setResponseCode(302)
                    .setHeader("Location", server.url("/other")));
            NvHTTP http = client(server);
            HostHttpResponseException failure = assertThrows(HostHttpResponseException.class,
                    () -> http.executePairingCommand("phrase=getservercert&clientcert=TEST", false));
            assertEquals(302, failure.getErrorCode());
            assertEquals(1, server.getRequestCount());
        }
    }

    private static NvHTTP client(MockWebServer server) throws IOException {
        return new NvHTTP(new ComputerDetails.AddressTuple("127.0.0.1", server.getPort()),
                0, "test-client", null, mock(LimelightCryptoProvider.class));
    }
}
