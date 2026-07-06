package io.github.forrestknight.buoy.api;

import io.github.forrestknight.buoy.config.ApiKeyAuthentication;
import io.github.forrestknight.buoy.domain.EvaluationContext;
import io.github.forrestknight.buoy.domain.EvaluationReason;
import io.github.forrestknight.buoy.domain.EvaluationResult;
import io.github.forrestknight.buoy.service.EvaluationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The SDK-facing evaluation API. Authenticated by SERVER_SDK keys only; the
 * environment is implied by the key, never sent by the client.
 */
@RestController
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    public record EvaluateRequest(
            @NotBlank String key,
            Map<String, Object> attributes,
            Boolean defaultValue) {

        EvaluationContext toContext() {
            return new EvaluationContext(key, attributes);
        }

        boolean defaultOrFalse() {
            return Boolean.TRUE.equals(defaultValue);
        }
    }

    public record EvaluateResponse(String flagKey, boolean value, EvaluationReason reason,
                                   String matchedRuleId) {
    }

    public record BulkEvaluateResponse(Map<String, EvaluateResponse> flags) {
    }

    @PostMapping("/api/v1/evaluate/{flagKey}")
    public EvaluateResponse evaluate(@PathVariable String flagKey,
                                     @Valid @RequestBody EvaluateRequest request,
                                     ApiKeyAuthentication authentication) {
        EvaluationResult result = evaluationService.evaluate(
                authentication.getPrincipal().environmentId(), flagKey,
                request.toContext(), request.defaultOrFalse());
        return new EvaluateResponse(flagKey, result.value(), result.reason(), result.matchedRuleId());
    }

    @PostMapping("/api/v1/evaluate")
    public BulkEvaluateResponse evaluateAll(@Valid @RequestBody EvaluateRequest request,
                                            ApiKeyAuthentication authentication) {
        Map<String, EvaluationResult> results = evaluationService.evaluateAll(
                authentication.getPrincipal().environmentId(), request.toContext());
        Map<String, EvaluateResponse> flags = new LinkedHashMap<>();
        results.forEach((flagKey, result) -> flags.put(flagKey,
                new EvaluateResponse(flagKey, result.value(), result.reason(), result.matchedRuleId())));
        return new BulkEvaluateResponse(flags);
    }
}
