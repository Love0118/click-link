package com.love0118.clicklink;

import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

@SuppressWarnings("deprecation")
class KakcIntegrationTest {
    @Test void realKakcHandlerConvertsTextButPreservesUrlsAndPlayerModes() throws Exception {
        String jar = System.getProperty("kakc.jar");
        assumeTrue(jar != null, "Set -Dkakc.jar to test the supplied KAKC 1.2 binary");
        try (var loader = new URLClassLoader(new java.net.URL[]{Path.of(jar).toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> type = loader.loadClass("me.desktop.KAKC.Main");
            var plugin = (JavaPlugin) mock(type, CALLS_REAL_METHODS);
            doReturn("KAKC").when(plugin).getName();
            doReturn(true).when(plugin).isEnabled();
            Map<String, Integer> modes = new HashMap<>();
            type.getField("changingMod").set(plugin, modes);
            type.getField("hangeulKey").set(plugin, new HashMap<String, Integer>());
            var handler = type.getMethod("onAsyncPlayerChat", AsyncPlayerChatEvent.class);
            var original = new RegisteredListener((org.bukkit.event.Listener) plugin, (listener, event) -> {
                try { handler.invoke(plugin, event); }
                catch (ReflectiveOperationException exception) { throw new org.bukkit.event.EventException(exception); }
            }, EventPriority.NORMAL, plugin, false);
            var owner = mock(JavaPlugin.class);
            when(owner.getLogger()).thenReturn(Logger.getAnonymousLogger());
            var player = mock(Player.class);
            when(player.getName()).thenReturn("Tester");
            var handlers = AsyncPlayerChatEvent.getHandlerList();
            handlers.register(original);
            try (var bridge = new KakcBridge(owner, () -> true)) {
                bridge.install();
                bridge.install();
                assertEquals(1, handlers.getRegisteredListeners().length);
                String url = "https://www.youtube.com/watch?v=pM8_wnJ7JsE&list=abc";
                for (int mode : new int[]{1, 2, 0}) {
                    modes.put("Tester", mode);
                    var event = new AsyncPlayerChatEvent(true, player, url + " dkssudgktpdy", new HashSet<>());
                    handlers.getRegisteredListeners()[0].callEvent(event);
                    assertEquals(url + (mode == 0 ? " dkssudgktpdy" : " 안녕하세요"), event.getMessage());
                    assertEquals(mode, modes.get("Tester"));
                }
            } finally {
                handlers.unregister(original);
            }
        }
    }
}
