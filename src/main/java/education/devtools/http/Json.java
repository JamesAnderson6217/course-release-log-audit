package education.devtools.http;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small JSON codec kept here so the example builds with only the JDK. */
public final class Json {
    private Json() {}

    public static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return quote(text);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            boolean comma = false;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (comma) out.append(',');
                out.append(quote(String.valueOf(entry.getKey()))).append(':').append(stringify(entry.getValue()));
                comma = true;
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable<?> values) {
            StringBuilder out = new StringBuilder("[");
            boolean comma = false;
            for (Object item : values) {
                if (comma) out.append(',');
                out.append(stringify(item));
                comma = true;
            }
            return out.append(']').toString();
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
    }

    public static Object parse(String source) {
        Parser parser = new Parser(source);
        Object value = parser.value();
        parser.space();
        if (!parser.done()) throw parser.failure("Trailing content");
        return value;
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char character : text.toCharArray()) {
            switch (character) {
                case '\"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 32) out.append(String.format("\\u%04x", (int) character));
                    else out.append(character);
                }
            }
        }
        return out.append('\"').toString();
    }

    private static final class Parser {
        private final String source;
        private int index;
        private Parser(String source) { this.source = source; }
        private boolean done() { return index == source.length(); }
        private void space() { while (!done() && Character.isWhitespace(source.charAt(index))) index++; }
        private IllegalArgumentException failure(String message) { return new IllegalArgumentException(message + " at " + index); }

        private Object value() {
            space();
            if (done()) throw failure("Expected value");
            return switch (source.charAt(index)) {
                case '{' -> object();
                case '[' -> array();
                case '\"' -> string();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> result = new LinkedHashMap<>();
            index++;
            space();
            if (take('}')) return result;
            do {
                space();
                if (done() || source.charAt(index) != '\"') throw failure("Expected object key");
                String key = string();
                space();
                if (!take(':')) throw failure("Expected colon");
                result.put(key, value());
                space();
            } while (take(','));
            if (!take('}')) throw failure("Expected object end");
            return result;
        }

        private List<Object> array() {
            List<Object> result = new ArrayList<>();
            index++;
            space();
            if (take(']')) return result;
            do { result.add(value()); space(); } while (take(','));
            if (!take(']')) throw failure("Expected array end");
            return result;
        }

        private String string() {
            StringBuilder out = new StringBuilder();
            index++;
            while (!done()) {
                char character = source.charAt(index++);
                if (character == '\"') return out.toString();
                if (character != '\\') { out.append(character); continue; }
                if (done()) throw failure("Expected escape");
                char escaped = source.charAt(index++);
                switch (escaped) {
                    case '\"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (index + 4 > source.length()) throw failure("Expected unicode escape");
                        out.append((char) Integer.parseInt(source.substring(index, index + 4), 16));
                        index += 4;
                    }
                    default -> throw failure("Unknown escape");
                }
            }
            throw failure("Expected closing quote");
        }

        private Object number() {
            int start = index;
            while (!done() && "-+0123456789.eE".indexOf(source.charAt(index)) >= 0) index++;
            if (start == index) throw failure("Expected number");
            String token = source.substring(start, index);
            return token.contains(".") || token.contains("e") || token.contains("E")
                    ? Double.parseDouble(token) : Long.parseLong(token);
        }

        private Object literal(String token, Object value) {
            if (!source.startsWith(token, index)) throw failure("Expected " + token);
            index += token.length();
            return value;
        }

        private boolean take(char expected) {
            if (!done() && source.charAt(index) == expected) { index++; return true; }
            return false;
        }
    }
}
