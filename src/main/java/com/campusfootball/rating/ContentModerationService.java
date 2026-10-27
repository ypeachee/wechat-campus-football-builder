package com.campusfootball.rating;

import com.campusfootball.user.AppUser;
import com.campusfootball.user.AppUserMapper;
import org.springframework.stereotype.Service;

@Service
public class ContentModerationService {
    private final SensitiveTermService sensitiveTermService;
    private final WechatContentSecurityClient wechatContentSecurityClient;
    private final AppUserMapper appUserMapper;

    public ContentModerationService(SensitiveTermService sensitiveTermService,
                                    WechatContentSecurityClient wechatContentSecurityClient,
                                    AppUserMapper appUserMapper) {
        this.sensitiveTermService = sensitiveTermService;
        this.wechatContentSecurityClient = wechatContentSecurityClient;
        this.appUserMapper = appUserMapper;
    }

    public ModerationResult evaluate(long userId, String content) {
        if (content == null || content.isBlank()) {
            return ModerationResult.pass();
        }
        ModerationResult local = sensitiveTermService.classify(content);
        if (local.decision() != ModerationDecision.PASS) {
            return local;
        }
        AppUser user = appUserMapper.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Rating user does not exist"));
        ModerationDecision decision = wechatContentSecurityClient.check(user.getWechatOpenid(), content);
        return switch (decision) {
            case PASS -> ModerationResult.pass();
            case REVIEW -> ModerationResult.review(null);
            case BLOCK -> ModerationResult.block(null);
        };
    }
}
