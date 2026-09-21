package com.love0118.clicklink;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ClickLinkPlugin extends JavaPlugin implements Listener {
    private volatile Settings settings;
    private KakcBridge kakc;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configure();
        getServer().getPluginManager().registerEvents(this, this);
        kakc = new KakcBridge(this, () -> settings.enabled() && settings.protectUrls());
        getServer().getPluginManager().registerEvents(kakc, this);
        kakc.install();
    }

    private void configure() {
        TextColor color = TextColor.fromHexString(getConfig().getString("link.color", "#55FFFF"));
        Settings old = settings;
        settings = new Settings(getConfig().getBoolean("enabled", true),
                getConfig().getBoolean("preview.enabled", true), getConfig().getBoolean("kakc.protect-urls", true),
                bounded("preview.wait-millis", 1500, 0, 3000), bounded("link.max-links", 5, 1, 20),
                color == null ? NamedTextColor.AQUA : color,
                new PreviewService(bounded("preview.timeout-millis", 3000, 250, 10000),
                        bounded("preview.max-cache-entries", 512, 16, 10000),
                        bounded("preview.cache-minutes", 60, 1, 1440)));
        if (old != null) old.previews().close();
    }

    private int bounded(String key, int fallback, int min, int max) {
        return Math.clamp(getConfig().getInt(key, fallback), min, max);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Settings current = settings;
        if (!current.enabled()) return;
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        var links = Links.find(plain).stream().limit(current.maxLinks()).toList();
        if (links.isEmpty()) return;
        Map<String, CompletableFuture<String>> pending = new LinkedHashMap<>();
        if (current.previewEnabled()) {
            for (var link : links) pending.computeIfAbsent(link.url(), current.previews()::title);
            // Never wait on the server thread, including synchronous chat invocations.
            if (event.isAsynchronous() && current.waitMillis() > 0) {
                try {
                    CompletableFuture.allOf(pending.values().toArray(CompletableFuture[]::new))
                            .get(current.waitMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ignored) {
                    // Use cached/finished titles or the original clickable URL.
                }
            }
        }
        Map<String, String> titles = new HashMap<>();
        pending.forEach((url, future) -> {
            String title = future.getNow(null);
            if (title != null) titles.put(url, title);
        });
        var previousRenderer = event.renderer();
        event.renderer((source, displayName, message, viewer) -> LinkRenderer.render(
                previousRenderer.render(source, displayName, message, viewer),
                titles, current.color(), current.maxLinks()));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("clicklink.admin")) return true;
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) return false;
        reloadConfig();
        configure();
        sender.sendMessage(Component.text("ClickLink 설정을 다시 불러왔습니다."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return sender.hasPermission("clicklink.admin") && args.length == 1
                && "reload".startsWith(args[0].toLowerCase(java.util.Locale.ROOT)) ? List.of("reload") : List.of();
    }

    @Override
    public void onDisable() {
        if (kakc != null) kakc.close();
        if (settings != null) settings.previews().close();
    }

    private record Settings(boolean enabled, boolean previewEnabled, boolean protectUrls, int waitMillis,
                            int maxLinks, TextColor color, PreviewService previews) {}
}
