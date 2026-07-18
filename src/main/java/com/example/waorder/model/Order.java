package com.example.waorder.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.envers.Audited; // Import Audited

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "orders")
@Audited // Add Audited annotation
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** WhatsApp user id (phone number in international format, no +) */
    @Column(nullable = false)
    private String waId;

    private String customerName;

    /** Date the customer wants the order delivered. */
    private LocalDate deliveryDate;

    /** Time of day (on deliveryDate) the customer wants the order delivered. */
    private LocalTime deliveryTime;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.CREATED;

    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    private Instant createdAt = Instant.now();

    public enum OrderStatus {
        CREATED, PAYMENT_LINK_SENT, PAID, CANCELLED
    }
}
