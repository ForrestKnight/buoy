package io.github.forrestknight.buoy.api;

import io.github.forrestknight.buoy.domain.ProjectMember;
import io.github.forrestknight.buoy.domain.ProjectRole;
import io.github.forrestknight.buoy.service.ProjectMemberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectKey}/members")
public class ProjectMemberController {

    private final ProjectMemberService memberService;

    public ProjectMemberController(ProjectMemberService memberService) {
        this.memberService = memberService;
    }

    public record AssignMemberRequest(@NotNull ProjectRole role) {
    }

    public record MemberResponse(String username, String displayName, ProjectRole role, Instant since) {

        static MemberResponse from(ProjectMember member) {
            return new MemberResponse(member.getUser().getUsername(), member.getUser().getDisplayName(),
                    member.getRole(), member.getCreatedAt());
        }
    }

    @PutMapping("/{username}")
    @PreAuthorize("@projectAccess.isOwner(authentication, #projectKey)")
    public MemberResponse assign(@PathVariable String projectKey, @PathVariable String username,
                                 @Valid @RequestBody AssignMemberRequest request) {
        return MemberResponse.from(memberService.assign(projectKey, username, request.role()));
    }

    @GetMapping
    @PreAuthorize("@projectAccess.canRead(authentication, #projectKey)")
    public List<MemberResponse> list(@PathVariable String projectKey) {
        return memberService.list(projectKey).stream().map(MemberResponse::from).toList();
    }

    @DeleteMapping("/{username}")
    @PreAuthorize("@projectAccess.isOwner(authentication, #projectKey)")
    public ResponseEntity<Void> remove(@PathVariable String projectKey, @PathVariable String username) {
        memberService.remove(projectKey, username);
        return ResponseEntity.noContent().build();
    }
}
