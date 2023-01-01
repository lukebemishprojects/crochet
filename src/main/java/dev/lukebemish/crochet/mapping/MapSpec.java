package dev.lukebemish.crochet.mapping;

import java.io.Serializable;

public sealed interface MapSpec extends Serializable {
    final class Unmapped implements MapSpec {
        public static final Unmapped INSTANCE = new Unmapped();
        private Unmapped() {}

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Unmapped;
        }

        @Override
        public String toString() {
            return "MapSpec.Unmapped";
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    record Named(String start, String end) implements MapSpec {
        @Override
        public String toString() {
            return "MapSpec.Named[" +
                    "start=" + start +
                    ", end=" + end +
                    ']';
        }
    }
}
