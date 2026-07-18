package com.example.waorder.repository;

import com.example.waorder.model.DeliveryWeeklyOffDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeliveryWeeklyOffDayRepository extends JpaRepository<DeliveryWeeklyOffDay, Long> {
}
