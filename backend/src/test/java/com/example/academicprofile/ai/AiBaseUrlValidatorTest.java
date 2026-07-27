package com.example.academicprofile.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;

import com.example.academicprofile.common.exception.BusinessException;

class AiBaseUrlValidatorTest {

    @Test
    void acceptsHttpsPublicHostNormalizesSlashAndAppendsEndpoint() throws Exception {
        AiBaseUrlValidator validator = validatorWith("8.8.8.8");

        String normalized = validator.normalizeAndValidate("https://api.example.com/v1/");

        assertEquals("https://api.example.com/v1", normalized);
        assertEquals(
                "https://api.example.com/v1/chat/completions",
                validator.chatCompletionsUrl(normalized));
    }

    @Test
    void rejectsDangerousSchemesCredentialsAndLocalNames() throws Exception {
        AiBaseUrlValidator validator = validatorWith("8.8.8.8");

        assertThrows(BusinessException.class, () -> validator.normalizeAndValidate("http://api.example.com"));
        assertThrows(BusinessException.class, () -> validator.normalizeAndValidate("file:///etc/passwd"));
        assertThrows(BusinessException.class, () -> validator.normalizeAndValidate("ftp://api.example.com"));
        assertThrows(BusinessException.class, () -> validator.normalizeAndValidate("https://user:pass@api.example.com"));
        assertThrows(BusinessException.class, () -> validator.normalizeAndValidate("https://localhost/v1"));
        assertThrows(BusinessException.class, () -> validator.normalizeAndValidate("https://service.localhost/v1"));
    }

    @Test
    void rejectsLoopbackPrivateLinkLocalMulticastAndCarrierGradeAddresses() throws Exception {
        assertRejectedAddress("127.0.0.1");
        assertRejectedAddress("10.0.0.1");
        assertRejectedAddress("172.16.0.1");
        assertRejectedAddress("192.168.1.1");
        assertRejectedAddress("169.254.169.254");
        assertRejectedAddress("224.0.0.1");
        assertRejectedAddress("100.64.0.1");
        assertRejectedAddress("::1");
        assertRejectedAddress("fc00::1");
    }

    @Test
    void rejectsUnresolvableHosts() {
        AiBaseUrlValidator validator = new AiBaseUrlValidator(
                host -> {
                    throw new UnknownHostException();
                },
                false);

        assertThrows(BusinessException.class, () -> validator.normalizeAndValidate("https://api.example.com"));
    }

    private void assertRejectedAddress(String address) throws Exception {
        AiBaseUrlValidator validator = validatorWith(address);
        assertThrows(BusinessException.class, () -> validator.normalizeAndValidate("https://api.example.com"));
    }

    private AiBaseUrlValidator validatorWith(String address) throws Exception {
        InetAddress resolved = InetAddress.getByName(address);
        return new AiBaseUrlValidator(host -> new InetAddress[] { resolved }, false);
    }
}
