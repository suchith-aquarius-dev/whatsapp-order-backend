package com.example.waorder.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.DayOfWeek;

/**
 * A day of the week on which delivery is never available (e.g. every
 * Monday). Managed by the admin from the Delivery Settings page.
 */
@Data
@Entity
@Table(name = "delivery_weekly_off_days")
public class DeliveryWeeklyOffDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private DayOfWeek dayOfWeek;
}
