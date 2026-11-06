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

    @Column(nullable = false)
    private int age;
    private Double glucose;
    private Double bmi;
    private Integer pregnancies;

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
        this.age = age;
        this.glucose = glucose;
        this.bmi = bmi;
        this.pregnancies = pregnancies;
        this.score = score;
        this.level = level;
        this.modelVersion = modelVersion;
    }

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public int getAge() { return age; }
    public Double getGlucose() { return glucose; }
    public Double getBmi() { return bmi; }
    public Integer getPregnancies() { return pregnancies; }
    public double getScore() { return score; }
    public Level getLevel() { return level; }
}
