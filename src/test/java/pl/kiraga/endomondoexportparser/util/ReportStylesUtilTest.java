package pl.kiraga.endomondoexportparser.util;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReportStylesUtilTest {

    @Test
    void migratedAndUploadedCardsUseDifferentAccentColors() {
        String migratedAccent = resolveVariable(borderColorOf("\\.workout\\.migrated"));
        String uploadedAccent = borderColorOf("\\.workout\\.uploaded");

        assertNotEquals(migratedAccent, uploadedAccent,
                "a workout that is both migrated and marked as uploaded must not render both states identically");
    }

    @Test
    void migratedAccentIsDefinedForBothThemes() {
        assertTrue(ReportStylesUtil.CSS.contains("--migrated-accent:#6f42c1"), "light theme migrated accent");
        assertTrue(ReportStylesUtil.CSS.contains("--migrated-accent:#a98eda"), "dark theme migrated accent");
        assertTrue(ReportStylesUtil.CSS.contains("--migrated-bg:#ede7f6"), "light theme migrated background");
        assertTrue(ReportStylesUtil.CSS.contains("--migrated-bg:#1e1526"), "dark theme migrated background");
    }

    @Test
    void markUploadedButtonMatchesTheUploadedCardAccent() {
        assertTrue(ReportStylesUtil.CSS.contains(
                        ".workout.uploaded .mark-uploaded{color:#2a9d5c;border-color:#2a9d5c}"),
                "the button's marked styling must stay paired with the card accent");
    }

    private static String borderColorOf(String selectorPattern) {
        Matcher matcher = Pattern.compile(selectorPattern + "\\{border-color:([^;}]+)").matcher(ReportStylesUtil.CSS);
        assertTrue(matcher.find(), "no border-color rule for " + selectorPattern);
        return matcher.group(1);
    }

    private static String resolveVariable(String value) {
        Matcher reference = Pattern.compile("var\\((--[a-z-]+)\\)").matcher(value);
        if (!reference.find()) {
            return value;
        }
        Matcher definition = Pattern.compile(reference.group(1) + ":([^;}]+)").matcher(ReportStylesUtil.CSS);
        assertTrue(definition.find(), "undefined custom property " + reference.group(1));
        return definition.group(1);
    }

}
