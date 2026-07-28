package pl.kiraga.endomondoexportparser.util;

/**
 * OpenStreetMap tags a bilingual place's own "name" tag as "French name - Dutch name"
 * (common around Brussels) — a property of the OSM data itself, not something request
 * language negotiation changes. An earlier version of this fix asked Nominatim for
 * {@code accept-language=en} instead, which also anglicized every monolingual place —
 * Kraków became "Krakow" — for no benefit, since Brussels' bilingual name persisted
 * either way. Trimming to the first segment fixes the actual cause without that cost.
 */
public final class OsmNamesUtil {

    private OsmNamesUtil() {
    }

    public static String primary(String raw) {
        if (raw == null) {
            return null;
        }
        int separator = raw.indexOf(" - ");
        return separator < 0 ? raw : raw.substring(0, separator).trim();
    }

}
