package com.cobre.notifications.delivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Worker headless (sin HTTP): consume {@code raw-events} y {@code deliveries},
 * entrega notificaciones a webhooks de cliente. Ver CLAUDE.md.
 */
@SpringBootApplication
public class NotifierApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotifierApplication.class, args);
    }
}
