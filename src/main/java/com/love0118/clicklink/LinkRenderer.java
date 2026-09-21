package com.love0118.clicklink;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Map;

final class LinkRenderer {
    static Component render(Component message, Map<String, String> titles, TextColor color, int maxLinks) {
        return render(message, titles, color, maxLinks, new int[]{0});
    }

    private static Component render(Component message, Map<String, String> titles, TextColor color,
                                    int maxLinks, int[] count) {
        Component result = message.children(java.util.List.of());
        if (message instanceof TextComponent text) {
            result = text.content("").children(java.util.List.of());
            int cursor = 0;
            for (var link : Links.find(text.content())) {
                if (count[0] >= maxLinks) break;
                result = result.append(surrounding(text.content().substring(cursor, link.start()), count[0] > 0));
                String title = titles.get(link.url());
                Component label = Component.text("[", NamedTextColor.GOLD)
                        .decoration(TextDecoration.BOLD, false)
                        .decoration(TextDecoration.UNDERLINED, false)
                        .append(Component.text(title == null ? link.url() : title, color))
                        .append(Component.text("]", NamedTextColor.GOLD))
                        .clickEvent(ClickEvent.openUrl(link.url()))
                        .hoverEvent(HoverEvent.showText(Component.text(link.url())));
                result = result.append(label);
                cursor = link.end();
                count[0]++;
            }
            result = result.append(surrounding(text.content().substring(cursor), count[0] > 0));
        }
        for (Component child : message.children()) {
            result = result.append(render(child, titles, color, maxLinks, count));
        }
        return result;
    }

    private static Component surrounding(String text, boolean afterLink) {
        Component component = Component.text(text);
        return afterLink ? component.color(NamedTextColor.WHITE)
                .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.UNDERLINED, false) : component;
    }

    private LinkRenderer() {}
}
