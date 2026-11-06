package com.healthcare.app.admin;

import com.healthcare.app.admin.AdminDtos.AdminDoctor;
import com.healthcare.app.admin.AdminDtos.CreateDoctorRequest;
import com.healthcare.app.admin.AdminDtos.UpdateDoctorRequest;
import com.healthcare.app.common.PageResponse;
import com.healthcare.app.common.Paging;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/admin/doctors")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Manage doctor accounts (administrators only)")
public class AdminController {

    private static final Set<String> SORTABLE = Set.of("fullName", "email", "specialty", "id");

    private final AdminDoctorService doctors;

    public AdminController(AdminDoctorService doctors) {
        this.doctors = doctors;
    }

    @GetMapping
    @Operation(summary = "List doctor accounts, including deactivated ones (paged, searchable)")
    public PageResponse<AdminDoctor> list(
            @Parameter(description = "Search in name, email or specialty") @RequestParam(required = false) String q,
            @Parameter(description = "Exact specialty") @RequestParam(required = false) String specialty,
            @Parameter(description = "true = active only, false = deactivated only") @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "field,direction. Fields: fullName, email, specialty, id") @RequestParam(required = false) String sort) {
        var pageable = Paging.of(page, size, sort, SORTABLE, Sort.by(Sort.Direction.ASC, "fullName"));
        return doctors.list(q, specialty, active, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a doctor account and email them a link to choose their password",
            description = "409 if the email is already registered.")
    public AdminDoctor create(@Valid @RequestBody CreateDoctorRequest req) {
        return doctors.create(req);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Edit a doctor, or deactivate/reactivate them",
            description = "Deactivating signs the doctor out everywhere, hides them from patients and blocks new bookings "
                    + "(existing appointments are kept).")
    public AdminDoctor update(@PathVariable Long id, @Valid @RequestBody UpdateDoctorRequest req) {
        return doctors.update(id, req);
    }

    @PostMapping("/{id}/resend-invite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Email the doctor a fresh set-password link",
            description = "409 if they have already set up their account.")
    public void resendInvite(@PathVariable Long id) {
        doctors.resendInvite(id);
    }
}
