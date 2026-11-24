package com.souptik.breakjourney.entity;

import com.souptik.breakjourney.enums.JourneyStatus;
import com.souptik.breakjourney.enums.LegStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "journeys")
@Getter
@Setter
@NoArgsConstructor
public class Journey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String passengerName;
    private Integer passengerAge;
    private String passengerGender;
    private String contactNumber;

    @Column(nullable = false, length = 300)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JourneyStatus status = JourneyStatus.SCHEDULED;

    @OneToMany(mappedBy = "journey", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("legOrder ASC")
    private List<JourneyLeg> legs = new ArrayList<>();

    public void addLeg(JourneyLeg leg) {
        leg.setJourney(this);
        legs.add(leg);
    }

    /** Derives the overall journey status from the status and delays of its legs. */
    public void refreshStatus() {
        if (legs.isEmpty()) {
            return;
        }
        boolean allDone = legs.stream().allMatch(l -> l.getStatus() == LegStatus.COMPLETED);
        boolean anyDelayed = legs.stream().anyMatch(l -> l.getStatus() != LegStatus.COMPLETED
                && (l.getStatus() == LegStatus.DELAYED || l.getDelayMinutes() > 0));
        boolean anyStarted = legs.stream().anyMatch(l -> l.getStatus() == LegStatus.IN_TRANSIT
                || l.getStatus() == LegStatus.COMPLETED);
        if (allDone) {
            status = JourneyStatus.COMPLETED;
        } else if (anyDelayed) {
            status = JourneyStatus.DELAYED;
        } else if (anyStarted) {
            status = JourneyStatus.IN_PROGRESS;
        } else {
            status = JourneyStatus.SCHEDULED;
        }
    }
}
