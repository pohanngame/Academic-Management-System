package com.example.academicprofile.ai;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.academicprofile.common.exception.BusinessException;

@Service
public class AiSettingsService {

    private final AiProviderConfigMapper configMapper;
    private final AiApiKeyCipher apiKeyCipher;
    private final AiBaseUrlValidator baseUrlValidator;
    private final AiChatClient chatClient;

    public AiSettingsService(
            AiProviderConfigMapper configMapper,
            AiApiKeyCipher apiKeyCipher,
            AiBaseUrlValidator baseUrlValidator,
            AiChatClient chatClient) {
        this.configMapper = configMapper;
        this.apiKeyCipher = apiKeyCipher;
        this.baseUrlValidator = baseUrlValidator;
        this.chatClient = chatClient;
    }

    public AiSettingsResponse getSettings(Long teacherId) {
        AiProviderConfig config = findByTeacherId(teacherId);
        return config == null ? AiSettingsResponse.empty() : AiSettingsResponse.from(config);
    }

    @Transactional
    public AiSettingsResponse saveSettings(Long teacherId, AiSettingsRequest request) {
        AiProviderConfig config = findByTeacherId(teacherId);
        boolean creating = config == null;
        String apiKey = trimToEmpty(request.apiKey());
        if (creating && !StringUtils.hasText(apiKey)) {
            throw new BusinessException("首次保存 AI 配置时必须提供 API Key");
        }
        if (StringUtils.hasText(apiKey) && apiKey.length() < 8) {
            throw new BusinessException("API Key 长度不能少于 8 个字符");
        }
        if (creating) {
            config = new AiProviderConfig();
            config.setTeacherId(teacherId);
        } else {
            verifyExistingKeyCanBeDecrypted(config);
        }
        config.setProviderName(request.providerName().trim());
        config.setBaseUrl(baseUrlValidator.normalizeAndValidate(request.baseUrl()));
        config.setModelName(request.modelName().trim());
        if (StringUtils.hasText(apiKey)) {
            AiApiKeyCipher.EncryptedValue encrypted = apiKeyCipher.encrypt(apiKey);
            config.setEncryptedApiKey(encrypted.ciphertext());
            config.setEncryptionKeyId(encrypted.keyId());
            config.setApiKeyLastFour(lastFour(apiKey));
        }
        if (creating) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
        return AiSettingsResponse.from(config);
    }

    public AiConnectionTestResult testConnection(Long teacherId) {
        return chatClient.testConnection(requireRuntimeConfig(teacherId));
    }

    @Transactional
    public void deleteSettings(Long teacherId) {
        AiProviderConfig config = findByTeacherId(teacherId);
        if (config != null) {
            verifyExistingKeyCanBeDecrypted(config);
            configMapper.deleteById(config.getId());
        }
    }

    public AiRuntimeConfig requireRuntimeConfig(Long teacherId) {
        AiProviderConfig config = findByTeacherId(teacherId);
        if (config == null) {
            throw new BusinessException("请先在 AI 模型设置中完成配置");
        }
        String apiKey = apiKeyCipher.decrypt(config.getEncryptedApiKey(), config.getEncryptionKeyId());
        return new AiRuntimeConfig(
                config.getProviderName(),
                baseUrlValidator.normalizeAndValidate(config.getBaseUrl()),
                config.getModelName(),
                apiKey);
    }

    private AiProviderConfig findByTeacherId(Long teacherId) {
        return configMapper.selectOne(new LambdaQueryWrapper<AiProviderConfig>()
                .eq(AiProviderConfig::getTeacherId, teacherId)
                .last("LIMIT 1"));
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String lastFour(String apiKey) {
        return apiKey.substring(apiKey.length() - 4);
    }

    private void verifyExistingKeyCanBeDecrypted(AiProviderConfig config) {
        apiKeyCipher.decrypt(config.getEncryptedApiKey(), config.getEncryptionKeyId());
    }
}
