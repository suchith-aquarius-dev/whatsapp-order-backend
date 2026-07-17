package com.example.waorder.model;

import org.hibernate.envers.RevisionListener;

public class CustomRevisionListener implements RevisionListener {
    @Override
    public void newRevision(Object revisionEntity) {
        // This method is called when a new revision is created.
        // You can cast revisionEntity to CustomRevisionEntity if you have custom fields
        // and set them here (e.g., current user, IP address, etc.).
        // For now, we'll leave it empty as DefaultRevisionEntity handles timestamp and ID.
    }
}
