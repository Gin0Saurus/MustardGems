package io.github.gin0saurus.facet.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects config problems so they can be printed once, clearly, at load or /reload. */
public final class Issues {
    private final List<String> warnings = new ArrayList<>();

    public void warn(String message) { warnings.add(message); }
    public List<String> warnings() { return Collections.unmodifiableList(warnings); }
    public boolean isEmpty() { return warnings.isEmpty(); }
}
