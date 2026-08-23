package com.cobre.notifications.delivery.core.domain;

/**
 * Estado terminal único de una notificación (constitución, Principio IV).
 * No existe un tercer valor "pendiente"/"en curso": la ausencia de
 * {@link DeliveryOutcome} es lo que representa "aún no resuelto".
 */
public enum DeliveryStatus {
    COMPLETED,
    FAILED
}
