package com.campusfootball.rating;

import com.campusfootball.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveTermServiceTest extends IntegrationTestBase {
    @Autowired
    private SensitiveTermService service;

    @BeforeEach
    void refreshAfterDatabaseReset() {
        service.refreshTerms();
    }

    @Test
    void classifiesDatabaseTermsWithBlockPrecedence() {
        long admin = data.findUserId("openid-admin");
        service.create(admin, "禁发词甲", "测试", SensitiveTermAction.BLOCK);
        service.create(admin, "审核词乙", "测试", SensitiveTermAction.REVIEW);
        service.create(admin, "BadWord", "测试", SensitiveTermAction.REVIEW);

        assertThat(service.classify("包含禁发词甲的内容").decision()).isEqualTo(ModerationDecision.BLOCK);
        assertThat(service.classify("包含审核词乙的内容").decision()).isEqualTo(ModerationDecision.REVIEW);
        assertThat(service.classify("禁发词甲和审核词乙同时出现").decision()).isEqualTo(ModerationDecision.BLOCK);
        assertThat(service.classify("BADWORD").decision()).isEqualTo(ModerationDecision.REVIEW);
        assertThat(service.classify("正常评价").decision()).isEqualTo(ModerationDecision.PASS);
        assertThat(service.classify("  ").decision()).isEqualTo(ModerationDecision.PASS);
    }

    @Test
    void disablingTermStopsMatchingAfterRefresh() {
        long admin = data.findUserId("openid-admin");
        long id = service.create(admin, "临时审核词", "测试", SensitiveTermAction.REVIEW);
        assertThat(service.classify("临时审核词").decision()).isEqualTo(ModerationDecision.REVIEW);

        service.update(admin, id, "测试", SensitiveTermAction.REVIEW, false);

        assertThat(service.classify("临时审核词").decision()).isEqualTo(ModerationDecision.PASS);
    }
}
