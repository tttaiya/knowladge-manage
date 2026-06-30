package com.km.admin.controller;

import com.km.admin.model.dto.BatchDeleteDTO;
import com.km.admin.model.dto.KnowledgeBaseCreateDTO;
import com.km.admin.model.dto.KnowledgeBaseQueryDTO;
import com.km.admin.model.dto.KnowledgeBaseUpdateDTO;
import com.km.admin.model.vo.ApiResult;
import com.km.admin.model.vo.KnowledgeBaseVO;
import com.km.admin.model.vo.PageResult;
import com.km.admin.model.vo.ReprocessResultVO;
import com.km.admin.model.vo.UpdateKnowledgeBaseResultVO;
import com.km.admin.service.KnowledgeBaseService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/knowledge-bases")
public class KnowledgeBaseController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @GetMapping
    public ApiResult<PageResult<KnowledgeBaseVO>> list(KnowledgeBaseQueryDTO query) {
        return ApiResult.success(knowledgeBaseService.listPage(query));
    }

    @GetMapping("/{id}")
    public ApiResult<KnowledgeBaseVO> detail(@PathVariable Long id) {
        return ApiResult.success(knowledgeBaseService.getById(id));
    }

    @PostMapping
    public ApiResult<Map<String, Long>> create(@Valid @RequestBody KnowledgeBaseCreateDTO dto) {
        Long id = knowledgeBaseService.create(dto);
        Map<String, Long> data = new HashMap<String, Long>();
        data.put("id", id);
        return ApiResult.success(data);
    }

    @PutMapping("/{id}")
    public ApiResult<UpdateKnowledgeBaseResultVO> update(@PathVariable Long id,
                                                          @RequestParam(value = "confirmation", required = false) Boolean confirmation,
                                                          @Valid @RequestBody KnowledgeBaseUpdateDTO dto) {
        return ApiResult.success(knowledgeBaseService.update(id, dto, confirmation));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Object> delete(@PathVariable Long id) {
        knowledgeBaseService.deleteById(id);
        return ApiResult.success(null);
    }

    @PostMapping("/batch-delete")
    public ApiResult<Map<String, Integer>> batchDelete(@Valid @RequestBody BatchDeleteDTO dto) {
        Integer affected = knowledgeBaseService.batchDelete(dto);
        Map<String, Integer> data = new HashMap<String, Integer>();
        data.put("affectedRows", affected);
        return ApiResult.success(data);
    }

    @PostMapping("/{id}/reprocess")
    public ApiResult<ReprocessResultVO> reprocess(@PathVariable Long id,
                                                   @RequestParam("confirmation") boolean confirmation) {
        return ApiResult.success(knowledgeBaseService.reprocess(id, confirmation));
    }
}
