package io.github.forrestknight.buoy.api;

import io.github.forrestknight.buoy.api.EnvironmentDtos.CreateEnvironmentRequest;
import io.github.forrestknight.buoy.api.EnvironmentDtos.EnvironmentResponse;
import io.github.forrestknight.buoy.api.EnvironmentDtos.UpdateEnvironmentRequest;
import io.github.forrestknight.buoy.service.EnvironmentService;
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

/**
 * Environments shape the project (and carry its SDK keys), so mutations
 * require OWNER; reading requires membership.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectKey}/environments")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    public EnvironmentController(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    @PostMapping
    @PreAuthorize("@projectAccess.isOwner(authentication, #projectKey)")
    public ResponseEntity<EnvironmentResponse> create(@PathVariable String projectKey,
                                                      @Valid @RequestBody CreateEnvironmentRequest request,
                                                      UriComponentsBuilder uri) {
        EnvironmentResponse response = EnvironmentResponse.from(
                environmentService.create(projectKey, request.key(), request.name()));
        return ResponseEntity
                .created(uri.path("/api/v1/projects/{p}/environments/{e}")
                        .buildAndExpand(projectKey, response.key()).toUri())
                .body(response);
    }

    @GetMapping
    @PreAuthorize("@projectAccess.canRead(authentication, #projectKey)")
    public List<EnvironmentResponse> list(@PathVariable String projectKey) {
        return environmentService.list(projectKey).stream().map(EnvironmentResponse::from).toList();
    }

    @GetMapping("/{environmentKey}")
    @PreAuthorize("@projectAccess.canRead(authentication, #projectKey)")
    public EnvironmentResponse get(@PathVariable String projectKey, @PathVariable String environmentKey) {
        return EnvironmentResponse.from(environmentService.get(projectKey, environmentKey));
    }

    @PutMapping("/{environmentKey}")
    @PreAuthorize("@projectAccess.isOwner(authentication, #projectKey)")
    public EnvironmentResponse update(@PathVariable String projectKey, @PathVariable String environmentKey,
                                      @Valid @RequestBody UpdateEnvironmentRequest request) {
        return EnvironmentResponse.from(environmentService.update(projectKey, environmentKey, request.name()));
    }

    @DeleteMapping("/{environmentKey}")
    @PreAuthorize("@projectAccess.isOwner(authentication, #projectKey)")
    public ResponseEntity<Void> delete(@PathVariable String projectKey, @PathVariable String environmentKey) {
        environmentService.delete(projectKey, environmentKey);
        return ResponseEntity.noContent().build();
    }
}
