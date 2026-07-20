package pl.kiraga.endomondoexportparser.migration;

import org.springframework.stereotype.Service;
import pl.kiraga.endomondoexportparser.format.json.EndomondoJson;
import pl.kiraga.endomondoexportparser.format.json.Location;
import pl.kiraga.endomondoexportparser.format.json.Picture;
import pl.kiraga.endomondoexportparser.service.EndomondoJsonParser;
import pl.kiraga.endomondoexportparser.service.InvalidWorkoutJsonException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Builds the photo handout report: every referenced photo grouped under its workout,
 * geotagged into a copy under {@code data/}, paired with a link to the migrated
 * activity (or "pending migration" before one exists); every unreferenced file under
 * resources/gfx surfaced in an "unmatched" section rather than silently dropped.
 */
@Service
public class PhotoReportGenerator {

    private static final String WORKOUTS_DIR = "Workouts";
    private static final String RESOURCES_DIR = "resources";
    private static final String GFX_DIR = "gfx";

    private final ArchiveScanner scanner;
    private final EndomondoJsonParser parser;
    private final PhotoGeotagger geotagger;

    public PhotoReportGenerator(ArchiveScanner scanner, EndomondoJsonParser parser, PhotoGeotagger geotagger) {
        this.scanner = scanner;
        this.parser = parser;
        this.geotagger = geotagger;
    }

    /**
     * Resolves every referenced photo's location and hands out a geotagged copy under
     * {@code photoCopiesDirectory}, without touching the archive. {@code activityIdsByBasename}
     * supplies Strava activity ids for workouts already migrated; workouts absent from it
     * are reported as pending, which is what a dry-run report (an empty map) produces.
     */
    public PhotoReport build(Path archiveRoot, Path photoCopiesDirectory, Map<String, String> activityIdsByBasename) {

        ArchiveScan scan = scanner.scan(archiveRoot.resolve(WORKOUTS_DIR));

        List<PhotoGroup> groups = new ArrayList<>();
        Set<Path> referenced = new HashSet<>();

        for (WorkoutPair workout : scan.workouts()) {
            EndomondoJson parsed = parseQuietly(workout.json());
            if (parsed == null || parsed.getPictures().isEmpty()) {
                continue;
            }

            Location workoutFirstPoint = parsed.getPoints().isEmpty()
                    ? null : parsed.getPoints().get(0).getLocation();

            List<GeotaggedPhoto> photos = new ArrayList<>();
            for (Picture picture : parsed.getPictures()) {
                if (picture.getUrl() == null) {
                    continue;
                }
                Path relative = Path.of(picture.getUrl()).normalize();
                referenced.add(relative);

                Path source = archiveRoot.resolve(relative);
                Path copy = photoCopiesDirectory.resolve(relative);
                photos.add(geotagger.handOut(source, copy, picture.getPoint(), workoutFirstPoint,
                        picture.getCreated_date()));
            }
            if (photos.isEmpty()) {
                continue;
            }

            groups.add(new PhotoGroup(
                    workout.basename(),
                    parsed.getName(),
                    parsed.getStart_time(),
                    Optional.ofNullable(activityIdsByBasename.get(workout.basename())),
                    List.copyOf(photos)));
        }

        return new PhotoReport(List.copyOf(groups), unmatchedPhotos(archiveRoot, referenced));

    }

    /** Builds the report and writes it as a single self-contained HTML file. */
    public PhotoReport generate(Path archiveRoot, Path outputHtmlFile, Map<String, String> activityIdsByBasename) {
        Path photoCopiesDirectory = outputHtmlFile.resolveSibling("photos");
        PhotoReport report = build(archiveRoot, photoCopiesDirectory, activityIdsByBasename);
        render(report, outputHtmlFile);
        return report;
    }

    private EndomondoJson parseQuietly(Path json) {
        try {
            return parser.parse(Files.readAllBytes(json));
        } catch (InvalidWorkoutJsonException | IOException e) {
            // MigrationPlanner already reports unparseable workouts as skipped; the
            // report simply contributes no photos for one
            return null;
        }
    }

