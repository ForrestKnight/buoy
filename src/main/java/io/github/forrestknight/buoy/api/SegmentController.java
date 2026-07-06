package io.github.forrestknight.buoy.api;

import io.github.forrestknight.buoy.api.SegmentDtos.CreateSegmentRequest;
import io.github.forrestknight.buoy.api.SegmentDtos.SegmentResponse;
import io.github.forrestknight.buoy.api.SegmentDtos.UpdateSegmentRequest;
import io.github.forrestknight.buoy.service.SegmentService;
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
@RequestMapping("/api/v1/projects/{projectKey}/segments")
public class SegmentController {

    private final SegmentService segmentService;

    public SegmentController(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    @PostMapping
    @PreAuthorize("@projectAccess.canEdit(authentication, #projectKey)")
    public ResponseEntity<SegmentResponse> create(@PathVariable String projectKey,
                                                  @Valid @RequestBody CreateSegmentRequest request,
                                                  UriComponentsBuilder uri) {
        SegmentResponse response = SegmentResponse.from(
                segmentService.create(projectKey, request.key(), request.name(),
                        request.description(), request.clauses()));
        return ResponseEntity
                .created(uri.path("/api/v1/projects/{p}/segments/{s}")
                        .buildAndExpand(projectKey, response.key()).toUri())
                .body(response);
    }

    @GetMapping
    @PreAuthorize("@projectAccess.canRead(authentication, #projectKey)")
    public List<SegmentResponse> list(@PathVariable String projectKey) {
        return segmentService.list(projectKey).stream().map(SegmentResponse::from).toList();
    }

    @GetMapping("/{segmentKey}")
    @PreAuthorize("@projectAccess.canRead(authentication, #projectKey)")
    public SegmentResponse get(@PathVariable String projectKey, @PathVariable String segmentKey) {
        return SegmentResponse.from(segmentService.get(projectKey, segmentKey));
    }

    @PutMapping("/{segmentKey}")
    @PreAuthorize("@projectAccess.canEdit(authentication, #projectKey)")
    public SegmentResponse update(@PathVariable String projectKey, @PathVariable String segmentKey,
                                  @Valid @RequestBody UpdateSegmentRequest request) {
        return SegmentResponse.from(segmentService.update(projectKey, segmentKey, request.name(),
                request.description(), request.clauses()));
    }

    @DeleteMapping("/{segmentKey}")
    @PreAuthorize("@projectAccess.canEdit(authentication, #projectKey)")
    public ResponseEntity<Void> delete(@PathVariable String projectKey, @PathVariable String segmentKey) {
        segmentService.delete(projectKey, segmentKey);
        return ResponseEntity.noContent().build();
    }
}
