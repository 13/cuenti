package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.GlobalSetting;
import com.cuenti.homebanking.repository.GlobalSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GlobalSettingService {

    private final GlobalSettingRepository globalSettingRepository;

    public static final String REGISTRATION_ENABLED = "registration_enabled";
    public static final String API_ENABLED = "api_enabled";

    @Transactional(readOnly = true)
    public boolean isRegistrationEnabled() {
        return getBooleanSetting(REGISTRATION_ENABLED, true);
    }

    @Transactional
    public void setRegistrationEnabled(boolean enabled) {
        setSetting(REGISTRATION_ENABLED, String.valueOf(enabled));
    }

    @Transactional(readOnly = true)
    public boolean isApiEnabled() {
        return getBooleanSetting(API_ENABLED, false);
    }

    @Transactional
    public void setApiEnabled(boolean enabled) {
        setSetting(API_ENABLED, String.valueOf(enabled));
    }

    private boolean getBooleanSetting(String key, boolean defaultValue) {
        return globalSettingRepository.findById(key)
                .map(GlobalSetting::getSettingValue)
                .map(Boolean::parseBoolean)
                .orElse(defaultValue);
    }

    private void setSetting(String key, String value) {
        GlobalSetting setting = GlobalSetting.builder()
                .settingKey(key)
                .settingValue(value)
                .build();
        globalSettingRepository.save(setting);
    }
}
