package com.healthcare.app.admin;

import com.healthcare.app.user.Role;
import com.healthcare.app.user.User;
import com.healthcare.app.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("First-admin bootstrap")
class AdminBootstrapTest {

    private final UserRepository users = mock(UserRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final AdminBootstrap bootstrap = new AdminBootstrap();

    @Test
    void doesNothing_withoutAnEmail() throws Exception {
        bootstrap.bootstrapAdmin(users, encoder, "", "", "Admin").run();
        verifyNoInteractions(users);
    }

    @Test
    void createsAVerifiedAdmin_withAHashedPassword() throws Exception {
        when(users.existsByEmailIgnoreCase("boss@clinic.test")).thenReturn(false);

        bootstrap.bootstrapAdmin(users, encoder, " Boss@Clinic.test ", "a-long-admin-password", "The Boss").run();

        var saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        User admin = saved.getValue();
        assertEquals(Role.ADMIN, admin.getRole());
        assertEquals("boss@clinic.test", admin.getEmail());
        assertTrue(admin.isEmailVerified() && admin.isActive());
        assertNotEquals("a-long-admin-password", admin.getPassword(), "never stored in clear");
        assertTrue(encoder.matches("a-long-admin-password", admin.getPassword()));
    }

    @Test
    void neverOverwritesAnExistingAccount_soChangingTheEnvPasswordDoesNotResetIt() throws Exception {
        when(users.existsByEmailIgnoreCase("boss@clinic.test")).thenReturn(true);
        bootstrap.bootstrapAdmin(users, encoder, "boss@clinic.test", "a-different-long-password", "Boss").run();
        verify(users, never()).save(any());
    }

    @Test
    void refusesToStart_withAWeakOrMissingPassword() {
        when(users.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        for (String weak : new String[]{"", "short", "elevenchars"}) {
            var e = assertThrows(IllegalStateException.class,
                    () -> bootstrap.bootstrapAdmin(users, encoder, "boss@clinic.test", weak, "Boss").run());
            assertTrue(e.getMessage().contains("ADMIN_PASSWORD"));
        }
        verify(users, never()).save(any());
    }
}
