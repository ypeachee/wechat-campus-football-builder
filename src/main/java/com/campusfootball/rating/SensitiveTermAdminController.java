package com.campusfootball.rating;

import com.campusfootball.common.api.IdResponse;
import com.campusfootball.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/sensitive-terms")
public class SensitiveTermAdminController {
    private final SensitiveTermService service;
    private final CurrentUser currentUser;

    public SensitiveTermAdminController(SensitiveTermService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<SensitiveTerm> list() {
        return service.findAll();
    }

    @PostMapping
    public IdResponse create(@Valid @RequestBody CreateTermRequest request) {
        return new IdResponse(service.create(currentUser.require().userId(), request.term(), request.category(), request.action()));
    }

    @PutMapping("/{id}")
    public void update(@PathVariable long id, @Valid @RequestBody UpdateTermRequest request) {
        service.update(currentUser.require().userId(), id, request.category(), request.action(), request.enabled());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        service.delete(currentUser.require().userId(), id);
    }

    @PostMapping("/import")
    public ImportResponse importTerms(@Valid @RequestBody ImportTermsRequest request) {
        int inserted = service.importTerms(currentUser.require().userId(), request.terms(), request.action());
        return new ImportResponse(inserted);
    }

    public record CreateTermRequest(@NotBlank @Size(max = 64) String term,
                                    @NotBlank @Size(max = 32) String category,
                                    @NotNull SensitiveTermAction action) {}
    public record UpdateTermRequest(@NotBlank @Size(max = 32) String category,
                                    @NotNull SensitiveTermAction action,
                                    boolean enabled) {}
    public record ImportTermsRequest(@NotNull List<@NotBlank @Size(max = 64) String> terms,
                                     @NotNull SensitiveTermAction action) {}
    public record ImportResponse(int inserted) {}
}
