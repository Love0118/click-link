package com.love0118.clicklink;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class Links {
    static final Pattern PATTERN = Pattern.compile("https?://[^\\s<>\"\']+", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    record Link(int start, int end, String url) {}

    static List<Link> find(String text) {
        List<Link> links = new ArrayList<>();
        var matcher = PATTERN.matcher(text);
        while (matcher.find()) {
            String url = matcher.group();
            int end = url.length();
            while (end > 0) {
                char last = url.charAt(end - 1);
                if (".,!?;:".indexOf(last) >= 0 || unbalanced(url.substring(0, end), last)) end--;
                else break;
            }
            url = url.substring(0, end);
            try {
                URI uri = URI.create(url);
                if (uri.getHost() != null && uri.getRawUserInfo() == null) {
                    links.add(new Link(matcher.start(), matcher.start() + end, url));
                }
            } catch (IllegalArgumentException ignored) {
                // Leave malformed links untouched.
            }
        }
        return links;
    }

    private static boolean unbalanced(String url, char last) {
        int index = ")]}".indexOf(last);
        if (index < 0) return false;
        char open = "([{".charAt(index);
        return url.chars().filter(c -> c == last).count() > url.chars().filter(c -> c == open).count();
    }

    private Links() {}
}
