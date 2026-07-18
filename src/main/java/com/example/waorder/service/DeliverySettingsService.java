package com.example.waorder.service;

import com.example.waorder.model.DeliveryHoliday;
import com.example.waorder.model.DeliveryWeeklyOffDay;
import com.example.waorder.repository.DeliveryHolidayRepository;
import com.example.waorder.repository.DeliveryWeeklyOffDayRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Central place for admin-managed delivery availability rules:
 *  - specific blocked calendar dates ("holidays")
 *  - recurring weekly off days (e.g. every Monday)
 *
 * Used both to render the admin management page and to validate/annotate
 * the customer-facing order form (client-side hints + server-side check).
 */
@Service
@Slf4j
public class DeliverySettingsService {

    private final DeliveryHolidayRepository holidayRepository;
    private final DeliveryWeeklyOffDayRepository weeklyOffDayRepository;

    public DeliverySettingsService(DeliveryHolidayRepository holidayRepository,
                                    DeliveryWeeklyOffDayRepository weeklyOffDayRepository) {
        this.holidayRepository = holidayRepository;
        this.weeklyOffDayRepository = weeklyOffDayRepository;
    }

    public List<DeliveryHoliday> getAllHolidays() {
        return holidayRepository.findAllByOrderByDateAsc();
    }

    @Transactional
    public void addHoliday(LocalDate date, String reason) {
        if (date == null) {
            throw new IllegalArgumentException("Date is required");
        }
        if (holidayRepository.existsByDate(date)) {
            throw new IllegalArgumentException("That date is already marked as a holiday");
        }
        DeliveryHoliday holiday = new DeliveryHoliday();
        holiday.setDate(date);
        holiday.setReason(reason);
        holidayRepository.save(holiday);
    }

    @Transactional
    public void deleteHoliday(Long id) {
        holidayRepository.deleteById(id);
    }

    public Set<DayOfWeek> getWeeklyOffDays() {
        return weeklyOffDayRepository.findAll().stream()
                .map(DeliveryWeeklyOffDay::getDayOfWeek)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    /** Replaces the full set of weekly off days with the given selection. */
    @Transactional
    public void updateWeeklyOffDays(Set<DayOfWeek> days) {
        weeklyOffDayRepository.deleteAll();
        if (days == null || days.isEmpty()) {
            return;
        }
        List<DeliveryWeeklyOffDay> entities = days.stream()
                .distinct()
                .map(day -> {
                    DeliveryWeeklyOffDay entity = new DeliveryWeeklyOffDay();
                    entity.setDayOfWeek(day);
                    return entity;
                })
                .collect(Collectors.toList());
        weeklyOffDayRepository.saveAll(entities);
    }

    /** True if the given date falls on a weekly off day OR is a specific blocked holiday. */
    public boolean isDateBlocked(LocalDate date) {
        if (date == null) {
            return false;
        }
        if (getWeeklyOffDays().contains(date.getDayOfWeek())) {
            return true;
        }
        return holidayRepository.existsByDate(date);
    }

    /** ISO ("yyyy-MM-dd") strings for every blocked holiday - handy for passing to the frontend as JSON. */
    public List<String> getHolidayDateStrings() {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        return getAllHolidays().stream()
                .map(h -> h.getDate().format(formatter))
                .collect(Collectors.toList());
    }

    /**
     * Weekly off days expressed as JavaScript's Date.getDay() indices
     * (Sunday=0 ... Saturday=6), since java.time.DayOfWeek uses ISO
     * numbering (Monday=1 ... Sunday=7).
     */
    public List<Integer> getWeeklyOffJsDayIndices() {
        return getWeeklyOffDays().stream()
                .map(day -> day.getValue() % 7)
                .sorted()
                .collect(Collectors.toList());
    }
}
