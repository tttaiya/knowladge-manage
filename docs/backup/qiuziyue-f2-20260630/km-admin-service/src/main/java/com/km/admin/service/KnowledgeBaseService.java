package com.km.admin.service;

import com.km.admin.exception.BusinessException;
import com.km.admin.mapper.KnowledgeBaseMapper;
import com.km.admin.model.dto.BatchDeleteDTO;
import com.km.admin.model.dto.KnowledgeBaseCreateDTO;
import com.km.admin.model.dto.KnowledgeBaseQueryDTO;
import com.km.admin.model.dto.KnowledgeBaseUpdateDTO;
import com.km.admin.model.entity.KnowledgeBase;
import com.km.admin.model.vo.KnowledgeBaseSnapshotVO;
import com.km.admin.model.vo.KnowledgeBaseVO;
import com.km.admin.model.vo.PageResult;
import com.km.admin.model.vo.ReprocessResultVO;
import com.km.admin.model.vo.UpdateKnowledgeBaseResultVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Service
public class KnowledgeBaseService {

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    public PageResult<KnowledgeBaseVO> listPage(KnowledgeBaseQueryDTO query) {
        normalizeQuery(query);
        Integer offset = (query.getPage() - 1) * query.getPageSize();
        List<KnowledgeBaseVO> records = knowledgeBaseMapper.selectPage(query, offset);
        Long total = knowledgeBaseMapper.countPage(query);

        PageResult<KnowledgeBaseVO> pageResult = new PageResult<KnowledgeBaseVO>();
        pageResult.setRecords(records);
        pageResult.setTotal(total);
        pageResult.setPage(query.getPage());
        pageResult.setPageSize(query.getPageSize());
        return pageResult;
    }

    public KnowledgeBaseVO getById(Long id) {
        KnowledgeBaseVO detail = knowledgeBaseMapper.selectDetailById(id);
        if (detail == null) {
            throw new BusinessException(2001, "??????");
        }
        return detail;
    }

    public Long create(KnowledgeBaseCreateDTO dto) {
        validateCategory(dto.getCategory());
        validateRetrievalStrategy(dto.getRetrievalStrategy());
        validateStrategyFields(dto.getChunkStrategy(), dto.getChunkSize(), dto.getChunkOverlap());
        checkNameUnique(dto.getName(), null);

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        BeanUtils.copyProperties(dto, knowledgeBase);
        knowledgeBase.setSeparatorsJson(normalizeSeparatorsJson(dto.getSeparatorsJson()));
        knowledgeBase.setDocumentCount(0);
        knowledgeBase.setIsDeleted(0);
        knowledgeBase.setCreatedByUserId(0L);
        knowledgeBase.setCreatedByName("system");
        knowledgeBase.setCreatedAt(new Date());
        knowledgeBase.setUpdatedAt(new Date());
        knowledgeBaseMapper.insert(knowledgeBase);
        return knowledgeBase.getId();
    }

    public UpdateKnowledgeBaseResultVO update(Long id, KnowledgeBaseUpdateDTO dto, Boolean confirmation) {
        validateCategory(dto.getCategory());
        validateRetrievalStrategy(dto.getRetrievalStrategy());
        validateStrategyFields(dto.getChunkStrategy(), dto.getChunkSize(), dto.getChunkOverlap());
        KnowledgeBase existing = knowledgeBaseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(2001, "??????");
        }

        checkNameUnique(dto.getName(), id);
        boolean strategyChanged = isStrategyChanged(existing, dto);
        if (strategyChanged && !Boolean.TRUE.equals(confirmation)) {
            throw new BusinessException(2003, "??????????? confirmation=true ???????");
        }

        KnowledgeBase updateEntity = new KnowledgeBase();
        updateEntity.setId(id);
        updateEntity.setName(dto.getName());
        updateEntity.setDescription(dto.getDescription());
        updateEntity.setCategory(dto.getCategory());
        updateEntity.setRetrievalStrategy(dto.getRetrievalStrategy());
        updateEntity.setChunkStrategy(dto.getChunkStrategy());
        updateEntity.setChunkSize(dto.getChunkSize());
        updateEntity.setChunkOverlap(dto.getChunkOverlap());
        updateEntity.setSeparatorsJson(normalizeSeparatorsJson(dto.getSeparatorsJson()));
        updateEntity.setUpdatedAt(new Date());
        knowledgeBaseMapper.updateById(updateEntity);

