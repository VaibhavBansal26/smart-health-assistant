package com.healthcare.app.user;

import com.healthcare.app.common.PageResponse;
import com.healthcare.app.common.Paging;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/doctors")
@Tag(name = "Doctors", description = "Browse doctors")
public class DoctorController {

    private static final Set<String> SORTABLE = Set.of("fullName", "specialty", "id");

    private final UserRepository users;

    public DoctorController(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    @Operation(summary = "List doctors (paged, searchable)")
    public PageResponse<UserSummary> list(
            @Parameter(description = "Exact specialty, case-insensitive (e.g. Cardiology)")
            @RequestParam(required = false) String specialty,
            @Parameter(description = "Search in name or specialty")
            @RequestParam(required = false) String q,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1-100") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "field,direction. Fields: fullName, specialty, id") @RequestParam(required = false) String sort) {

        var pageable = Paging.of(page, size, sort, SORTABLE, Sort.by(Sort.Direction.ASC, "fullName"));
        // deactivated doctors are not offered to patients
        Specification<User> spec = (root, query, cb) -> cb.and(cb.equal(root.get("role"), Role.DOCTOR), cb.isTrue(root.get("active")));

        if (specialty != null && !specialty.isBlank()) {
            String wanted = specialty.trim().toLowerCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("specialty")), wanted));
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullName")), like, '\\'),
                    cb.like(cb.lower(root.get("specialty")), like, '\\')));
        }
        return PageResponse.of(users.findAll(spec, pageable), UserSummary::from);
    }

    /** Treat user input as plain text: neutralise LIKE wildcards. */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