    private List<Path> unmatchedPhotos(Path archiveRoot, Set<Path> referenced) {

        Path gfx = archiveRoot.resolve(RESOURCES_DIR).resolve(GFX_DIR);
        if (!Files.isDirectory(gfx)) {
            return List.of();
        }

        try (Stream<Path> files = Files.walk(gfx)) {
            return files.filter(Files::isRegularFile)
                    .map(archiveRoot::relativize)
                    .filter(relative -> !referenced.contains(relative.normalize()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not scan " + gfx, e);
        }

    }

    private void render(PhotoReport report, Path outputHtmlFile) {

        Path reportDir = outputHtmlFile.toAbsolutePath().normalize().getParent();
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"UTF-8\">")
                .append("<title>Endomondo photo handout</title>")
                .append("<style>")
                .append("body{font-family:sans-serif;margin:2em;background:#fafafa}")
                .append("h1{font-size:1.3em}")
                .append(".workout{margin-bottom:2em;padding:1em;background:#fff;border:1px solid #ddd}")
                .append(".workout h2{font-size:1em;margin:0 0 .3em}")
                .append(".meta{color:#666;font-size:.85em;margin-bottom:.6em}")
                .append(".photos{display:flex;flex-wrap:wrap;gap:.6em}")
                .append(".photos figure{margin:0;width:220px}")
                .append(".photos img{width:220px;height:auto;display:block;border:1px solid #ccc}")
                .append(".photos figcaption{font-size:.75em;color:#666}")
                .append(".no-location{color:#b00}")
                .append(".unmatched li{font-family:monospace;font-size:.85em}")
                .append("</style></head><body>\n")
                .append("<h1>Endomondo photo handout</h1>\n");

        for (PhotoGroup group : report.groups()) {
            html.append("<section class=\"workout\">\n")
                    .append("<h2>").append(escape(nameOrBasename(group))).append("</h2>\n")
                    .append("<div class=\"meta\">")
                    .append(escape(group.startTime() == null ? "" : group.startTime()))
                    .append(" &mdash; ")
                    .append(activityLink(group.activityId()))
                    .append("</div>\n")
                    .append("<div class=\"photos\">\n");
            for (GeotaggedPhoto photo : group.photos()) {
                html.append("<figure><img src=\"").append(relativeHref(reportDir, photo.copy())).append("\" alt=\"\">")
                        .append("<figcaption>")
                        .append(photo.hasLocation()
                                ? photo.location().source().toString()
                                : "<span class=\"no-location\">no location available</span>")
                        .append("</figcaption></figure>\n");
            }
            html.append("</div></section>\n");
        }

        if (!report.unmatchedPhotos().isEmpty()) {
            html.append("<section class=\"workout\"><h2>Unmatched photos</h2><ul class=\"unmatched\">\n");
            for (Path unmatched : report.unmatchedPhotos()) {
                html.append("<li>").append(escape(toSlashes(unmatched))).append("</li>\n");
            }
            html.append("</ul></section>\n");
        }

        html.append("</body></html>\n");

        try {
            Files.createDirectories(reportDir);
            Files.writeString(outputHtmlFile, html.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write report " + outputHtmlFile, e);
        }

    }

    private String nameOrBasename(PhotoGroup group) {
        return (group.name() == null || group.name().isBlank()) ? group.basename() : group.name();
    }

    private String activityLink(Optional<String> activityId) {
        return activityId
                .map(id -> "<a href=\"https://www.strava.com/activities/" + id + "\">view on Strava</a>")
                .orElse("pending migration");
    }

    private String relativeHref(Path reportDir, Path target) {
        return toSlashes(reportDir.relativize(target.toAbsolutePath().normalize()));
    }

    private String toSlashes(Path path) {
        return path.toString().replace('\\', '/');
    }

    private String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

}
