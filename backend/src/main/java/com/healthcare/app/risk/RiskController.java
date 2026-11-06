package com.healthcare.app.risk;

import com.healthcare.app.common.PageResponse;
import com.healthcare.app.common.Paging;
import com.healthcare.app.exception.ApiException;
import com.healthcare.app.risk.RiskDtos.HistoryItem;
import com.healthcare.app.risk.RiskDtos.RiskRequest;
import com.healthcare.app.risk.RiskDtos.RiskResponse;
import com.healthcare.app.user.User;
import com.healthcare.app.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/risk")
@PreAuthorize("hasRole('PATIENT')")
@Tag(name = "Risk check", description = "Diabetes risk estimate with saved history")
public class RiskController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "score", "id");

    private final RiskService service;
    private final UserRepository users;

    public RiskController(RiskService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    @PostMapping("/diabetes")
    @Operation(summary = "Run a diabetes risk check and save it",
            description = "Needs age plus glucose or BMI. 400 if age is under 21 or nothing useful is given; "
                    + "503 if the ML service is down (nothing is saved then).")
    public RiskResponse assess(@Valid @RequestBody RiskRequest req, Authentication auth) {
        return service.assess(currentUser(auth), req);
    }

    @GetMapping("/history")
    @Operation(summary = "My saved risk checks (paged, newest first)")
    public PageResponse<HistoryItem> history(
            @Parameter(description = "Only this level") @RequestParam(required = false) RiskAssessment.Level level,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1-100") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "field,direction. Fields: createdAt, score, id") @RequestParam(required = false) String sort,
            Authentication auth) {
        var pageable = Paging.of(page, size, sort, SORTABLE, Sort.by(Sort.Direction.DESC, "createdAt"));
        return service.history(currentUser(auth), level, pageable);
    }

    @DeleteMapping("/history/{id}")
    @Operation(summary = "Delete one saved check (own records only)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) {
        service.delete(currentUser(auth), id);
    }

    /** Lets people erase all their saved health checks in one go. */
    @DeleteMapping("/history")
    @Operation(summary = "Delete all my saved checks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAll(Authentication auth) {
        service.deleteAll(currentUser(auth));
    }

    private User currentUser(Authentication auth) {
        return users.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
    }
}
