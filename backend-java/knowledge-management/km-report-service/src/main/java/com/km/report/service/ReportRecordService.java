package com.km.report.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.km.report.dto.ReportPageQueryDTO;
import com.km.report.entity.ReportRecord;
import com.km.report.vo.PageResultVO;
import java.util.List;

public interface ReportRecordService extends IService<ReportRecord> {
    PageResultVO<ReportRecord> pageRecords(ReportPageQueryDTO queryDTO);

    List<ReportRecord> listOwned();
}
