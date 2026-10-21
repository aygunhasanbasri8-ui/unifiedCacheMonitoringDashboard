package com.ucmd.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenBlacklist {

    private static final Logger logger= LoggerFactory.getLogger(TokenBlacklist.class);

    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    public void blacklist(String token, long expiryMillis) {
        if (token == null || token.isEmpty()) return;
        blacklist.put(token, expiryMillis);
        logger.debug("Token blacklisted, expires at {}", expiryMillis);
    }
    public boolean isBlacklisted(String token) {
        if (token == null) return false;
        Long expiry = blacklist.get(token);
        if (expiry == null ) return false;

        if (System.currentTimeMillis() >= expiry ) {
            blacklist.remove(token);
            return false;
        }
        return true;
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        int before = blacklist.size();
        blacklist.entrySet().removeIf(e -> now >= e.getValue());
        int removed = before - blacklist.size();
        if (removed > 0 ) {
            logger.debug("Cleaned up {} expired blacklisted tokens", removed);
        }
    }

}
