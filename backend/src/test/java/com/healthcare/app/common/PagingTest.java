package com.healthcare.app.common;

import com.healthcare.app.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Paging helper")
class PagingTest {

    private static final Set<String> ALLOWED = Set.of("dateTime", "status", "id");
    private static final Sort DEFAULT = Sort.by(Sort.Direction.ASC, "dateTime");

    private Pageable of(int page, int size, String sort) {
        return Paging.of(page, size, sort, ALLOWED, DEFAULT);
    }

    @Test
    void usesTheDefaultSort_andAlwaysAddsAnIdTiebreaker() {
        Pageable p = of(0, 10, null);
        assertEquals(Sort.Direction.ASC, p.getSort().getOrderFor("dateTime").getDirection());
        assertNotNull(p.getSort().getOrderFor("id"), "ties must be broken deterministically so pages don't overlap");
    }

    @Test
    void parsesFieldAndDirection() {
        assertEquals(Sort.Direction.DESC, of(0, 10, "status,desc").getSort().getOrderFor("status").getDirection());
        assertEquals(Sort.Direction.ASC, of(0, 10, "status").getSort().getOrderFor("status").getDirection());
        assertEquals(Sort.Direction.DESC, of(0, 10, "status, DESC").getSort().getOrderFor("status").getDirection());
    }

    @Test
    void blankSortFallsBackToTheDefault() {
        assertNotNull(of(0, 10, "  ").getSort().getOrderFor("dateTime"));
    }

    @Test
    void rejectsFieldsOutsideTheWhitelist() {
        ApiException e = assertThrows(ApiException.class, () -> of(0, 10, "patient.password,asc"));
        assertTrue(e.getMessage().contains("Cannot sort by 'patient.password'"));
        assertTrue(e.getMessage().contains("dateTime, id, status"), "tells the client what IS allowed, sorted");
    }

    @Test
    void rejectsBadDirections() {
        assertThrows(ApiException.class, () -> of(0, 10, "status,up"));
    }

    @Test
    void enforcesPageAndSizeBounds() {
        assertThrows(ApiException.class, () -> of(-1, 10, null));
        assertThrows(ApiException.class, () -> of(0, 0, null));
        assertThrows(ApiException.class, () -> of(0, Paging.MAX_SIZE + 1, null));
        assertEquals(Paging.MAX_SIZE, of(0, Paging.MAX_SIZE, null).getPageSize());
        assertEquals(1, of(0, 1, null).getPageSize());
    }
}
