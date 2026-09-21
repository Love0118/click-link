package com.love0118.clicklink;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatPipelineTest {
    @Test void defaultPaperRendererProducesClickableTitleInsideTranslationArgument() throws Exception {
        var player = mock(Player.class);
        String url = "https://example.com";
        Component original = ChatRenderer.defaultRenderer().render(player, Component.text("Tester"),
                Component.text(url + " 안녕하세요"), player);
        Component rendered = LinkRenderer.render(original, java.util.Map.of(url, "Example"),
                net.kyori.adventure.text.format.NamedTextColor.GREEN, 5);
        var translated = assertInstanceOf(net.kyori.adventure.text.TranslatableComponent.class, rendered);
        assertEquals("chat.type.text", translated.key());
        assertEquals(Component.text("Tester"), translated.arguments().get(0).asComponent().compact());
        Component body = translated.arguments().get(1).asComponent();
        assertEquals("[Example] 안녕하세요", PlainTextComponentSerializer.plainText().serialize(body));
        assertEquals("§6[§aExample§6]§f 안녕하세요", LegacyComponentSerializer.legacySection().serialize(body));
        String json = GsonComponentSerializer.gson().serialize(rendered);
        assertTrue(json.contains("open_url"));
        assertTrue(json.contains(url));
        assertTrue(json.contains("show_text"));
    }

    @Test void preservesLegacyFormatterAndAddsClickEventsAfterSerialization() throws Exception {
        var plugin = mock(ClickLinkPlugin.class, CALLS_REAL_METHODS);
        var config = new YamlConfiguration();
        config.set("preview.enabled", false);
        doReturn(config).when(plugin).getConfig();
        var configure = ClickLinkPlugin.class.getDeclaredMethod("configure");
        configure.setAccessible(true);
        configure.invoke(plugin);
        try {
            var player = mock(Player.class);
            var legacy = LegacyComponentSerializer.legacySection();
            ChatRenderer renderer = (source, display, message, viewer) ->
                    legacy.deserialize("[Rank] Tester: " + legacy.serialize(message));
            Component message = Component.text("https://example.com 안녕하세요");
            var viewers = new HashSet<net.kyori.adventure.audience.Audience>();
            viewers.add(player);
            var event = new AsyncChatEvent(false, player, viewers, renderer, message, message, null);
            plugin.onChat(event);
            Component result = event.renderer().render(player, Component.text("Tester"), event.message(), player);
            assertEquals("[Rank] Tester: [https://example.com] 안녕하세요",
                    PlainTextComponentSerializer.plainText().serialize(result));
            assertTrue(GsonComponentSerializer.gson().serialize(result).contains("open_url"));
            assertSame(viewers, event.viewers());
            assertFalse(event.isCancelled());
        } finally {
            plugin.onDisable();
        }
    }
}
