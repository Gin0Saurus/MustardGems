package io.github.gin0saurus.facet.core;

import java.util.UUID;

/** What a gem item carries: which ledger record it claims to be, and which version of it. */
public record Token(UUID id, int version) {

    /** Parses the "uuid:version" form stored on items. Returns null for anything malformed. */
    public static Token parse(String raw) {
        if (raw == null) return null;
        int i = raw.lastIndexOf(':');
        if (i <= 0 || i == raw.length() - 1) return null;
        try {
            return new Token(UUID.fromString(raw.substring(0, i)), Integer.parseInt(raw.substring(i + 1)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String encode() { return id + ":" + version; }
}
