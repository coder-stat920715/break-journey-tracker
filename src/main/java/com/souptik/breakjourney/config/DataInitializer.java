package com.souptik.breakjourney.config;

import com.souptik.breakjourney.entity.Journey;
import com.souptik.breakjourney.repository.JourneyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final JourneyRepository journeyRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (journeyRepository.count() > 0) {
            return;
        }
        Journey saved = journeyRepository.save(JourneySeedData.kolkataToHyderabad());
        log.info("Seeded journey #{} '{}' for {} with {} legs",
                saved.getId(), saved.getTitle(), saved.getPassengerName(), saved.getLegs().size());
    }
}
