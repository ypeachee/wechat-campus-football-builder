package com.campusfootball.news;

import com.campusfootball.audit.AuditAction;
import com.campusfootball.audit.AuditService;
import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.team.AuthorizationService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class SiteNewsService {
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final SiteNewsMapper newsMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public SiteNewsService(SiteNewsMapper newsMapper, AuthorizationService authorizationService,
                           AuditService auditService) {
        this.newsMapper = newsMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    public PublicFeed publicFeed(int limit) {
        int safeLimit = Math.max(1, Math.min(12, limit));
        boolean managed = newsMapper.countAll() > 0;
        return new PublicFeed(managed, newsMapper.findPublic(safeLimit).stream().map(this::publicView).toList());
    }

    public SiteNews image(long id) {
        return newsMapper.findById(id).orElseThrow(() -> notFound("新闻图片不存在"));
    }

    public List<NewsView> adminList(long actorUserId) {
        authorizationService.requireAdmin(actorUserId);
        return newsMapper.findAll().stream().map(this::adminView).toList();
    }

    @Transactional
    public NewsView create(long actorUserId, String title, String description, String tag,
                           LocalDate publishedAt, int displayOrder, boolean active, MultipartFile image) {
        authorizationService.requireAdmin(actorUserId);
        SiteNews news = new SiteNews();
        applyMetadata(news, title, description, tag, publishedAt, displayOrder, active);
        applyRequiredImage(news, image);
        news.setCreatedBy(actorUserId);
        newsMapper.insert(news);
        SiteNews created = newsMapper.findById(news.getId()).orElseThrow();
        auditService.record(actorUserId, null, AuditAction.CREATE, "SITE_NEWS", created.getId(), null, created);
        return adminView(created);
    }

    @Transactional
    public NewsView update(long actorUserId, long id, String title, String description, String tag,
                           LocalDate publishedAt, int displayOrder, boolean active, MultipartFile image) {
        authorizationService.requireAdmin(actorUserId);
        SiteNews existing = newsMapper.findById(id).orElseThrow(() -> notFound("新闻不存在"));
        SiteNews before = copy(existing);
        applyMetadata(existing, title, description, tag, publishedAt, displayOrder, active);
        if (image != null && !image.isEmpty()) {
            applyRequiredImage(existing, image);
        }
        newsMapper.update(existing);
        SiteNews updated = newsMapper.findById(id).orElseThrow();
        auditService.record(actorUserId, null, AuditAction.UPDATE, "SITE_NEWS", id, before, updated);
        return adminView(updated);
    }

    @Transactional
    public void delete(long actorUserId, long id) {
        authorizationService.requireAdmin(actorUserId);
        SiteNews existing = newsMapper.findById(id).orElseThrow(() -> notFound("新闻不存在"));
        newsMapper.delete(id);
        auditService.record(actorUserId, null, AuditAction.DELETE, "SITE_NEWS", id, existing, null);
    }

    @Transactional
    public List<NewsView> seedDefaults(long actorUserId) {
        authorizationService.requireAdmin(actorUserId);
        if (newsMapper.countAll() > 0) {
            throw new AppException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "新闻栏已有内容，不能重复导入默认新闻");
        }
        insertDefault(actorUserId, "北地女足主场集结", "校园足球在学院路29号开场，绿茵与看台一起见证比赛。", "主场亮相", 10, "cugb-match-team.jpg");
        insertDefault(actorUserId, "绿茵场上的攻防转换", "奔跑、拼抢和传递，构成北地校园足球最鲜活的现场。", "赛场实况", 20, "cugb-match-action.jpg");
        insertDefault(actorUserId, "赛前集结，准备开场", "队员们在球场列队，迎接下一场校园足球较量。", "赛前时刻", 30, "cugb-match-lineup.jpg");
        insertDefault(actorUserId, "球门前的关键一刻", "禁区内的争抢与扑救，让每一场比赛都充满悬念。", "门前瞬间", 40, "cugb-match-goal.jpg");
        return newsMapper.findAll().stream().map(this::adminView).toList();
    }

    private void insertDefault(long actorUserId, String title, String description, String tag,
                               int displayOrder, String filename) {
        try {
            ClassPathResource resource = new ClassPathResource("static/assets/" + filename);
            SiteNews news = new SiteNews();
            news.setTitle(title);
            news.setDescription(description);
            news.setTag(tag);
            news.setPublishedAt(LocalDate.of(2026, 10, 26));
            news.setDisplayOrder(displayOrder);
            news.setActive(true);
            news.setImageData(resource.getInputStream().readAllBytes());
            news.setImageContentType(filename.endsWith(".png") ? "image/png" : "image/jpeg");
            news.setImageFilename(filename);
            news.setCreatedBy(actorUserId);
            newsMapper.insert(news);
        } catch (IOException error) {
            throw new IllegalStateException("无法读取默认新闻图片: " + filename, error);
        }
    }

    private void applyMetadata(SiteNews news, String title, String description, String tag,
                               LocalDate publishedAt, int displayOrder, boolean active) {
        String safeTitle = normalize(title, 120, "新闻标题必须为 1–120 个字符");
        String safeDescription = normalize(description, 300, "新闻说明必须为 1–300 个字符");
        String safeTag = normalize(tag, 40, "新闻分类必须为 1–40 个字符");
        if (displayOrder < 0 || displayOrder > 9999) {
            throw badRequest("排序值必须在 0–9999 之间");
        }
        news.setTitle(safeTitle);
        news.setDescription(safeDescription);
        news.setTag(safeTag);
        news.setPublishedAt(publishedAt == null ? LocalDate.now() : publishedAt);
        news.setDisplayOrder(displayOrder);
        news.setActive(active);
    }

    private void applyRequiredImage(SiteNews news, MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw badRequest("请选择新闻图片");
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw badRequest("新闻图片不能超过 5MB");
        }
        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw badRequest("新闻图片仅支持 JPG、PNG 或 WebP");
        }
        try {
            news.setImageData(image.getBytes());
            news.setImageContentType(contentType);
            news.setImageFilename(image.getOriginalFilename());
        } catch (IOException error) {
            throw badRequest("无法读取新闻图片");
        }
    }

    private String normalize(String value, int maxLength, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > maxLength) {
            throw badRequest(message);
        }
        return normalized;
    }

    private PublicNewsView publicView(SiteNews news) {
        return new PublicNewsView(news.getId(), news.getTitle(), news.getDescription(), news.getTag(),
                news.getPublishedAt(), imageUrl(news.getId()));
    }

    private NewsView adminView(SiteNews news) {
        return new NewsView(news.getId(), news.getTitle(), news.getDescription(), news.getTag(),
                news.getPublishedAt(), news.getDisplayOrder(), news.isActive(), imageUrl(news.getId()),
                news.getImageFilename(), news.getCreatedAt());
    }

    private String imageUrl(long id) {
        return "/api/v1/news/" + id + "/image";
    }

    private SiteNews copy(SiteNews source) {
        SiteNews copy = new SiteNews();
        copy.setId(source.getId());
        copy.setTitle(source.getTitle());
        copy.setDescription(source.getDescription());
        copy.setTag(source.getTag());
        copy.setPublishedAt(source.getPublishedAt());
        copy.setImageData(source.getImageData());
        copy.setImageContentType(source.getImageContentType());
        copy.setImageFilename(source.getImageFilename());
        copy.setDisplayOrder(source.getDisplayOrder());
        copy.setActive(source.isActive());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setCreatedAt(source.getCreatedAt());
        return copy;
    }

    private AppException badRequest(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }

    private AppException notFound(String message) {
        return new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }

    public record PublicNewsView(long id, String title, String description, String tag,
                                 LocalDate publishedAt, String imageUrl) {
    }

    public record PublicFeed(boolean managed, List<PublicNewsView> items) {
    }

    public record NewsView(long id, String title, String description, String tag,
                           LocalDate publishedAt, int displayOrder, boolean active, String imageUrl,
                           String imageFilename, LocalDateTime createdAt) {
    }
}
