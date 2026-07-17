package com.example.waorder.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.RevisionEntity;

@Entity
@Table(name = "revinfo") // Envers default table name for revision information
@RevisionEntity(CustomRevisionListener.class)
public class CustomRevisionEntity extends DefaultRevisionEntity {
    // No additional fields needed for a basic custom revision entity
    // You can add custom fields here if you need to store more information about the revision
}