        UpdateKnowledgeBaseResultVO result = new UpdateKnowledgeBaseResultVO();
        result.setId(id);
        result.setStrategyChanged(strategyChanged);
        if (strategyChanged) {
            Integer readyDocumentCount = knowledgeBaseMapper.countReadyDocumentsByKbId(id);
            result.setReadyDocumentCount(readyDocumentCount == null ? 0 : readyDocumentCount);
            result.setReprocessTriggered(result.getReadyDocumentCount() > 0);
            result.setSnapshot(buildSnapshot(updateEntity));
        } else {
            result.setReadyDocumentCount(0);
            result.setReprocessTriggered(Boolean.FALSE);
        }
        return result;
    }

    public void deleteById(Long id) {
        KnowledgeBase existing = knowledgeBaseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(2001, "??????");
        }
        knowledgeBaseMapper.logicDeleteById(id);
    }

    public Integer batchDelete(BatchDeleteDTO dto) {
        return knowledgeBaseMapper.logicBatchDelete(dto.getIds());
    }

    public ReprocessResultVO reprocess(Long id, boolean confirmation) {
        if (!confirmation) {
            throw new BusinessException(2003, "???? confirmation=true ???????");
        }
        KnowledgeBase existing = knowledgeBaseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(2001, "??????");
        }
        Integer readyDocumentCount = knowledgeBaseMapper.countReadyDocumentsByKbId(id);
        ReprocessResultVO result = new ReprocessResultVO();
        result.setKbId(id);
        result.setReadyDocumentCount(readyDocumentCount == null ? 0 : readyDocumentCount);
        result.setSnapshot(buildSnapshot(existing));
        if (result.getReadyDocumentCount() > 0) {
            result.setMessage("???????????????????????");
        } else {
            result.setMessage("???? READY ??????????");
        }
        return result;
    }

    private void normalizeQuery(KnowledgeBaseQueryDTO query) {
        if (query.getPage() == null || query.getPage() < 1) {
            query.setPage(1);
        }
        if (query.getPageSize() == null || query.getPageSize() < 1) {
            query.setPageSize(10);
        }
        if (query.getPageSize() > 100) {
            query.setPageSize(100);
        }
    }

    private void validateCategory(String category) {
        if (!"REGULATION".equals(category)
                && !"REPORT_PAPER".equals(category)
                && !"TERM".equals(category)
                && !"GENERAL".equals(category)) {
            throw new BusinessException(4000, "category ??? REGULATION?REPORT_PAPER?TERM?GENERAL");
        }
    }

    private void validateRetrievalStrategy(String retrievalStrategy) {
        if (!"SEMANTIC".equals(retrievalStrategy) && !"VECTOR_RERANK".equals(retrievalStrategy)) {
            throw new BusinessException(4000, "retrievalStrategy ??? SEMANTIC ? VECTOR_RERANK");
        }
    }

    private void validateStrategyFields(String chunkStrategy, Integer chunkSize, Integer chunkOverlap) {
        if (!"HEADING".equals(chunkStrategy) && !"FIXED".equals(chunkStrategy)) {
            throw new BusinessException(4000, "chunkStrategy ??? HEADING ? FIXED");
        }
        if (chunkSize == null || chunkSize <= 0) {
            throw new BusinessException(4000, "chunkSize ???? 0");
        }
        if (chunkOverlap == null || chunkOverlap < 0) {
            throw new BusinessException(4000, "chunkOverlap ???? 0");
        }
        if ("FIXED".equals(chunkStrategy) && chunkOverlap >= chunkSize) {
            throw new BusinessException(4000, "FIXED ??? chunkOverlap ???? chunkSize");
        }
    }


    private String normalizeSeparatorsJson(String separatorsJson) {
        if (separatorsJson == null) {
            return "[]";
        }
        String trimmed = separatorsJson.trim();
        if (trimmed.length() == 0) {
            return "[]";
        }
        return trimmed;
    }

    private void checkNameUnique(String name, Long excludeId) {
        KnowledgeBase existing = knowledgeBaseMapper.selectByName(name);
        if (existing == null) {
            return;
        }
        if (excludeId != null && excludeId.equals(existing.getId())) {
            return;
        }
        throw new BusinessException(2002, "????????");
    }

    private boolean isStrategyChanged(KnowledgeBase existing, KnowledgeBaseUpdateDTO dto) {
        return !equalsNullable(existing.getRetrievalStrategy(), dto.getRetrievalStrategy())
                || !equalsNullable(existing.getChunkStrategy(), dto.getChunkStrategy())
                || !equalsNullable(existing.getChunkSize(), dto.getChunkSize())
                || !equalsNullable(existing.getChunkOverlap(), dto.getChunkOverlap())
                || !equalsNullable(normalizeSeparatorsJson(existing.getSeparatorsJson()), normalizeSeparatorsJson(dto.getSeparatorsJson()));
    }

    private boolean equalsNullable(Object left, Object right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }

    private KnowledgeBaseSnapshotVO buildSnapshot(KnowledgeBase knowledgeBase) {
        KnowledgeBaseSnapshotVO snapshot = new KnowledgeBaseSnapshotVO();
        snapshot.setKbId(knowledgeBase.getId());
        snapshot.setKbName(knowledgeBase.getName());
        snapshot.setRetrievalStrategy(knowledgeBase.getRetrievalStrategy());
        snapshot.setChunkStrategy(knowledgeBase.getChunkStrategy());
        snapshot.setChunkSize(knowledgeBase.getChunkSize());
        snapshot.setChunkOverlap(knowledgeBase.getChunkOverlap());
        snapshot.setSeparatorsJson(knowledgeBase.getSeparatorsJson());
        snapshot.setOperatorUserId(0L);
        snapshot.setOperatorName("system");
        snapshot.setTriggerReason("STRATEGY_CHANGED");
        return snapshot;
    }
}

