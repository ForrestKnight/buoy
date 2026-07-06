package io.github.forrestknight.buoy.api;

import io.github.forrestknight.buoy.api.FlagDtos.CreateFlagRequest;
import io.github.forrestknight.buoy.api.FlagDtos.FlagConfigResponse;
import io.github.forrestknight.buoy.api.FlagDtos.FlagResponse;
import io.github.forrestknight.buoy.api.FlagDtos.UpdateFlagConfigRequest;
import io.github.forrestknight.buoy.api.FlagDtos.UpdateFlagRequest;
import io.github.forrestknight.buoy.service.FlagService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectKey}/flags")
public class FlagController {

    private final FlagService flagService;

    public FlagController(FlagService flagService) {
        this.flagService = flagService;
    }

    @PostMapping
    @PreAuthorize("@projectAccess.canEdit(authentication, #projectKey)")
    public ResponseEntity<FlagResponse> create(@PathVariable String projectKey,
                                               @Valid @RequestBody CreateFlagRequest request,
                                               UriComponentsBuilder uri) {
        FlagResponse response = FlagResponse.from(
                flagService.create(projectKey, request.key(), request.name(),
                        request.description(), request.tags()));
        return ResponseEntity
                .created(uri.path("/api/v1/projects/{p}/flags/{f}")
                        .buildAndExpand(projectKey, response.key()).toUri())
                .body(response);
    }

    @GetMapping
    @PreAuthorize("@projectAccess.canRead(authentication, #projectKey)")
    public List<FlagResponse> list(@PathVariable String projectKey) {
        return flagService.list(projectKey).stream().map(FlagResponse::from).toList();
    }

    @GetMapping("/{flagKey}")
    @PreAuthorize("@projectAccess.canRead(authentication, #projectKey)")
    public FlagResponse get(@PathVariable String projectKey, @PathVariable String flagKey) {
        return FlagResponse.from(flagService.get(projectKey, flagKey));
    }

    @PutMapping("/{flagKey}")
    @PreAuthorize("@projectAccess.canEdit(authentication, #projectKey)")
    public FlagResponse update(@PathVariable String projectKey, @PathVariable String flagKey,
                               @Valid @RequestBody UpdateFlagRequest request) {
        return FlagResponse.from(flagService.update(projectKey, flagKey, request.name(),
                request.description(), request.tags(), request.archived()));
    }

    @DeleteMapping("/{flagKey}")
    @PreAuthorize("@projectAccess.canEdit(authentication, #projectKey)")
    public ResponseEntity<Void> delete(@PathVariable String projectKey, @PathVariable String flagKey) {
        flagService.delete(projectKey, flagKey);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{flagKey}/config/{environmentKey}")
    @PreAuthorize("@projectAccess.canRead(authentication, #projectKey)")
    public FlagConfigResponse getConfig(@PathVariable String projectKey, @PathVariable String flagKey,
                                        @PathVariable String environmentKey) {
        return FlagConfigResponse.from(flagService.getConfig(projectKey, flagKey, environmentKey));
    }

    @PutMapping("/{flagKey}/config/{environmentKey}")
    @PreAuthorize("@projectAccess.canEdit(authentication, #projectKey)")
    public FlagConfigResponse updateConfig(@PathVariable String projectKey, @PathVariable String flagKey,
                                           @PathVariable String environmentKey,
                                           @Valid @RequestBody UpdateFlagConfigRequest request) {
        return FlagConfigResponse.from(flagService.updateConfig(projectKey, flagKey, environmentKey,
                request.version(), request.enabled(), request.rules(),
                request.defaultVariation(), request.offVariation()));
    }
}
