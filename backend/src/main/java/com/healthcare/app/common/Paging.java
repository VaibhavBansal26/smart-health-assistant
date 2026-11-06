package com.healthcare.app.common;

import com.healthcare.app.exception.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.util.Set;

/**
 * Builds a validated Pageable from raw query params: ?page=0&size=10&sort=field,desc
 * Sorting is restricted to a per-endpoint whitelist, so a client can never sort by
 * (or probe the existence of) arbitrary entity fields such as another table's columns.
 */
public final class Paging {

    public static final int MAX_SIZE = 100;

    private Paging() {
    }

    public static Pageable of(int page, int size, String sort, Set<String> allowedFields, Sort defaultSort) {
        if (page < 0) {
            throw bad("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw bad("size must be between 1 and " + MAX_SIZE);
        }
        Sort resolved = defaultSort;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String field = parts[0].trim();
            if (!allowedFields.contains(field)) {
                throw bad("Cannot sort by '" + field + "'. Allowed: " + String.join(", ", allowedFields.stream().sorted().toList()));
            }
            Sort.Direction direction = Sort.Direction.ASC;
            if (parts.length > 1) {
                direction = switch (parts[1].trim().toLowerCase()) {
                    case "asc" -> Sort.Direction.ASC;
                    case "desc" -> Sort.Direction.DESC;
                    default -> throw bad("Sort direction must be asc or desc");
                };
            }
            resolved = Sort.by(direction, field);
        }
        // id as a final tiebreaker keeps page boundaries stable between requests
        return PageRequest.of(page, size, resolved.and(Sort.by(Sort.Direction.ASC, "id")));
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
