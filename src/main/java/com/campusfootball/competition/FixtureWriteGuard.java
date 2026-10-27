package com.campusfootball.competition;

import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class FixtureWriteGuard {
    private final FixtureMapper fixtureMapper;

    public FixtureWriteGuard(FixtureMapper fixtureMapper) {
        this.fixtureMapper = fixtureMapper;
    }

    public Fixture requireWritable(long fixtureId) {
        Fixture fixture = fixtureMapper.findById(fixtureId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "比赛不存在"));
        if (fixture.isDataLocked()) {
            throw new AppException(HttpStatus.FORBIDDEN, ErrorCode.FIXTURE_LOCKED, "该场比赛的数据已锁定");
        }
        return fixture;
    }
}
