package com.healthcare.app.user;

import com.healthcare.app.support.ApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Doctors API")
class DoctorApiTest extends ApiTest {

    @Test
    void listsSeededDoctors_withoutLeakingSensitiveFields() throws Exception {
        get("/api/doctors?size=100", newPatient().token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(12)))
                .andExpect(jsonPath("$.content[*].role", everyItem(is("DOCTOR"))))
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
    }

    @Test
    void patientsAreNeverListedAsDoctors() throws Exception {
        var patient = newPatient();
        get("/api/doctors?size=100&q=" + "Test Patient", patient.token())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersBySpecialty_caseInsensitively() throws Exception {
        get("/api/doctors?specialty=cARdiOlogy", newPatient().token())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].specialty", everyItem(is("Cardiology"))));
    }

    @Test
    void searchMatchesNameOrSpecialty() throws Exception {
        String token = newPatient().token();
        get("/api/doctors?q=verma", token).andExpect(jsonPath("$.content[0].fullName").value("Dr. Asha Verma"));
        get("/api/doctors?q=dermat", token).andExpect(jsonPath("$.content[0].specialty").value("Dermatology"));
        get("/api/doctors?q=zzzz-nobody", token).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void searchTreatsWildcardsAsPlainText() throws Exception {
        String token = newPatient().token();
        // "%" and "_" must not behave as SQL LIKE wildcards
        get("/api/doctors?q=%25", token).andExpect(jsonPath("$.totalElements").value(0));
        get("/api/doctors?q=_", token).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void pagingAndSorting_work() throws Exception {
        String token = newPatient().token();
        get("/api/doctors?size=5&page=0&sort=fullName,asc", token)
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.content[0].fullName").value("Dr. Arjun Rao"));
        get("/api/doctors?size=5&sort=fullName,desc", token)
                .andExpect(jsonPath("$.content[0].fullName").value("Dr. Vikram Shah"));
    }

    @Test
    void invalidParameters_areRejected() throws Exception {
        String token = newPatient().token();
        get("/api/doctors?size=101", token).andExpect(status().isBadRequest());
        get("/api/doctors?sort=password", token).andExpect(status().isBadRequest());
    }

    @Test
    void requiresLogin() throws Exception {
        get("/api/doctors", null).andExpect(status().isUnauthorized());
    }
}
