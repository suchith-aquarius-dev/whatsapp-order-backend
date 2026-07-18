package com.example.waorder.repository;

import com.example.waorder.model.DeliveryHoliday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DeliveryHolidayRepository extends JpaRepository<DeliveryHoliday, Long> {
    boolean existsByDate(LocalDate date);
    List<DeliveryHoliday> findAllByOrderByDateAsc();
}
