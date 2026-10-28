package com.campusfootball.bulkimport;

import com.campusfootball.audit.AuditAction;
import com.campusfootball.audit.AuditService;
import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.team.AuthorizationService;
import com.campusfootball.team.Player;
import com.campusfootball.team.PlayerMapper;
import com.campusfootball.team.PlayerStatus;
import com.campusfootball.team.Team;
import com.campusfootball.team.TeamMapper;
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
public class PlayerImportService {
    private static final List<String> REQUIRED_HEADERS = List.of("号码", "姓名");

    private final TabularFileReader fileReader;
    private final TeamMapper teamMapper;
    private final PlayerMapper playerMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public PlayerImportService(TabularFileReader fileReader, TeamMapper teamMapper, PlayerMapper playerMapper,
                               AuthorizationService authorizationService, AuditService auditService) {
        this.fileReader = fileReader;
        this.teamMapper = teamMapper;
        this.playerMapper = playerMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    public ImportPreview preview(long actorUserId, Long teamId, MultipartFile file, boolean overwrite) {
        Parsed parsed = parse(actorUserId, teamId, file, overwrite);
        return new ImportPreview(parsed.totalRows(), parsed.validRows().size(), parsed.createCount(),
                parsed.updateCount(), parsed.errors(), parsed.sample());
    }

    @Transactional
    public ImportResult commit(long actorUserId, Long teamId, MultipartFile file, boolean overwrite) {
        Parsed parsed = parse(actorUserId, teamId, file, overwrite);
        if (!parsed.errors().isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "导入校验失败，共 " + parsed.errors().size() + " 行错误；请先预览并修正");
        }
        int created = 0;
        int updated = 0;
        for (PlayerRow row : parsed.validRows()) {
            Player existing = playerMapper.findActiveByTeamAndJersey(row.teamId(), row.jerseyNo()).orElse(null);
            Player player = existing == null ? new Player() : existing;
            player.setTeamId(row.teamId());
            player.setName(row.name());
            player.setJerseyNo(row.jerseyNo());
            if (existing == null) player.setPosition(null);
            player.setStatus(PlayerStatus.ACTIVE);
            if (existing == null) {
                playerMapper.insert(player);
                auditService.record(actorUserId, row.teamId(), AuditAction.CREATE,
                        "PLAYER", player.getId(), null, player);
                created++;
            } else {
                playerMapper.update(player);
                auditService.record(actorUserId, row.teamId(), AuditAction.UPDATE,
                        "PLAYER", player.getId(), existing, player);
                updated++;
            }
        }
        return new ImportResult(created, updated, 0);
    }
    private Parsed parse(long actorUserId, Long teamId, MultipartFile file, boolean overwrite) {
        TabularData data = fileReader.read(file);
        for (String required : REQUIRED_HEADERS) {
            if (!data.headers().contains(required)) {
                throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                        "缺少表头：" + required);
            }
        }
        List<ImportError> errors = new ArrayList<>();
        List<PlayerRow> validRows = new ArrayList<>();
        Set<String> seenJerseys = new HashSet<>();
        int createCount = 0;
        int updateCount = 0;
        Team team = teamMapper.findById(teamId == null ? 0 : teamId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "球队不存在"));
        authorizationService.requireCanManageTeam(actorUserId, team.getId());
        for (TabularRow row : data.rows()) {
            String fileTeamName = row.get("球队名称").trim();
            if (!fileTeamName.isBlank() && !fileTeamName.equals(team.getName())) {
                errors.add(new ImportError(row.rowNumber(), "文件球队与所选球队不一致"));
                continue;
            }
            String name = row.get("姓名").trim();
            if (name.isBlank() || name.length() > 64) {
                errors.add(new ImportError(row.rowNumber(), "球员姓名必须为 1–64 个字符"));
                continue;
            }
            int jerseyNo;
            try {
                jerseyNo = Integer.parseInt(row.get("号码").trim());
            } catch (NumberFormatException ex) {
                errors.add(new ImportError(row.rowNumber(), "球衣号码必须是整数"));
                continue;
            }
            if (jerseyNo < 1 || jerseyNo > 99) {
                errors.add(new ImportError(row.rowNumber(), "球衣号码必须在 1–99 之间"));
                continue;
            }
            String key = team.getId() + ":" + jerseyNo;
            if (!seenJerseys.add(key)) {
                errors.add(new ImportError(row.rowNumber(), "文件内同队球衣号码重复"));
                continue;
            }
            boolean exists = playerMapper.findActiveByTeamAndJersey(team.getId(), jerseyNo).isPresent();
            if (exists && !overwrite) {
                errors.add(new ImportError(row.rowNumber(), "同队号码已存在；如需更新请开启覆盖"));
                continue;
            }
            if (exists) updateCount++; else createCount++;
            validRows.add(new PlayerRow(row.rowNumber(), team.getId(), name, jerseyNo));
        }
        List<Map<String, String>> sample = data.rows().stream().limit(20)
                .map(row -> (Map<String, String>) new LinkedHashMap<>(row.values())).toList();
        return new Parsed(data.rows().size(), validRows, errors, createCount, updateCount, sample);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record PlayerRow(int rowNumber, long teamId, String name, int jerseyNo) {
    }

    private record Parsed(int totalRows, List<PlayerRow> validRows, List<ImportError> errors,
                          int createCount, int updateCount, List<Map<String, String>> sample) {
    }
}