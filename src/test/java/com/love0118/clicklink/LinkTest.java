package com.love0118.clicklink;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LinkTest {
    @Test void detectsMultipleUrlsAndKeepsBalancedParentheses() {
        var links = Links.find("(https://example.com/a_(b)). https://youtu.be/id!\u00a0hello");
        assertEquals(2, links.size());
        assertEquals("https://example.com/a_(b)", links.get(0).url());
        assertEquals("https://youtu.be/id", links.get(1).url());
    }

    @Test void rejectsMalformedUrlsAndCredentials() {
        assertTrue(Links.find("https:// https://user:pass@example.com https://example.com/%zz").isEmpty());
    }

    @Test void restoresRepeatedUrlsWithQueriesExactly() {
        String url = "https://www.youtube.com/watch?v=pM8_wnJ7JsE&list=abc";
        String input = url + " dkssudgktpdy " + url;
        var protectedText = UrlProtection.mask(input);
        assertFalse(protectedText.masked().contains(url));
        assertEquals(input, protectedText.restore(protectedText.masked()));
    }

    @Test void extractsMetadataWithEntitiesAndQuotes() {
        var doc = Jsoup.parse("<title>fallback</title><meta content='Artist&#39;s &amp; song' property='og:title'>");
        assertEquals("Artist's & song", PreviewService.extractTitle(doc));
        assertEquals("Fallback", PreviewService.extractTitle(Jsoup.parse("<meta property=og:title content='  '><title>Fallback</title>")));
        assertNull(PreviewService.extractTitle(Jsoup.parse("<body>none</body>")));
    }

    @Test void rendersTitleAndOriginalClickTargetWithoutLosingSurroundingStyle() {
        String url = "https://www.youtube.com/watch?v=pM8_wnJ7JsE";
        String title = "Overdose (なとり) / 아오쿠모 린 (Aokumo Rin) Cover";
        var input = Component.text(url + " 안녕하세요", NamedTextColor.GREEN);
        var result = LinkRenderer.render(input, Map.of(url, title), NamedTextColor.AQUA, 5);
        assertEquals("[" + title + "] 안녕하세요", PlainTextComponentSerializer.plainText().serialize(result));
        assertEquals(NamedTextColor.GREEN, result.color());
        String json = GsonComponentSerializer.gson().serialize(result);
        assertTrue(json.contains("open_url"));
        assertTrue(json.contains("pM8_wnJ7JsE"));
        assertTrue(json.contains("show_text"));
    }

    @Test void fallbackKeepsUrlAndPunctuation() {
        String message = "See (https://example.com).";
        var result = LinkRenderer.render(Component.text(message), Map.of(), NamedTextColor.AQUA, 5);
        assertEquals(message, PlainTextComponentSerializer.plainText().serialize(result));
        assertTrue(GsonComponentSerializer.gson().serialize(result).contains("open_url"));
    }

    @Test void blocksLocalAndSpecialNetworkAddresses() throws Exception {
        for (String host : new String[]{"127.0.0.1", "10.0.0.1", "169.254.169.254", "192.168.1.1", "100.64.0.1", "::1", "fc00::1"}) {
            assertFalse(PreviewService.publicAddress(InetAddress.getByName(host)), host);
        }
        assertTrue(PreviewService.publicAddress(InetAddress.getByName("8.8.8.8")));
        assertThrows(IllegalArgumentException.class, () -> PreviewService.validate(URI.create("file:///etc/passwd")));
        assertThrows(IllegalArgumentException.class, () -> PreviewService.validate(URI.create("http://127.0.0.1/")));
        assertThrows(IllegalArgumentException.class, () -> PreviewService.validate(URI.create("https://example.com:8080/")));
    }
}
