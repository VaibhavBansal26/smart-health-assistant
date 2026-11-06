package com.healthcare.app.admin;

import com.healthcare.app.auth.RefreshCookies;
import com.healthcare.app.support.ApiTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Own context and database: it creates, edits and deactivates doctors, which would disturb other tests' counts. */
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:admintest;DB_CLOSE_DELAY=-1")
@DisplayName("Admin: doctor management")
class AdminApiTest extends ApiTest {

    private String uniqueEmail() {
        return "newdoc-" + System.nanoTime() + "@clinic.test";
    }

    private long createDoctor(String adminToken, String email, String specialty) throws Exception {
        return body(post("/api/admin/doctors", adminToken,
                Map.of("fullName", "Dr. New Doctor", "email", email, "specialty", specialty))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    /** The doctor follows the invitation email and chooses a password. */
    private void acceptInvite(String email, String password) throws Exception {
        String token = mail.tokenIn(mail.latestTo(email, "Set up your doctor account"));
        post("/api/auth/reset-password", null, Map.of("token", token, "newPassword", password)).andExpect(status().isNoContent());
    }

    @Nested
    @DisplayName("access control")
    class Access {
        @Test
        void onlyAdministrators_mayUseTheAdminEndpoints() throws Exception {
            String patient = newPatient().token();
            String doctor = seededDoctor("asha.verma@clinic.test").token();
            for (String token : new String[]{patient, doctor}) {
                get("/api/admin/doctors", token).andExpect(status().isForbidden());
                post("/api/admin/doctors", token, Map.of("fullName", "X", "email", uniqueEmail(), "specialty", "ENT")).andExpect(status().isForbidden());
                patch("/api/admin/doctors/1", token, Map.of("active", false)).andExpect(status().isForbidden());
                post("/api/admin/doctors/1/resend-invite", token, Map.of()).andExpect(status().isForbidden());
            }
            get("/api/admin/doctors", null).andExpect(status().isUnauthorized());
        }

        @Test
        void theBootstrapAdmin_canSignIn_andIsAnAdmin() throws Exception {
            var admin = admin();
            get("/api/auth/me", admin.token()).andExpect(jsonPath("$.role").value("ADMIN")).andExpect(jsonPath("$.emailVerified").value(true));
        }

        @Test
        void adminsCannotUsePatientFeatures() throws Exception {
            String admin = admin().token();
            post("/api/appointments", admin, Map.of("doctorId", 1, "dateTime", slot(), "reason", "x")).andExpect(status().isForbidden());
            post("/api/risk/diabetes", admin, Map.of("age", 40, "glucose", 100)).andExpect(status().isForbidden());
        }

        @Test
        void nobodyCanSelfRegisterAsDoctorOrAdmin() throws Exception {
            for (String role : new String[]{"DOCTOR", "ADMIN"}) {
                post("/api/auth/register", null, Map.of("fullName", "Sneaky", "email", uniqueEmail(),
                        "password", "Passw0rd!", "role", role)).andExpect(status().isBadRequest());
            }
        }
    }

    @Nested
    @DisplayName("creating doctors")
    class Creating {
        @Test
        void createsAnAccountPendingSetup_andEmailsAnInvitation() throws Exception {
            String admin = admin().token();
            String email = uniqueEmail();

            post("/api/admin/doctors", admin, Map.of("fullName", "Dr. Ana Lopez", "email", email.toUpperCase(), "specialty", "Sleep Medicine"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value(email)) // normalised to lower case
                    .andExpect(jsonPath("$.active").value(true))
                    .andExpect(jsonPath("$.setupPending").value(true));

            var invite = mail.latestTo(email, "Set up your doctor account");
            assertThat(invite.body(), containsString("Sleep Medicine"));
            assertThat(invite.body(), containsString("http://app.test/reset-password?token="));
            assertThat(invite.body(), containsString("expires in 72 hour"));
        }

        @Test
        void thePendingDoctor_cannotSignInUntilTheySetAPassword() throws Exception {
            String email = uniqueEmail();
            createDoctor(admin().token(), email, "Sleep Medicine");

            post("/api/auth/login", null, Map.of("email", email, "password", "Passw0rd!")).andExpect(status().isUnauthorized());

            acceptInvite(email, "My-Own-Password-1");
            var doctor = loginAs(email, "My-Own-Password-1");
            get("/api/auth/me", doctor.token()).andExpect(jsonPath("$.role").value("DOCTOR")).andExpect(jsonPath("$.emailVerified").value(true));
        }

        @Test
        void aNewDoctor_appearsToPatients_andCanBeBooked() throws Exception {
            String specialty = "Sleep-" + System.nanoTime();
            String email = uniqueEmail();
            long id = createDoctor(admin().token(), email, specialty);
            var patient = newPatient();

            get("/api/doctors?specialty=" + specialty, patient.token()).andExpect(jsonPath("$.content[0].id").value(id));
            post("/api/appointments", patient.token(), Map.of("doctorId", id, "dateTime", slot(), "reason", "can't sleep"))
                    .andExpect(status().isCreated());
        }

        @Test
        void duplicatesAndBadInput_areRejected() throws Exception {
            String admin = admin().token();
            post("/api/admin/doctors", admin, Map.of("fullName", "Dup", "email", "asha.verma@clinic.test", "specialty", "ENT"))
                    .andExpect(status().isConflict());
            post("/api/admin/doctors", admin, Map.of("fullName", "", "email", "nope", "specialty", ""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                    .andExpect(jsonPath("$.fieldErrors.email").exists())
                    .andExpect(jsonPath("$.fieldErrors.specialty").exists());
        }

        @Test
        void resendInvite_issuesAFreshLink_untilTheDoctorHasSetUp() throws Exception {
            String admin = admin().token();
            String email = uniqueEmail();
            long id = createDoctor(admin, email, "Sleep Medicine");
            String first = mail.tokenIn(mail.latestTo(email, "Set up"));

            post("/api/admin/doctors/" + id + "/resend-invite", admin, Map.of()).andExpect(status().isNoContent());
            String second = mail.tokenIn(mail.latestTo(email, "Set up"));
            assertNotEquals(first, second);
            post("/api/auth/reset-password", null, Map.of("token", first, "newPassword", "Whatever-Pass1")).andExpect(status().isBadRequest());

            post("/api/auth/reset-password", null, Map.of("token", second, "newPassword", "Whatever-Pass1")).andExpect(status().isNoContent());
            post("/api/admin/doctors/" + id + "/resend-invite", admin, Map.of()).andExpect(status().isConflict());
        }

        @Test
        void resendInvite_forUnknownIdsOrNonDoctors_is404() throws Exception {
            String admin = admin().token();
            post("/api/admin/doctors/999999/resend-invite", admin, Map.of()).andExpect(status().isNotFound());
            post("/api/admin/doctors/" + newPatient().id() + "/resend-invite", admin, Map.of()).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("editing and deactivating")
    class Editing {
        @Test
        void canEditNameAndSpecialty_withPartialUpdates() throws Exception {
            String admin = admin().token();
            long id = createDoctor(admin, uniqueEmail(), "Sleep Medicine");

            patch("/api/admin/doctors/" + id, admin, Map.of("specialty", "  Rheumatology "))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.specialty").value("Rheumatology"))
                    .andExpect(jsonPath("$.fullName").value("Dr. New Doctor")); // untouched
            patch("/api/admin/doctors/" + id, admin, Map.of("fullName", "Dr. Renamed"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Dr. Renamed"));
        }

        @Test
        void invalidEdits_areRejected() throws Exception {
            String admin = admin().token();
            long id = createDoctor(admin, uniqueEmail(), "Sleep Medicine");
            patch("/api/admin/doctors/" + id, admin, Map.of()).andExpect(status().isBadRequest());
            patch("/api/admin/doctors/" + id, admin, Map.of("fullName", "   ")).andExpect(status().isBadRequest());
            patch("/api/admin/doctors/" + newPatient().id(), admin, Map.of("active", false)).andExpect(status().isNotFound());
            patch("/api/admin/doctors/999999", admin, Map.of("active", false)).andExpect(status().isNotFound());
        }

        @Test
        void deactivating_takesEffectImmediately_everywhere() throws Exception {
            String specialty = "Sleep-" + System.nanoTime();
            String admin = admin().token();
            String email = uniqueEmail();
            long id = createDoctor(admin, email, specialty);
            acceptInvite(email, "My-Own-Password-1");

            // the doctor is signed in on a device, and a patient already has an appointment with them
            var login = post("/api/auth/login", null, Map.of("email", email, "password", "My-Own-Password-1")).andReturn();
            String accessToken = body(login).get("token").asText();
            String refreshCookie = login.getResponse().getCookie(RefreshCookies.NAME).getValue();
            var patient = newPatient();
            book(patient.token(), id, slot());
            get("/api/auth/me", accessToken).andExpect(status().isOk());

            patch("/api/admin/doctors/" + id, admin, Map.of("active", false))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

            get("/api/auth/me", accessToken).andExpect(status().isUnauthorized());            // old access token refused
            mvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh").header("X-Requested-With", "XMLHttpRequest")
                    .cookie(new Cookie(RefreshCookies.NAME, refreshCookie))).andExpect(status().isUnauthorized()); // session gone
            post("/api/auth/login", null, Map.of("email", email, "password", "My-Own-Password-1")).andExpect(status().isUnauthorized());
            get("/api/doctors?specialty=" + specialty, patient.token()).andExpect(jsonPath("$.totalElements").value(0)); // hidden
            post("/api/appointments", patient.token(), Map.of("doctorId", id, "dateTime", slot(), "reason", "x")).andExpect(status().isNotFound());
            get("/api/appointments", patient.token()).andExpect(jsonPath("$.totalElements").value(1)); // history kept
        }

        @Test
        void aDeactivatedAccount_cannotUseForgotPasswordToGetBackIn() throws Exception {
            String admin = admin().token();
            String email = uniqueEmail();
            long id = createDoctor(admin, email, "Sleep Medicine");
            acceptInvite(email, "My-Own-Password-1");
            patch("/api/admin/doctors/" + id, admin, Map.of("active", false));
            int before = mail.countTo(email);

            post("/api/auth/forgot-password", null, Map.of("email", email)).andExpect(status().isAccepted());
            assertEquals(before, mail.countTo(email), "no reset email goes to a deactivated account");
        }

        @Test
        void reactivating_restoresAccess() throws Exception {
            String specialty = "Sleep-" + System.nanoTime();
            String admin = admin().token();
            String email = uniqueEmail();
            long id = createDoctor(admin, email, specialty);
            acceptInvite(email, "My-Own-Password-1");
            patch("/api/admin/doctors/" + id, admin, Map.of("active", false));
            post("/api/auth/login", null, Map.of("email", email, "password", "My-Own-Password-1")).andExpect(status().isUnauthorized());

            patch("/api/admin/doctors/" + id, admin, Map.of("active", true)).andExpect(jsonPath("$.active").value(true));

            post("/api/auth/login", null, Map.of("email", email, "password", "My-Own-Password-1")).andExpect(status().isOk());
            get("/api/doctors?specialty=" + specialty, newPatient().token()).andExpect(jsonPath("$.content[0].id").value(id));
        }
    }

    @Nested
    @DisplayName("listing")
    class Listing {
        @Test
        void listsEveryDoctor_includingDeactivated_withFilters() throws Exception {
            String admin = admin().token();
            long id = createDoctor(admin, uniqueEmail(), "Audiology");
            patch("/api/admin/doctors/" + id, admin, Map.of("active", false));

            get("/api/admin/doctors?specialty=Audiology", admin)
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].active").value(false))
                    .andExpect(jsonPath("$.content[0].setupPending").value(true));
            get("/api/admin/doctors?specialty=Audiology&active=true", admin).andExpect(jsonPath("$.totalElements").value(0));
            get("/api/admin/doctors?specialty=Audiology&active=false", admin).andExpect(jsonPath("$.totalElements").value(1));
            get("/api/admin/doctors?size=100", admin).andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(13)));
            get("/api/admin/doctors?q=verma", admin).andExpect(jsonPath("$.content[0].fullName").value("Dr. Asha Verma"));
            get("/api/admin/doctors?q=asha.verma@", admin).andExpect(jsonPath("$.totalElements").value(1)); // matches email too
        }

        @Test
        void isPagedAndSortable_withAWhitelist() throws Exception {
            String admin = admin().token();
            get("/api/admin/doctors?size=3&page=0&sort=fullName,asc", admin)
                    .andExpect(jsonPath("$.content", hasSize(3))).andExpect(jsonPath("$.hasNext").value(true));
            get("/api/admin/doctors?sort=password", admin).andExpect(status().isBadRequest());
            get("/api/admin/doctors?size=101", admin).andExpect(status().isBadRequest());
        }
    }

    private static void assertThat(String actual, org.hamcrest.Matcher<String> m) {
        org.hamcrest.MatcherAssert.assertThat(actual, m);
    }
}
