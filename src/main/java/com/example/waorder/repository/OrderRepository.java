package com.example.waorder.repository;

import com.example.waorder.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByDeliveryDateBetweenOrderByDeliveryDateAscDeliveryTimeAsc(LocalDate startDate, LocalDate endDate);

    List<Order> findByDeliveryDateBetweenAndStatusOrderByDeliveryDateAscDeliveryTimeAsc(LocalDate startDate, LocalDate endDate, Order.OrderStatus status);

    @Query("SELECT DISTINCT o.status FROM Order o")
    List<Order.OrderStatus> findDistinctStatuses();
}
