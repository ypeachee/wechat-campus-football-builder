package com.campusfootball.team;

import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TeamQrCodeService {
    private static final Logger log = LoggerFactory.getLogger(TeamQrCodeService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final TeamMapper teamMapper;
    private final TeamQrCodeClient qrCodeClient;
    private final Clock clock;
    private final ConcurrentHashMap<Long, CachedQrCode> cache = new ConcurrentHashMap<>();

    public TeamQrCodeService(TeamMapper teamMapper, TeamQrCodeClient qrCodeClient, Clock clock) {
        this.teamMapper = teamMapper;
        this.qrCodeClient = qrCodeClient;
        this.clock = clock;
    }

    public byte[] get(long teamId) {
        teamMapper.findById(teamId).orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "球队不存在"));
        Instant now = clock.instant();
        CachedQrCode cached = cache.get(teamId);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.bytes();
        }
        try {
            byte[] bytes = qrCodeClient.fetch(teamId);
            cache.put(teamId, new CachedQrCode(bytes, now.plus(CACHE_TTL)));
            return bytes;
        } catch (IllegalStateException ex) {
            log.warn("Team QR generation failed: teamId={} type={} message={}", teamId, ex.getClass().getSimpleName(), ex.getMessage());
            throw new AppException(HttpStatus.BAD_GATEWAY, ErrorCode.WECHAT_QR_FAILED,
                    "球队小程序码生成失败，请稍后重试");
        }
    }

    private record CachedQrCode(byte[] bytes, Instant expiresAt) {
    }
}