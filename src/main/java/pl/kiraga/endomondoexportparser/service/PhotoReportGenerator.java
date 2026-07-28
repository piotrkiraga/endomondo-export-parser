package pl.kiraga.endomondoexportparser.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import pl.kiraga.endomondoexportparser.dto.endomondo.EndomondoJsonDto;
import pl.kiraga.endomondoexportparser.dto.endomondo.LocationDto;
import pl.kiraga.endomondoexportparser.dto.endomondo.PictureDto;
import pl.kiraga.endomondoexportparser.exception.InvalidWorkoutJsonException;
import pl.kiraga.endomondoexportparser.model.ArchiveScan;
import pl.kiraga.endomondoexportparser.model.CaptionedPhoto;
import pl.kiraga.endomondoexportparser.model.GeotaggedPhoto;
import pl.kiraga.endomondoexportparser.model.LocationSource;
import pl.kiraga.endomondoexportparser.model.PhotoGroup;
import pl.kiraga.endomondoexportparser.model.PhotoReport;
import pl.kiraga.endomondoexportparser.model.PlaceDescription;
import pl.kiraga.endomondoexportparser.model.WorkoutPair;
import pl.kiraga.endomondoexportparser.service.EndomondoJsonParser;
import pl.kiraga.endomondoexportparser.util.ReportStylesUtil;
import pl.kiraga.endomondoexportparser.util.SportMappingUtil;

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
    private final PlaceLookup placeLookup;

    public PhotoReportGenerator(ArchiveScanner scanner, EndomondoJsonParser parser, PhotoGeotagger geotagger,
                                 PlaceLookup placeLookup) {
        this.scanner = scanner;
        this.parser = parser;
        this.geotagger = geotagger;
        this.placeLookup = placeLookup;
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
            EndomondoJsonDto parsed = parseQuietly(workout.json());
            if (parsed == null || parsed.getPictures().isEmpty()) {
                continue;
            }

            LocationDto workoutFirstPoint = parsed.getPoints().isEmpty()
                    ? null : parsed.getPoints().get(0).getLocation();

            List<CaptionedPhoto> photos = new ArrayList<>();
            for (PictureDto picture : parsed.getPictures()) {
                if (picture.getUrl() == null) {
                    continue;
                }
                Path relative = Path.of(picture.getUrl()).normalize();
                referenced.add(relative);

                Path source = archiveRoot.resolve(relative);
                Path copy = photoCopiesDirectory.resolve(relative);
                GeotaggedPhoto geotagged = geotagger.handOut(source, copy, picture.getPoint(), workoutFirstPoint,
                        picture.getCreated_date());
                PlaceDescription photoPlace = geotagged.hasLocation()
                        ? placeLookup.lookup(geotagged.location().latitude(), geotagged.location().longitude()).orElse(null)
                        : null;
                photos.add(new CaptionedPhoto(geotagged, photoPlace));
            }
            if (photos.isEmpty()) {
                continue;
            }

            PlaceDescription workoutPlace = workoutFirstPoint == null ? null
                    : placeLookup.lookup(workoutFirstPoint.getLatitude(), workoutFirstPoint.getLongitude()).orElse(null);

            groups.add(new PhotoGroup(
                    workout.basename(),
                    parsed.getName(),
                    parsed.getStart_time(),
                    SportMappingUtil.stravaSportType(parsed.getSport()).orElse(null),
                    workoutPlace,
                    Optional.ofNullable(activityIdsByBasename.get(workout.basename())),
                    List.copyOf(photos)));
        }

        return new PhotoReport(List.copyOf(groups), unmatchedPhotos(archiveRoot, referenced));

    }

    /** Builds the report and writes it as a single self-contained HTML file. */
    public PhotoReport generate(Path archiveRoot, Path outputHtmlFile, Map<String, String> activityIdsByBasename) {
        Path photoCopiesDirectory = outputHtmlFile.resolveSibling("photos");
        PhotoReport report = build(archiveRoot, photoCopiesDirectory, activityIdsByBasename);
        render(report, outputHtmlFile, archiveRoot);
        return report;
    }

    private EndomondoJsonDto parseQuietly(Path json) {
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

    private void render(PhotoReport report, Path outputHtmlFile, Path archiveRoot) {

        Path reportDir = outputHtmlFile.toAbsolutePath().normalize().getParent();
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"UTF-8\">")
                .append("<title>Endomondo photo handout</title>")
                .append("<style>").append(ReportStylesUtil.CSS).append("</style></head><body>\n")
                .append("<h1>Endomondo photo handout</h1>\n");

        for (PhotoGroup group : report.groups()) {
            html.append("<section class=\"workout\">\n")
                    .append("<h2>").append(escape(group.displayName())).append("</h2>\n")
                    .append("<div class=\"meta\">")
                    .append(escape(group.startTime() == null ? "" : group.startTime()))
                    .append(" &mdash; ")
                    .append(activityLink(group.activityId()))
                    .append(" &mdash; <span class=\"basename\">archive: ").append(escape(group.basename())).append("</span>")
                    .append("</div>\n")
                    .append("<div class=\"photos\">\n");
            for (CaptionedPhoto captioned : group.photos()) {
                String href = relativeHref(reportDir, captioned.photo().copy());
                String caption = caption(captioned);
                html.append("<figure><img src=\"").append(href).append("\" alt=\"\" onclick=\"openLightbox(this.src)\">")
                        .append("<figcaption>")
                        .append(caption != null ? escape(caption) : "<span class=\"no-location\">no location available</span>")
                        .append("</figcaption></figure>\n");
            }
            html.append("</div></section>\n");
        }

        if (!report.unmatchedPhotos().isEmpty()) {
            // Copied next to the matched photos (rather than linked in place under
            // archiveRoot) so the thumbnail resolves both when this file is opened
            // straight from disk and when it's served through the app's own
            // /photo-report/** mapping, which only covers this report's own directory.
            Path unmatchedCopiesDirectory = reportDir.resolve("photos").resolve("unmatched");
            html.append("<section class=\"workout\"><h2>Unmatched photos</h2>")
                    .append("<div class=\"meta\">not referenced by any workout, so they weren't geotagged</div>\n")
                    .append("<div class=\"photos\">\n");
            for (Path unmatched : report.unmatchedPhotos()) {
                Path copy = unmatchedCopiesDirectory.resolve(unmatched);
                copyQuietly(archiveRoot.resolve(unmatched), copy);
                String href = relativeHref(reportDir, copy);
                html.append("<figure><img src=\"").append(href).append("\" alt=\"\" onclick=\"openLightbox(this.src)\">")
                        .append("<figcaption class=\"unmatched\">").append(escape(toSlashes(unmatched))).append("</figcaption></figure>\n");
            }
            html.append("</div></section>\n");
        }

        html.append("<div id=\"lightbox\" onclick=\"this.classList.remove('open')\"><img id=\"lightbox-img\" src=\"\" alt=\"\"></div>\n")
                .append("<script>function openLightbox(src){")
                .append("document.getElementById('lightbox-img').src=src;")
                .append("document.getElementById('lightbox').classList.add('open');}</script>\n");

        html.append("</body></html>\n");

        try {
            Files.createDirectories(reportDir);
            Files.writeString(outputHtmlFile, html.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write report " + outputHtmlFile, e);
        }

    }

    /** The resolved place when one was found; otherwise the raw {@link LocationSource}, or null (no location at all). */
    private String caption(CaptionedPhoto captioned) {
        if (captioned.place() != null) {
            return "Recorded " + captioned.place().phrase();
        }
        if (captioned.photo().hasLocation()) {
            return captioned.photo().location().source().toString();
        }
        return null;
    }

    private String activityLink(Optional<String> activityId) {
        return activityId
                .map(id -> "<a href=\"https://www.strava.com/activities/" + id + "\">view on Strava</a>")
                .orElse("pending migration");
    }

    /** Best-effort: an unmatched photo missing its thumbnail still shows its path in the caption. */
    private void copyQuietly(Path source, Path target) {
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // ignored, see above
        }
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
