package com.campusfootball.rating;

import com.campusfootball.audit.AuditAction;
import com.campusfootball.audit.AuditService;
import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.team.AuthorizationService;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SensitiveTermService {
    private static final int MAX_IMPORT_SIZE = 5000;

    private final SensitiveTermMapper mapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private volatile SensitiveWordBs blockWords = emptyMatcher();
    private volatile SensitiveWordBs reviewWords = emptyMatcher();

    public SensitiveTermService(SensitiveTermMapper mapper, AuthorizationService authorizationService,
                                AuditService auditService) {
        this.mapper = mapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        refreshTerms();
    }

    public ModerationResult classify(String text) {
        if (text == null || text.isBlank()) {
            return ModerationResult.pass();
        }
        String blocked = blockWords.findFirst(text);
        if (blocked != null) {
            return ModerationResult.block(blocked);
        }
        String review = reviewWords.findFirst(text);
        return review == null ? ModerationResult.pass() : ModerationResult.review(review);
    }

    public List<SensitiveTerm> findAll() {
        return mapper.findAll();
    }

    @Transactional
    public long create(long actorUserId, String term, String category, SensitiveTermAction action) {
        authorizationService.requireAdmin(actorUserId);
        String cleaned = validateTerm(term);
        if (mapper.findByTerm(cleaned).isPresent()) {
            throw conflict("敏感词已存在");
        }
        SensitiveTerm entity = new SensitiveTerm();
        entity.setTerm(cleaned);
        entity.setCategory(validateCategory(category));
        entity.setAction(action == null ? SensitiveTermAction.REVIEW : action);
        entity.setEnabled(true);
        entity.setCreatedBy(actorUserId);
        mapper.insert(entity);
        auditService.record(actorUserId, null, AuditAction.CREATE, "SENSITIVE_TERM", entity.getId(), null, entity);
        refreshTerms();
        return entity.getId();
    }

    @Transactional
    public void update(long actorUserId, long id, String category, SensitiveTermAction action, boolean enabled) {
        authorizationService.requireAdmin(actorUserId);
        SensitiveTerm existing = mapper.findById(id).orElseThrow(() -> notFound("敏感词不存在"));
        SensitiveTerm updated = new SensitiveTerm();
        updated.setId(id);
        updated.setTerm(existing.getTerm());
        updated.setCategory(validateCategory(category));
        updated.setAction(action);
        updated.setEnabled(enabled);
        updated.setCreatedBy(existing.getCreatedBy());
        mapper.update(updated);
        auditService.record(actorUserId, null, AuditAction.UPDATE, "SENSITIVE_TERM", id, existing, updated);
        refreshTerms();
    }

    @Transactional
    public void delete(long actorUserId, long id) {
        authorizationService.requireAdmin(actorUserId);
        SensitiveTerm existing = mapper.findById(id).orElseThrow(() -> notFound("敏感词不存在"));
        mapper.delete(id);
        auditService.record(actorUserId, null, AuditAction.DELETE, "SENSITIVE_TERM", id, existing, null);
        refreshTerms();
    }

    @Transactional
    public int importTerms(long actorUserId, List<String> terms, SensitiveTermAction action) {
        authorizationService.requireAdmin(actorUserId);
        if (terms == null || terms.isEmpty()) {
            throw badRequest("导入内容不能为空");
        }
        if (terms.size() > MAX_IMPORT_SIZE) {
            throw badRequest("单次最多导入 5000 个敏感词");
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String raw : terms) {
            String cleaned = validateTerm(raw);
            unique.putIfAbsent(cleaned, cleaned);
        }
        int inserted = 0;
        for (String cleaned : unique.values()) {
            if (mapper.findByTerm(cleaned).isPresent()) {
                continue;
            }
            SensitiveTerm entity = new SensitiveTerm();
            entity.setTerm(cleaned);
            entity.setCategory("导入");
            entity.setAction(action == null ? SensitiveTermAction.REVIEW : action);
            entity.setEnabled(true);
            entity.setCreatedBy(actorUserId);
            mapper.insert(entity);
            inserted++;
        }
        auditService.record(actorUserId, null, AuditAction.CREATE, "SENSITIVE_TERM_IMPORT", 0,
                null, Map.of("count", inserted, "action", action));
        refreshTerms();
        return inserted;
    }

    public synchronized void refreshTerms() {
        List<String> block = new ArrayList<>();
        List<String> review = new ArrayList<>();
        for (SensitiveTerm term : mapper.findEnabled()) {
            if (term.getAction() == SensitiveTermAction.BLOCK) {
                block.add(term.getTerm());
            } else {
                review.add(term.getTerm());
            }
        }
        blockWords = buildMatcher(block);
        reviewWords = buildMatcher(review);
    }

    private SensitiveWordBs emptyMatcher() {
        return buildMatcher(List.of());
    }

    private SensitiveWordBs buildMatcher(List<String> terms) {
        return SensitiveWordBs.newInstance()
                .wordDeny(() -> terms)
                .ignoreCase(true)
                .ignoreWidth(true)
                .ignoreChineseStyle(true)
                .ignoreEnglishStyle(true)
                .ignoreRepeat(true)
                .init();
    }

    private String validateTerm(String term) {
        String cleaned = term == null ? "" : term.trim();
        if (cleaned.isEmpty() || cleaned.length() > 64) {
            throw badRequest("敏感词长度必须为 1–64 个字符");
        }
        return cleaned;
    }

    private String validateCategory(String category) {
        String cleaned = category == null ? "" : category.trim();
        if (cleaned.isEmpty() || cleaned.length() > 32) {
            throw badRequest("敏感词分类长度必须为 1–32 个字符");
        }
        return cleaned;
    }

    private AppException conflict(String message) {
        return new AppException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message);
    }

    private AppException notFound(String message) {
        return new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }

    private AppException badRequest(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }

}
