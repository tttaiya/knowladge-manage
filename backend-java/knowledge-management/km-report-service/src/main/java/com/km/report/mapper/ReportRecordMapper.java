package com.km.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.km.report.entity.ReportRecord;
import com.km.report.vo.ReportGenerateTrendVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReportRecordMapper extends BaseMapper<ReportRecord> {

    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS date, COUNT(1) AS count " +
            "FROM report_record " +
            "WHERE deleted = 0 AND user_id = #{userId} AND create_time >= DATE_SUB(CURDATE(), INTERVAL 29 DAY) " +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') " +
            "ORDER BY date ASC")
    List<ReportGenerateTrendVO> selectLast30DaysTrend(@Param("userId") String userId);
}
