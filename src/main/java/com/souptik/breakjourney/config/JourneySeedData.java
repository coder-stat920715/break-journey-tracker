package com.souptik.breakjourney.config;

import com.souptik.breakjourney.entity.Journey;
import com.souptik.breakjourney.entity.JourneyLeg;
import com.souptik.breakjourney.enums.JourneyStatus;
import com.souptik.breakjourney.enums.LegStatus;
import com.souptik.breakjourney.enums.TransportMode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Builds the exact Nov 23-24, 2026 itinerary. Shared by the startup seeder and the tests. */
public final class JourneySeedData {

    private JourneySeedData() {
    }

    private static OffsetDateTime t(String iso) {
        return OffsetDateTime.parse(iso);
    }

    public static Journey kolkataToHyderabad() {
        Journey journey = new Journey();
        journey.setPassengerName("Souptik Tat");
        journey.setPassengerAge(34);
        journey.setPassengerGender("Male");
        journey.setContactNumber("+919038577517");
        journey.setTitle("Kolkata to Hyderabad Break Journey (Nov 23 – Nov 24, 2026)");
        journey.setStatus(JourneyStatus.SCHEDULED);

        journey.addLeg(JourneyLeg.builder()
                .legOrder(1)
                .mode(TransportMode.CAB)
                .status(LegStatus.SCHEDULED)
                .origin("Xclusive Mansion, 59C Kalupara Lane, Dhakuria, Kolkata 700031")
                .destination("Howrah Junction Railway Station (New Complex Cab Road)")
                .distanceKm(11.7)
                .recommendedBookingTime(t("2026-11-23T04:15:00+05:30"))
                .scheduledDeparture(t("2026-11-23T04:30:00+05:30"))
                .scheduledArrival(t("2026-11-23T05:00:00+05:30"))
                .notes("Early morning cab transfer; walk to Dhakuria main road junction if cab struggles with narrow Kalupara Lane.")
                .build());

        journey.addLeg(JourneyLeg.builder()
                .legOrder(2)
                .mode(TransportMode.TRAIN)
                .status(LegStatus.SCHEDULED)
                .operatorName("22895 Howrah - Puri Vande Bharat Express")
                .bookingReference("6951647716")
                .transactionId("100006932256103")
                .seatInfo("AC Chair Car (CC) | Coach C6 | Seat 78 (Window Side)")
                .seatNumber("78")
                .mealPlan("1-Morning Tea + Breakfast (Included)")
                .origin("Howrah Junction (HWH)")
                .destination("Bhubaneswar (BBS) - Platform 4")
                .distanceKm(436.0)
                .scheduledDeparture(t("2026-11-23T06:10:00+05:30"))
                .scheduledArrival(t("2026-11-23T11:28:00+05:30"))
                .expectedDelayBufferMinutes(30)
                .build());

        journey.addLeg(JourneyLeg.builder()
                .legOrder(3)
                .mode(TransportMode.AUTO_CAB)
                .status(LegStatus.SCHEDULED)
                .origin("Bhubaneswar Railway Station (Master Canteen Side)")
                .destination("Baramunda ISBT (BSABT), Bhubaneswar")
                .distanceKm(7.7)
                .scheduledDeparture(t("2026-11-23T12:10:00+05:30"))
                .scheduledArrival(t("2026-11-23T12:40:00+05:30"))
                .layoverBufferMinutes(50)
                .notes("50 minute layover at Baramunda Bus Stand for lunch and boarding.")
                .build());

        journey.addLeg(JourneyLeg.builder()
                .legOrder(4)
                .mode(TransportMode.BUS)
                .status(LegStatus.SCHEDULED)
                .operatorName("Shyamoli Paribahan Pvt Ltd")
                .vehicleType("Volvo 9600 A/C Seater/Sleeper (2+1)")
                .ticketNumber("TVBQ22662970")
                .bookingReference("7296768")
                .seatNumber("K1")
                .fare(new BigDecimal("1568.05"))
                .currency("INR")
                .origin("Baramunda Bus Stand, Bhubaneswar")
                .destination("Miyapur, Hyderabad")
                .scheduledDeparture(t("2026-11-23T13:30:00+05:30"))
                .scheduledArrival(t("2026-11-24T08:30:00+05:30"))
                .build());

        return journey;
    }
}
