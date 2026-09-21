package com.love0118.clicklink;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

@SuppressWarnings("deprecation")
final class KakcBridge implements Listener, AutoCloseable {
    private final JavaPlugin owner;
    private final BooleanSupplier enabled;
    private final List<Replacement> replacements = new ArrayList<>();

    KakcBridge(JavaPlugin owner, BooleanSupplier enabled) {
        this.owner = owner;
        this.enabled = enabled;
    }

    void install() {
        var registered = List.of(AsyncPlayerChatEvent.getHandlerList().getRegisteredListeners());
        replacements.removeIf(replacement -> !registered.contains(replacement.wrapper()));
        for (var original : AsyncPlayerChatEvent.getHandlerList().getRegisteredListeners()) {
            if (!original.getPlugin().getName().equalsIgnoreCase("KAKC")) continue;
            if (replacements.stream().anyMatch(r -> r.wrapper() == original)) continue;
            if (!original.getListener().getClass().getName().equals("me.desktop.KAKC.Main")) {
                owner.getLogger().warning("Unknown KAKC implementation; URL protection was not installed.");
                continue;
            }
            var wrapper = new RegisteredListener(original.getListener(), (listener, event) -> {
                var chat = (AsyncPlayerChatEvent) event;
                if (!enabled.getAsBoolean() || chat.isCancelled()) {
                    original.callEvent(event);
                    return;
                }
                var protection = UrlProtection.mask(chat.getMessage());
                chat.setMessage(protection.masked());
                try {
                    original.callEvent(event);
                } finally {
                    chat.setMessage(protection.restore(chat.getMessage()));
                }
            }, original.getPriority(), original.getPlugin(), original.isIgnoringCancelled());
            AsyncPlayerChatEvent.getHandlerList().unregister(original);
            AsyncPlayerChatEvent.getHandlerList().register(wrapper);
            replacements.add(new Replacement(original, wrapper));
            owner.getLogger().info("KAKC URL protection installed; surrounding text still uses KAKC conversion.");
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equalsIgnoreCase("KAKC")) install();
    }

    @Override
    public void close() {
        for (var replacement : replacements) {
            if (!List.of(AsyncPlayerChatEvent.getHandlerList().getRegisteredListeners())
                    .contains(replacement.wrapper())) continue;
            AsyncPlayerChatEvent.getHandlerList().unregister(replacement.wrapper());
            if (replacement.original().getPlugin().isEnabled()) {
                AsyncPlayerChatEvent.getHandlerList().register(replacement.original());
            }
        }
        replacements.clear();
    }

    private record Replacement(RegisteredListener original, RegisteredListener wrapper) {}
}
