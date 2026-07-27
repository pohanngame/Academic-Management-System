package com.example.academicprofile.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.academicprofile.common.exception.BusinessException;

class AiSettingsServiceTest {

    private AiProviderConfigMapper mapper;
    private AiApiKeyCipher cipher;
    private AiChatClient chatClient;
    private AiSettingsService service;
    private AtomicReference<AiProviderConfig> stored;

    @BeforeEach
    void setUp() throws Exception {
        mapper = mock(AiProviderConfigMapper.class);
        chatClient = mock(AiChatClient.class);
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 7);
        cipher = new AiApiKeyCipher(key);
        InetAddress publicAddress = InetAddress.getByName("8.8.8.8");
        AiBaseUrlValidator validator = new AiBaseUrlValidator(
                host -> new InetAddress[] { publicAddress },
                false);
        service = new AiSettingsService(mapper, cipher, validator, chatClient);
        stored = new AtomicReference<>();
        when(mapper.selectOne(any())).thenAnswer(invocation -> stored.get());
        when(mapper.insert(any(AiProviderConfig.class))).thenAnswer(invocation -> {
            AiProviderConfig config = invocation.getArgument(0);
            config.setId(20L);
            stored.set(config);
            return 1;
        });
        when(mapper.updateById(any(AiProviderConfig.class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });
        when(mapper.deleteById(any(Long.class))).thenAnswer(invocation -> {
            stored.set(null);
            return 1;
        });
    }

    @Test
    void firstSaveRequiresApiKeyAndGetReturnsOnlyMaskedMetadata() {
        assertThrows(
                BusinessException.class,
                () -> service.saveSettings(1L, request("  ")));
        assertThrows(
                BusinessException.class,
                () -> service.saveSettings(1L, request("short")));

        AiSettingsResponse response = service.saveSettings(1L, request("test-secret-1234"));
        AiProviderConfig entity = stored.get();

        assertTrue(response.configured());
        assertEquals("****1234", response.maskedApiKey());
        assertFalse(entity.getEncryptedApiKey().contains("test-secret-1234"));
        assertEquals("1234", entity.getApiKeyLastFour());
        assertEquals(cipher.keyId(), entity.getEncryptionKeyId());
        assertEquals("test-secret-1234", cipher.decrypt(entity.getEncryptedApiKey(), entity.getEncryptionKeyId()));
        assertFalse(response.toString().contains("test-secret-1234"));
        assertFalse(response.toString().contains(entity.getEncryptedApiKey()));
        assertFalse(request("test-secret-1234").toString().contains("test-secret-1234"));
    }

    @Test
    void blankUpdateRetainsKeyAndNewKeyReplacesIt() {
        service.saveSettings(1L, request("old-secret-1111"));
        String oldCiphertext = stored.get().getEncryptedApiKey();

        service.saveSettings(
                1L,
                new AiSettingsRequest("Updated", "https://api.example.com/v2/", "new-model", ""));
        assertEquals(oldCiphertext, stored.get().getEncryptedApiKey());
        assertEquals("old-secret-1111", service.requireRuntimeConfig(1L).apiKey());

        service.saveSettings(1L, request("new-secret-2222"));
        assertFalse(oldCiphertext.equals(stored.get().getEncryptedApiKey()));
        assertEquals("new-secret-2222", service.requireRuntimeConfig(1L).apiKey());
        assertEquals("****2222", service.getSettings(1L).maskedApiKey());
    }

    @Test
    void emptyGetDeleteAndConnectionTestAreControlled() {
        AiSettingsResponse empty = service.getSettings(1L);
        assertFalse(empty.configured());
        assertFalse(empty.keyConfigured());
        assertNull(empty.maskedApiKey());
        assertThrows(BusinessException.class, () -> service.requireRuntimeConfig(1L));

        service.saveSettings(1L, request("connection-secret-3333"));
        AiConnectionTestResult expected = new AiConnectionTestResult(true, "连接测试成功");
        when(chatClient.testConnection(any(AiRuntimeConfig.class))).thenReturn(expected);
        assertEquals(expected, service.testConnection(1L));
        verify(chatClient).testConnection(any(AiRuntimeConfig.class));

        service.deleteSettings(1L);
        assertFalse(service.getSettings(1L).configured());
        assertThrows(BusinessException.class, () -> service.requireRuntimeConfig(1L));
    }

    @Test
    void unreadableExistingKeyCannotBeOverwrittenOrDeleted() {
        service.saveSettings(1L, request("original-secret-4444"));
        AiProviderConfig config = stored.get();
        config.setEncryptionKeyId("wrong-key-id");

        assertThrows(BusinessException.class, () -> service.saveSettings(1L, request("replacement-secret-5555")));
        assertThrows(BusinessException.class, () -> service.deleteSettings(1L));
        assertEquals(config, stored.get());
    }

    @Test
    void runtimeConfigStringNeverIncludesApiKey() {
        AiRuntimeConfig runtime = new AiRuntimeConfig(
                "Provider",
                "https://api.example.com/v1",
                "model",
                "runtime-secret-6666");

        assertFalse(runtime.toString().contains("runtime-secret-6666"));
        assertTrue(runtime.toString().contains("[REDACTED]"));
    }

    private AiSettingsRequest request(String apiKey) {
        return new AiSettingsRequest(" Example Provider ", "https://api.example.com/v1/", " example-model ", apiKey);
    }
}
