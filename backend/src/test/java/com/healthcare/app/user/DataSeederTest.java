package com.healthcare.app.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Demo data seeding")
class DataSeederTest {

    private final UserRepository users = mock(UserRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void doesNothing_unlessExplicitlyEnabled() throws Exception {
        new DataSeeder().seed(users, encoder, false, "").run();
        verifyNoInteractions(users);
    }

    @Test
    void refusesToSeed_withoutAPasswordFromTheEnvironment() {
        var runner = new DataSeeder().seed(users, encoder, true, "short");
        var e = assertThrows(IllegalStateException.class, runner::run);
        assertTrue(e.getMessage().contains("DEMO_DOCTOR_PASSWORD"));
        verify(users, never()).save(any());
    }

    @Test
    void whenEnabled_createsDoctors_withTheSuppliedPassword_andSkipsExistingOnes() throws Exception {
        when(users.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(users.existsByEmailIgnoreCase("asha.verma@clinic.test")).thenReturn(true);

        new DataSeeder().seed(users, encoder, true, "my-own-demo-password").run();

        var saved = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users, times(11)).save(saved.capture()); // 12 doctors, one already existed
        assertTrue(saved.getAllValues().stream().allMatch(u -> u.getRole() == Role.DOCTOR));
        assertTrue(encoder.matches("my-own-demo-password", saved.getValue().getPassword()));
    }
}
