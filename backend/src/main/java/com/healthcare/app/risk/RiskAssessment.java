package com.healthcare.app.risk;

import com.healthcare.app.user.User;
import jakarta.persistence.*;

import java.time.Instant;

/** One saved risk check. Stores the inputs and the result so the user can see their history. */
@Entity
@Table(name = "risk_assessments")
public class RiskAssessment {

    public enum Level { LOW, MODERATE, HIGH }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User user;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** age, glucose, BMI and pregnancies as one AES-GCM encrypted JSON value (see FieldEncryptor). */
    @Convert(converter = EncryptedRiskInputsConverter.class)
    @Column(name = "inputs", nullable = false, length = 1000)
    private RiskInputs inputs;

    @Column(nullable = false)
    private double score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Level level;

    @Column(nullable = false)
    private String modelVersion;

    protected RiskAssessment() {
    }

    public RiskAssessment(User user, int age, Double glucose, Double bmi, Integer pregnancies,
                          double score, Level level, String modelVersion) {
        this.user = user;
        this.inputs = new RiskInputs(age, glucose, bmi, pregnancies);
        this.score = score;
        this.level = level;
        this.modelVersion = modelVersion;
    }

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public int getAge() { return inputs.age(); }
    public Double getGlucose() { return inputs.glucose(); }
    public Double getBmi() { return inputs.bmi(); }
    public Integer getPregnancies() { return inputs.pregnancies(); }
    public double getScore() { return score; }
    public Level getLevel() { return level; }
}
