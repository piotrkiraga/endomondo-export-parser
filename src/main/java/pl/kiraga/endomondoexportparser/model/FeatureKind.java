package pl.kiraga.endomondoexportparser.model;

/**
 * The kind of notable nearby feature a workout might be named or described after, in
 * priority order (lower {@link #priority()} wins when several are nearby): a river or
 * other water body beats a historic landmark beats a park beats a merely-named road.
 */
public enum FeatureKind {

    WATER(0, "along"),
    HISTORIC(1, "near"),
    PARK(2, "through"),
    BOULEVARD(3, "along");

    private final int priority;
    private final String preposition;

    FeatureKind(int priority, String preposition) {
        this.priority = priority;
        this.preposition = preposition;
    }

    public int priority() {
        return priority;
    }

    public String preposition() {
        return preposition;
    }

}
