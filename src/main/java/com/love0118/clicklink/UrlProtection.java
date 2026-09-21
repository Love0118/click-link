package com.love0118.clicklink;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

record UrlProtection(String masked, Map<String, String> replacements) {
    static UrlProtection mask(String message) {
        String prefix;
        do {
            prefix = "919" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong()) + "919";
        } while (message.contains(prefix));
        var replacements = new LinkedHashMap<String, String>();
        var result = new StringBuilder();
        int cursor = 0;
        for (var link : Links.find(message)) {
            // Preserve KAKC mode 1 ASCII detection, including Unicode inside URLs.
            String token = prefix + replacements.size() + "818";
            if (link.url().chars().anyMatch(c -> c > 127)) token += "\uE000";
            result.append(message, cursor, link.start()).append(token);
            replacements.put(token, link.url());
            cursor = link.end();
        }
        result.append(message.substring(cursor));
        return new UrlProtection(result.toString(), replacements);
    }

    String restore(String message) {
        for (var entry : replacements.entrySet()) message = message.replace(entry.getKey(), entry.getValue());
        return message;
    }
}
