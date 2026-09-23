package com.yufesta.domain.appsetting.entity;

import com.yufesta.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 운영 정책에 사용하는 설정값
 */
@Getter
@Entity
@Table(name = "app_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppSetting extends BaseTimeEntity {

    @Id
    @Column(name = "setting_key", length = 50)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 500)
    private String settingValue;

    @Column(length = 100)
    private String description;

    public AppSetting(String settingKey, String settingValue, String description) {
        this.settingKey = settingKey;
        this.settingValue = settingValue;
        this.description = description;
    }
}
