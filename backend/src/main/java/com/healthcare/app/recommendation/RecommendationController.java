package com.healthcare.app.recommendation;

import com.healthcare.app.auth.VerifiedEmailGuard;
import com.healthcare.app.recommendation.RecommendationDtos.RecommendRequest;
import com.healthcare.app.recommendation.RecommendationDtos.RecommendResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@Tag(name = "Doctor recommender", description = "Symptoms to specialty to ranked doctors")
public class RecommendationController {

    private final RecommendationService service;
    private final VerifiedEmailGuard verified;

    public RecommendationController(RecommendationService service, VerifiedEmailGuard verified) {
        this.service = service;
        this.verified = verified;
    }

    @PostMapping
    @Operation(summary = "Suggest specialties and doctors for described symptoms (patients only)",
            description = "Emergency wording is caught by fixed rules before the ML model runs (response has `emergency: true` and no doctors).")
    @PreAuthorize("hasRole('PATIENT')")
    public RecommendResponse recommend(@Valid @RequestBody RecommendRequest req, Authentication auth) {
        verified.require(auth);
        return service.recommend(req.symptoms().trim());
    }
}
