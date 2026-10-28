package com.limelight.nvstream.http;

import com.limelight.nvstream.http.PairingManager.PairState;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PairingManagerTest {
    private static KeyPair clientKeys, serverKeys;
    private static X509Certificate clientCert, serverCert;
    private static byte[] clientPem;
    private NvHTTP http;
    private PairingManager manager;
    private byte[] aesKey, clientResponse;
    private final byte[] serverChallenge = new byte[16];
    private final byte[] serverSecret = new byte[16];
    private boolean corruptSignature, wrongPin;

    @BeforeClass
    public static void createIdentities() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        clientKeys = generator.generateKeyPair();
        serverKeys = generator.generateKeyPair();
        clientCert = certificate(clientKeys, "Test client");
        serverCert = certificate(serverKeys, "Test server");
        clientPem = pem(clientCert);
    }

    @Before
    public void setup() throws Exception {
        http = mock(NvHTTP.class);
        LimelightCryptoProvider crypto = mock(LimelightCryptoProvider.class);
        when(crypto.getClientCertificate()).thenReturn(clientCert);
        when(crypto.getClientPrivateKey()).thenReturn(clientKeys.getPrivate());
        when(crypto.getPemEncodedClientCertificate()).thenReturn(clientPem);
        when(http.getServerMajorVersion(anyString())).thenReturn(7);
        when(http.executePairingChallenge()).thenReturn(xml("paired", "1"));
        when(http.executePairingCommand(anyString(), anyBoolean())).thenAnswer(call ->
                respond(call.getArgument(0)));
        manager = new PairingManager(http, crypto);
        Arrays.fill(serverChallenge, (byte) 0x35);
        Arrays.fill(serverSecret, (byte) 0x71);
    }

    @Test
    public void completesRealCryptographicHandshakeWithoutUnpairing() throws Exception {
        assertEquals(PairState.PAIRED, pair());
        assertEquals(serverCert, manager.getPairedCert());
        verify(http, never()).unpair();
        verify(http, times(1)).executePairingChallenge();
    }

    @Test
    public void lostChallengeResponseIsCancelledAndNextAttemptCanSucceed() throws Exception {
        doThrow(new SocketTimeoutException("Lost response"))
                .doAnswer(call -> respond(call.getArgument(0)))
                .when(http).executePairingCommand(startsWith("clientchallenge="), eq(true));
        assertThrows(SocketTimeoutException.class, this::pair);
        assertNull(manager.getPairedCert());
        verify(http, times(1)).executePairingCommand(startsWith("clientchallenge="), eq(true));
        verify(http, times(1)).unpair();
        assertEquals(PairState.PAIRED, pair());
        verify(http, times(1)).unpair();
    }

    @Test
    public void lostInitialPinResponseIsNotReplayed() throws Exception {
        doThrow(new IOException("Connection closed")).when(http)
                .executePairingCommand(startsWith("phrase=getservercert"), eq(false));
        assertThrows(IOException.class, this::pair);
        verify(http, times(1)).executePairingCommand(startsWith("phrase=getservercert"), eq(false));
        verify(http).unpair();
        verify(http, never()).executePairingChallenge();
    }

    @Test
    public void cleanupFailurePreservesOriginalNetworkError() throws Exception {
        SocketTimeoutException original = new SocketTimeoutException("Lost response");
        doThrow(original).when(http)
                .executePairingCommand(startsWith("serverchallengeresp="), eq(true));
        doThrow(new IOException("Host unavailable")).when(http).unpair();
        assertSame(original, assertThrows(SocketTimeoutException.class, this::pair));
        assertNull(manager.getPairedCert());
    }

    @Test
    public void retriesOnlyFinalConfirmationAfterTransientNetworkFailures() throws Exception {
        when(http.executePairingChallenge())
                .thenThrow(new SocketTimeoutException("Not ready"))
                .thenThrow(new IOException("Connection reset"))
                .thenReturn(xml("paired", "1"));
        assertEquals(PairState.PAIRED, pair());
        verify(http, times(3)).executePairingChallenge();
        verify(http, times(1)).executePairingCommand(startsWith("clientpairingsecret="), eq(true));
        verify(http, never()).unpair();
    }

    @Test
    public void toleratesHostCertificatePublicationDelay() throws Exception {
        when(http.executePairingChallenge())
                .thenReturn("<root status_code=\"401\" status_message=\"Not authorized\"/>")
                .thenReturn(xml("paired", "1"));
        assertEquals(PairState.PAIRED, pair());
        verify(http, times(2)).executePairingChallenge();
        verify(http, never()).unpair();
    }

    @Test
    public void finalConfirmationRetriesAreBoundedAndFailureCancelsSession() throws Exception {
        when(http.executePairingChallenge()).thenThrow(new IOException("Unavailable"));
        assertThrows(IOException.class, this::pair);
        verify(http, times(3)).executePairingChallenge();
        verify(http).unpair();
        assertNull(manager.getPairedCert());
    }

    @Test
    public void certificateValidationFailureIsNeverRetriedOrAccepted() throws Exception {
        SSLHandshakeException failure = new SSLHandshakeException("Untrusted certificate");
        failure.initCause(new CertificateException("Certificate mismatch"));
        when(http.executePairingChallenge()).thenThrow(failure);
        assertSame(failure, assertThrows(SSLHandshakeException.class, this::pair));
        verify(http, times(1)).executePairingChallenge();
        verify(http).unpair();
        assertNull(manager.getPairedCert());
    }

    @Test
    public void hostnameValidationFailureIsNeverRetriedOrAccepted() throws Exception {
        when(http.executePairingChallenge()).thenThrow(new SSLPeerUnverifiedException("Wrong peer"));
        assertThrows(SSLPeerUnverifiedException.class, this::pair);
        verify(http, times(1)).executePairingChallenge();
        verify(http).unpair();
    }

    @Test
    public void explicitFinalRejectionIsNotRetried() throws Exception {
        when(http.executePairingChallenge()).thenReturn(xml("paired", "0"));
        assertEquals(PairState.FAILED, pair());
        verify(http, times(1)).executePairingChallenge();
        verify(http).unpair();
    }

    @Test
    public void incorrectPinIsRejectedAndCleanedUp() throws Exception {
        wrongPin = true;
        assertEquals(PairState.PIN_WRONG, pair());
        verify(http).unpair();
        verify(http, never()).executePairingChallenge();
        assertNull(manager.getPairedCert());
    }

    @Test
    public void wrongPinOnFirstAttemptIsRejectedBeforeRetryCanPair() throws Exception {
        wrongPin = true;
        assertEquals(PairState.PIN_WRONG, pair());
        assertNull(manager.getPairedCert());
        verify(http, times(1)).unpair();
        verify(http, never()).executePairingChallenge();

        wrongPin = false;
        assertEquals(PairState.PAIRED, pair());
        assertEquals(serverCert, manager.getPairedCert());
        verify(http, times(1)).unpair();
        verify(http, times(1)).executePairingChallenge();
    }

    @Test
    public void tamperedServerSecretIsRejectedAndCleanedUp() throws Exception {
        corruptSignature = true;
        assertEquals(PairState.FAILED, pair());
        verify(http).unpair();
        verify(http, never()).executePairingChallenge();
    }

    @Test
    public void malformedResponseCannotLeavePendingSession() throws Exception {
        doReturn(xml("paired", "1")).when(http) // Missing signed secret.
                .executePairingCommand(startsWith("serverchallengeresp="), eq(true));
        assertThrows(org.xmlpull.v1.XmlPullParserException.class, this::pair);
        verify(http).unpair();
        assertNull(manager.getPairedCert());
    }

    @Test
    public void missingServerCertificateReturnsBusyAndCancelsAttempt() throws Exception {
        doReturn(xml("paired", "1")).when(http)
                .executePairingCommand(startsWith("phrase=getservercert"), eq(false));
        assertEquals(PairState.ALREADY_IN_PROGRESS, pair());
        verify(http).unpair();
    }

    @Test
    public void otpAuthenticationRemainsBoundToPinSaltAndPassphrase() throws Exception {
        String passphrase = "test-only passphrase";
        assertEquals(PairState.PAIRED, manager.pair("server info", "1234", passphrase));
        org.mockito.ArgumentCaptor<String> arguments = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(http).executePairingCommand(arguments.capture(), eq(false));
        String request = arguments.getValue();
        String salt = parameter(request, "salt");
        assertEquals(hex(hash("1234" + salt + passphrase)), parameter(request, "otpauth"));
    }

    private PairState pair() throws Exception {
        return manager.pair("server info", "1234", null);
    }

    // A small host peer computes real AES challenges and RSA signatures. It also
    // verifies the client's response, so success is not a sequence of canned XML.
    private String respond(String arguments) throws Exception {
        if (arguments.startsWith("phrase=getservercert")) {
            byte[] salt = unhex(parameter(arguments, "salt"));
            aesKey = Arrays.copyOf(hash(concat(salt, "1234".getBytes(StandardCharsets.UTF_8))), 16);
            return xml("paired", "1", "plaincert", hex(pem(serverCert)));
        }
        if (arguments.startsWith("clientchallenge=")) {
            byte[] challenge = aes(unhex(parameter(arguments, "clientchallenge")), Cipher.DECRYPT_MODE);
            byte[] response = hash(concat(challenge, serverCert.getSignature(), serverSecret));
            if (wrongPin) response[0] ^= 1;
            return xml("paired", "1", "challengeresponse",
                    hex(aes(concat(response, serverChallenge), Cipher.ENCRYPT_MODE)));
        }
        if (arguments.startsWith("serverchallengeresp=")) {
            clientResponse = aes(unhex(parameter(arguments, "serverchallengeresp")), Cipher.DECRYPT_MODE);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(serverKeys.getPrivate());
            signature.update(serverSecret);
            byte[] signed = signature.sign();
            if (corruptSignature) signed[0] ^= 1;
            return xml("paired", "1", "pairingsecret", hex(concat(serverSecret, signed)));
        }
        if (arguments.startsWith("clientpairingsecret=")) {
            byte[] payload = unhex(parameter(arguments, "clientpairingsecret"));
            byte[] secret = Arrays.copyOf(payload, 16);
            assertArrayEquals(hash(concat(serverChallenge, clientCert.getSignature(), secret)), clientResponse);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(clientCert);
            signature.update(secret);
            assertTrue(signature.verify(Arrays.copyOfRange(payload, 16, payload.length)));
            return xml("paired", "1");
        }
        throw new AssertionError("Unexpected pairing phase");
    }

    private byte[] aes(byte[] input, int mode) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(mode, new SecretKeySpec(aesKey, "AES"));
        return cipher.doFinal(input);
    }

    private static X509Certificate certificate(KeyPair keys, String name) throws Exception {
        X500Name subject = new X500Name("CN=" + name);
        return new JcaX509CertificateConverter().getCertificate(new JcaX509v3CertificateBuilder(
                subject, BigInteger.ONE, new Date(0), new Date(4102444800000L), subject, keys.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate())));
    }

    private static byte[] pem(X509Certificate certificate) throws Exception {
        StringWriter output = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(output)) {
            writer.writeObject(certificate);
        }
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] hash(String input) throws Exception {
        return hash(input.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hash(byte[] input) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(input);
    }

    private static byte[] concat(byte[]... inputs) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        for (byte[] input : inputs) output.write(input);
        return output.toByteArray();
    }

    private static String parameter(String arguments, String name) {
        for (String value : arguments.split("&")) {
            if (value.startsWith(name + "=")) return value.substring(name.length() + 1);
        }
        throw new AssertionError("Missing parameter: " + name);
    }

    private static String xml(String... values) {
        StringBuilder output = new StringBuilder("<root status_code=\"200\">");
        for (int i = 0; i < values.length; i += 2) {
            output.append('<').append(values[i]).append('>').append(values[i + 1])
                    .append("</").append(values[i]).append('>');
        }
        return output.append("</root>").toString();
    }

    private static String hex(byte[] input) {
        StringBuilder output = new StringBuilder();
        for (byte value : input) output.append(String.format(java.util.Locale.ROOT, "%02X", value & 255));
        return output.toString();
    }

    private static byte[] unhex(String input) {
        byte[] output = new byte[input.length() / 2];
        for (int i = 0; i < output.length; i++) {
            output[i] = (byte) Integer.parseInt(input.substring(i * 2, i * 2 + 2), 16);
        }
        return output;
    }
}
