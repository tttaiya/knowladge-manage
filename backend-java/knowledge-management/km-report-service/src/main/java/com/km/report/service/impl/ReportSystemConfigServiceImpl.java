package com.km.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.km.report.entity.ReportSystemConfig;
import com.km.report.mapper.ReportSystemConfigMapper;
import com.km.report.service.ReportSystemConfigService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReportSystemConfigServiceImpl extends ServiceImpl<ReportSystemConfigMapper, ReportSystemConfig> implements ReportSystemConfigService {

    @Override
    public String getValueByKey(String configKey, String defaultValue) {
        if (!StringUtils.hasText(configKey)) {
            return defaultValue;
        }

        ReportSystemConfig config = getOne(
                new LambdaQueryWrapper<ReportSystemConfig>()
                        .eq(ReportSystemConfig::getConfigKey, configKey)
                        .last("LIMIT 1")
        );

        if (config == null || config.getConfigValue() == null) {
            return defaultValue;
        }

        return config.getConfigValue();
    }
}
