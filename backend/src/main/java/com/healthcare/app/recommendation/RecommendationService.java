package com.healthcare.app.recommendation;

import com.healthcare.app.appointment.Appointment;
import com.healthcare.app.appointment.AppointmentRepository;
import com.healthcare.app.recommendation.RecommendationDtos.DoctorMatch;
import com.healthcare.app.recommendation.RecommendationDtos.RecommendResponse;
import com.healthcare.app.recommendation.RecommendationDtos.SpecialtyScore;
import com.healthcare.app.user.Role;
import com.healthcare.app.user.User;
import com.healthcare.app.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RecommendationService {

    static final String DISCLAIMER =
            "This is an automated suggestion to help you choose a specialist. It is not a diagnosis or medical advice.";
    private static final String GENERAL = "general medicine";
    private static final int MAX_DOCTORS = 6;
    /** Specialties below this are not worth recommending a doctor for. */
    private static final double MIN_DOCTOR_CONFIDENCE = 0.10;
    /** Specialties below this are hidden from the "why" breakdown (the top one always stays). */
    private static final double MIN_DISPLAY_CONFIDENCE = 0.05;

    private final RedFlagDetector redFlags;
    private final MlClient ml;
    private final UserRepository users;
    private final AppointmentRepository appointments;

    public RecommendationService(RedFlagDetector redFlags, MlClient ml, UserRepository users,
                                 AppointmentRepository appointments) {
        this.redFlags = redFlags;
        this.ml = ml;
        this.users = users;
        this.appointments = appointments;
    }

    public RecommendResponse recommend(String symptoms) {
        // 1. Safety first: emergency rules bypass the model entirely.
        var flag = redFlags.detect(symptoms);
        if (flag.isPresent()) {
            return new RecommendResponse(true, flag.get().message(), false, null, List.of(), List.of(), DISCLAIMER);
        }

        // 2. Ask the model which specialties fit.
        MlClient.Prediction prediction = ml.predict(symptoms);
        List<MlClient.Item> items = prediction.predictions();
        List<SpecialtyScore> specialties = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (i == 0 || items.get(i).confidence() >= MIN_DISPLAY_CONFIDENCE) {
                specialties.add(new SpecialtyScore(items.get(i).specialty(), items.get(i).confidence()));
            }
        }
        Map<String, Double> confidenceBySpecialty = new HashMap<>();
        specialties.forEach(s -> confidenceBySpecialty.put(key(s.specialty()), s.confidence()));

        // 3. Rank real doctors: specialty confidence first, then lighter workload.
        LocalDateTime now = LocalDateTime.now();
        List<Candidate> candidates = new ArrayList<>();
        for (User d : users.findByRole(Role.DOCTOR)) {
            String spec = key(d.getSpecialty());
            double conf = confidenceBySpecialty.getOrDefault(spec, 0.0);
            boolean generalFallback = prediction.lowConfidence() && spec.equals(GENERAL);
            if (conf < MIN_DOCTOR_CONFIDENCE && !generalFallback) {
                continue; // a 0.03% "match" is noise, not a recommendation
            }
            int load = (int) appointments.countByDoctorIdAndStatusAndDateTimeAfter(
                    d.getId(), Appointment.Status.BOOKED, now);
            String reason = generalFallback
                    ? "Your description is broad, so a general physician is a good place to start."
                    : "%s fits your symptoms (%d%% match).".formatted(d.getSpecialty(), Math.round(conf * 100));
            candidates.add(new Candidate(d, conf, generalFallback ? 2.0 : conf, load, reason));
        }

        String note = null;
        if (candidates.isEmpty()) {
            // No specialist for the predicted specialties: fall back to general physicians.
            note = "We don't have a %s specialist yet, so here are general physicians who can guide you."
                    .formatted(specialties.get(0).specialty());
            for (User d : users.findByRole(Role.DOCTOR)) {
                if (key(d.getSpecialty()).equals(GENERAL)) {
                    int load = (int) appointments.countByDoctorIdAndStatusAndDateTimeAfter(
                            d.getId(), Appointment.Status.BOOKED, now);
                    candidates.add(new Candidate(d, 0, 0, load,
                            "A general physician can assess you and refer you if needed."));
                }
            }
        }

        List<DoctorMatch> matches = candidates.stream()
                .sorted(Comparator.comparingDouble(Candidate::rank).reversed()
                        .thenComparingInt(Candidate::load)
                        .thenComparing(c -> c.doctor().getFullName()))
                .limit(MAX_DOCTORS)
                .map(c -> new DoctorMatch(c.doctor().getId(), c.doctor().getFullName(), c.doctor().getSpecialty(),
                        c.confidence(), c.reason(), c.load()))
                .toList();

        return new RecommendResponse(false, null, prediction.lowConfidence(), note, specialties, matches, DISCLAIMER);
    }

    private static String key(String specialty) {
        return specialty == null ? "" : specialty.trim().toLowerCase(Locale.ROOT);
    }

    private record Candidate(User doctor, double confidence, double rank, int load, String reason) {
    }
}
