UPDATE app_settings
SET description = '소형 LLM 콘텐츠 차단 판정 신뢰도 임계값(0~1)',
    updated_at = NOW()
WHERE setting_key = 'filter.llm.threshold';
