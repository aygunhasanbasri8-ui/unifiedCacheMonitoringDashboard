package com.ucmd.service.alert;

import com.ucmd.model.entity.AlertEvent;

/** Bir bildirim kanalı. Yapılandırılmamış kanal sessizce devre dışı kalır. */
public interface AlertChannel {

    String name();

    boolean isConfigured();

    void send(AlertEvent event) throws Exception;

    static String format(AlertEvent event) {
        String icon = switch (event.getState()) {
            case FIRING -> "🔴";
            case RESOLVED -> "✅";
        };
        return icon + " [" + event.getSeverity() + "] " + event.getMessage();
    }
}
