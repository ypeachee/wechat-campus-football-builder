package com.campusfootball.bulkimport;

import com.campusfootball.common.security.CurrentUser;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/manage/import")
public class ImportController {
    private final TeamImportService teamImportService;
    private final PlayerImportService playerImportService;
    private final CurrentUser currentUser;

    public ImportController(TeamImportService teamImportService, PlayerImportService playerImportService,
                            CurrentUser currentUser) {
        this.teamImportService = teamImportService;
        this.playerImportService = playerImportService;
        this.currentUser = currentUser;
    }

    @PostMapping(value = "/teams/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportPreview previewTeams(@RequestParam("file") MultipartFile file,
                                      @RequestParam(defaultValue = "false") boolean overwrite) {
        return teamImportService.preview(currentUser.require().userId(), file, overwrite);
    }

    @PostMapping(value = "/teams/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult commitTeams(@RequestParam("file") MultipartFile file,
                                    @RequestParam(defaultValue = "false") boolean overwrite) {
        return teamImportService.commit(currentUser.require().userId(), file, overwrite);
    }

    @PostMapping(value = "/players/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportPreview previewPlayers(@RequestParam("file") MultipartFile file,
                                        @RequestParam(defaultValue = "false") boolean overwrite) {
        return playerImportService.preview(currentUser.require().userId(), file, overwrite);
    }

    @PostMapping(value = "/players/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult commitPlayers(@RequestParam("file") MultipartFile file,
                                      @RequestParam(defaultValue = "false") boolean overwrite) {
        return playerImportService.commit(currentUser.require().userId(), file, overwrite);
    }
}