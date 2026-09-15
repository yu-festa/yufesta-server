package com.yufesta.domain.appsetting.repository;

import com.yufesta.domain.appsetting.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
