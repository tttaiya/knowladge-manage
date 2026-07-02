package com.km.report.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.km.report.entity.ReportSystemConfig;

public interface ReportSystemConfigService extends IService<ReportSystemConfig> {

    String getValueByKey(String configKey, String defaultValue);
}
