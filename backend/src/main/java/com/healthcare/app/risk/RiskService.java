package com.healthcare.app.risk;

import com.healthcare.app.common.PageResponse;
import com.healthcare.app.exception.ApiException;
import com.healthcare.app.recommendation.MlClient;
import com.healthcare.app.risk.RiskAssessment.Level;
import com.healthcare.app.risk.RiskDtos.Factor;
import com.healthcare.app.risk.RiskDtos.Flag;
import com.healthcare.app.risk.RiskDtos.HistoryItem;
import com.healthcare.app.risk.RiskDtos.ModelInfo;
import com.healthcare.app.risk.RiskDtos.RiskRequest;
import com.healthcare.app.risk.RiskDtos.RiskResponse;
import com.healthcare.app.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RiskService {

    static final String DISCLAIMER =
            "This is an educational estimate from a statistical model trained on a research dataset "
                    + "(women of Pima heritage aged 21 and over). It is not a diagnosis, and it may be less accurate "
                    + "for other groups. Only a clinician can diagnose diabetes.";

    /** Score bands, in points out of 100. Relative to the training data, not absolute probabilities. */
    static final double MODERATE_FROM = 30;
    static final double HIGH_FROM = 60;
    /** 2-hour glucose in a glucose tolerance test at or above this is in the range used to diagnose diabetes. */
    static final double GLUCOSE_DIABETES_RANGE = 200;
    /** Contributions smaller than this (log-odds) are shown as neutral. */
    private static final double NEUTRAL_BAND = 0.05;

    private final MlClient ml;
    private final RiskRepository repo;

    public RiskService(MlClient ml, RiskRepository repo) {
        this.ml = ml;
        this.repo = repo;
    }

    @Transactional
    public RiskResponse assess(User user, RiskRequest req) {
        // Age alone says almost nothing: refuse instead of returning a score that merely echoes the dataset average.
        if (req.glucose() == null && req.bmi() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Please enter at least your glucose or your BMI so the estimate means something.");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("age", req.age());
        body.put("glucose", req.glucose());
        body.put("bmi", req.bmi());
        body.put("pregnancies", req.pregnancies());
        MlClient.RiskResult r = ml.predictDiabetesRisk(body);

        Level level = level(r.score());
        RiskAssessment saved = repo.save(new RiskAssessment(user, req.age(), req.glucose(), req.bmi(),
                req.pregnancies(), r.score(), level, r.modelVersion()));

        List<Factor> factors = r.contributions().stream()
                .map(c -> new Factor(c.name(), c.label(), c.unit(), c.value(), c.impact(), direction(c.impact())))
                .toList();

        return new RiskResponse(saved.getId(), saved.getCreatedAt(), r.score(), level, factors, r.missing(),
                r.outOfRange(), flags(req), reliabilityNote(r),
                new ModelInfo(r.modelVersion(), r.trainedOn(), r.cvAuc(), r.cvAucWithoutGlucose()), DISCLAIMER);
    }

    @Transactional(readOnly = true)
    public PageResponse<HistoryItem> history(User user, Level level, Pageable pageable) {
        Page<RiskAssessment> page = level == null
                ? repo.findByUserId(user.getId(), pageable)
                : repo.findByUserIdAndLevel(user.getId(), level, pageable);
        return PageResponse.of(page, HistoryItem::from);
    }

    @Transactional
    public void delete(User user, Long id) {
        RiskAssessment a = repo.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Assessment not found"));
        repo.delete(a);
    }

    @Transactional
    public long deleteAll(User user) {
        return repo.deleteByUserId(user.getId());
    }

    static Level level(double score) {
        return score >= HIGH_FROM ? Level.HIGH : score >= MODERATE_FROM ? Level.MODERATE : Level.LOW;
    }

    private static String direction(double impact) {
        return impact > NEUTRAL_BAND ? "RAISES" : impact < -NEUTRAL_BAND ? "LOWERS" : "NEUTRAL";
    }

    /** Fixed, reviewable rules, independent of the statistical model. */
    private static List<Flag> flags(RiskRequest req) {
        if (req.glucose() != null && req.glucose() >= GLUCOSE_DIABETES_RANGE) {
            return List.of(new Flag("warning",
                    "A 2-hour glucose of 200 mg/dL or more is in the range doctors use to diagnose diabetes. "
                            + "Please see a doctor soon to confirm with a proper test, whatever the score below says."));
        }
        return List.of();
    }

    private static String reliabilityNote(MlClient.RiskResult r) {
        StringBuilder note = new StringBuilder();
        if (r.missing().contains("glucose")) {
            note.append("You did not enter a glucose value, so this estimate is much less reliable "
                    + "(the model's accuracy drops from about %.2f to %.2f AUC). ".formatted(r.cvAuc(), r.cvAucWithoutGlucose()));
        }
        if (!r.outOfRange().isEmpty()) {
            note.append("Some of your values are outside the range of the data this model learned from, "
                    + "so treat the result with extra caution. ");
        }
        return note.length() == 0 ? null : note.toString().trim();
    }
}
