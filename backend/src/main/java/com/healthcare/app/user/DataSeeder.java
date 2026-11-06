package com.healthcare.app.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

/**
 * Seeds demo doctors (one or two per specialty the recommender knows about) for local development and tests.
 * OFF by default: enable with SEED_DEMO_DATA=true and supply their shared password via DEMO_DOCTOR_PASSWORD.
 * Idempotent: only adds doctors whose email is not already in the database.
 */
@Configuration
public class DataSeeder {

    private record Seed(String name, String email, String specialty) {
    }

    private static final List<Seed> DOCTORS = List.of(
            new Seed("Dr. Asha Verma", "asha.verma@clinic.test", "Cardiology"),
            new Seed("Dr. Imran Qureshi", "imran.qureshi@clinic.test", "Cardiology"),
            new Seed("Dr. Rohan Mehta", "rohan.mehta@clinic.test", "General Medicine"),
            new Seed("Dr. Leela Thomas", "leela.thomas@clinic.test", "General Medicine"),
            new Seed("Dr. Priya Nair", "priya.nair@clinic.test", "Dermatology"),
            new Seed("Dr. Kabir Singh", "kabir.singh@clinic.test", "Endocrinology"),
            new Seed("Dr. Meera Iyer", "meera.iyer@clinic.test", "Neurology"),
            new Seed("Dr. Arjun Rao", "arjun.rao@clinic.test", "Orthopedics"),
            new Seed("Dr. Sana Khan", "sana.khan@clinic.test", "ENT"),
            new Seed("Dr. Vikram Shah", "vikram.shah@clinic.test", "Pulmonology"),
            new Seed("Dr. Nisha Patel", "nisha.patel@clinic.test", "Gastroenterology"),
            new Seed("Dr. Rahul Menon", "rahul.menon@clinic.test", "Psychiatry"));

    @Bean
    CommandLineRunner seed(UserRepository users, PasswordEncoder encoder,
                           @Value("${app.seed.demo-doctors:false}") boolean enabled,
                           @Value("${app.seed.demo-doctor-password:}") String password) {
        return args -> {
            if (!enabled) {
                return;
            }
            if (password.length() < 8) {
                throw new IllegalStateException(
                        "SEED_DEMO_DATA=true needs DEMO_DOCTOR_PASSWORD (at least 8 characters). See .env.example.");
            }
            String pw = encoder.encode(password);
            for (Seed s : DOCTORS) {
                if (!users.existsByEmailIgnoreCase(s.email())) {
                    users.save(new User(s.name(), s.email(), pw, Role.DOCTOR, s.specialty()));
                }
            }
        };
    }
}
