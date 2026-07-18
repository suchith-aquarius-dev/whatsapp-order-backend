package com.example.waorder.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * A single calendar date on which delivery is not available (e.g. a public
 * holiday). Managed by the admin from the Delivery Settings page.
 */
@Data
@Entity
@Table(name = "delivery_holidays")
public class DeliveryHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate date;

    /** Optional label shown to the admin, e.g. "Diwali" or "Christmas Day". */
    private String reason;
}
