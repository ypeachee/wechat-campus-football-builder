package com.campusfootball.bulkimport;

import com.campusfootball.audit.AuditAction;
import com.campusfootball.audit.AuditService;
import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.team.AuthorizationService;
import com.campusfootball.team.Team;
import com.campusfootball.team.TeamMapper;
import com.campusfootball.team.TeamStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TeamImportService {
    private static final List<String> REQUIRED_HEADERS = List.of("球队名称", "简称", "教练", "主场", "状态");

    private final TabularFileReader fileReader;
    private final TeamMapper teamMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public TeamImportService(TabularFileReader fileReader, TeamMapper teamMapper,
                             AuthorizationService authorizationService, AuditService auditService) {
        this.fileReader = fileReader;
        this.teamMapper = teamMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    public ImportPreview preview(long actorUserId, MultipartFile file, boolean overwrite) {
        authorizationService.requireManagementAccess(actorUserId);
        Parsed parsed = parse(file, overwrite);
        return new ImportPreview(parsed.totalRows(), parsed.validRows().size(), parsed.createCount(),
                parsed.updateCount(), parsed.errors(), parsed.sample());
    }

    @Transactional
    public ImportResult commit(long actorUserId, MultipartFile file, boolean overwrite) {
        authorizationService.requireManagementAccess(actorUserId);
        Parsed parsed = parse(file, overwrite);
        if (!parsed.errors().isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "导入校验失败，共 " + parsed.errors().size() + " 行错误；请先预览并修正");
        }
        int created = 0;
        int updated = 0;
        for (TeamRow row : parsed.validRows()) {
            Team existing = teamMapper.findFirstByName(row.name()).orElse(null);
            Team team = existing == null ? new Team() : existing;
            team.setName(row.name());
            team.setShortName(blankToNull(row.shortName()));
            team.setCoachName(blankToNull(row.coach()));
            team.setHomeVenue(blankToNull(row.venue()));
            team.setStatus(row.status());
            if (existing == null) {
                teamMapper.insert(team);
                auditService.record(actorUserId, null, AuditAction.CREATE, "TEAM", team.getId(), null, team);
                created++;
            } else {
                teamMapper.update(team);
                auditService.record(actorUserId, null, AuditAction.UPDATE, "TEAM", team.getId(), existing, team);
                updated++;
            }
        }
        return new ImportResult(created, updated, 0);
    }

    private Parsed parse(MultipartFile file, boolean overwrite) {
        TabularData data = fileReader.read(file);
        for (String required : REQUIRED_HEADERS) {
            if (!data.headers().contains(required)) {
                throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                        "缺少表头：" + required);
            }
        }
        List<ImportError> errors = new ArrayList<>();
        List<TeamRow> validRows = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        int createCount = 0;
        int updateCount = 0;
        for (TabularRow row : data.rows()) {
            String name = row.get("球队名称").trim();
            if (name.isBlank() || name.length() > 100) {
                errors.add(new ImportError(row.rowNumber(), "球队名称必须为 1–100 个字符"));
                continue;
            }
            if (!seenNames.add(name.toLowerCase(Locale.ROOT))) {
                errors.add(new ImportError(row.rowNumber(), "文件内球队名称重复"));
                continue;
            }
            String shortName = row.get("简称").trim();
            String coach = row.get("教练").trim();
            String venue = row.get("主场").trim();
            if (shortName.length() > 32 || coach.length() > 64 || venue.length() > 128) {
                errors.add(new ImportError(row.rowNumber(), "简称、教练或主场长度超限"));
                continue;
            }
            TeamStatus status;
            try {
                status = parseStatus(row.get("状态"));
            } catch (IllegalArgumentException ex) {
                errors.add(new ImportError(row.rowNumber(), ex.getMessage()));
                continue;
            }
            Team existing = teamMapper.findFirstByName(name).orElse(null);
            if (existing != null && !overwrite) {
                errors.add(new ImportError(row.rowNumber(), "球队已存在；如需更新请开启覆盖"));
                continue;
            }
            if (existing == null) createCount++; else updateCount++;
            validRows.add(new TeamRow(row.rowNumber(), name, shortName, coach, venue, status));
        }
        List<Map<String, String>> sample = data.rows().stream().limit(20)
                .map(row -> (Map<String, String>) new LinkedHashMap<>(row.values())).toList();
        return new Parsed(data.rows().size(), validRows, errors, createCount, updateCount, sample);
    }

    private TeamStatus parseStatus(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.equals("ACTIVE") || normalized.equals("启用")) {
            return TeamStatus.ACTIVE;
        }
        if (normalized.equals("INACTIVE") || normalized.equals("停用")) {
            return TeamStatus.INACTIVE;
        }
        throw new IllegalArgumentException("状态只能填写 ACTIVE/INACTIVE 或 启用/停用");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record TeamRow(int rowNumber, String name, String shortName, String coach,
                           String venue, TeamStatus status) {
    }

    private record Parsed(int totalRows, List<TeamRow> validRows, List<ImportError> errors,
                          int createCount, int updateCount, List<Map<String, String>> sample) {
    }
}