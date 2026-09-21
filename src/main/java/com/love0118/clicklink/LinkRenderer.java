package com.love0118.clicklink;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Map;

final class LinkRenderer {
    static Component render(Component message, Map<String, String> titles, TextColor color, int maxLinks) {
        return message.replaceText(TextReplacementConfig.builder().match(Links.PATTERN).times(maxLinks)
                .replacement((match, builder) -> {
                    String raw = match.group();
                    var links = Links.find(raw);
                    if (links.isEmpty()) return builder.build();
                    var link = links.getFirst();
                    String title = titles.get(link.url());
                    Component label = Component.text(title == null ? link.url() : "[" + title + "]")
                            .color(color).decorate(TextDecoration.UNDERLINED)
                            .decoration(TextDecoration.BOLD, title != null)
                            .clickEvent(ClickEvent.openUrl(link.url()))
                            .hoverEvent(HoverEvent.showText(Component.text(link.url())));
                    return Component.empty().append(label).append(Component.text(raw.substring(link.end())));
                }).build());
    }

    private LinkRenderer() {}
}
