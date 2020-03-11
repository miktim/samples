/**
 * my JSON, java 1.7+
 * MIT (c) 2020 miktim@mail.ru (invented the wheel?)
 *
 * RFC 8259 https://datatracker.ietf.org/doc/rfc8259/?include_text=1
 * Release notes:
 * - supported java types:
 *     JSON object, Object[] array, String, Number, Boolean, null;
 * - parser implements BigDecimal for numbers;
 *
 * Usage: see main method at the end of code
 *
 * Updated: 2020-03-11
 */
package samples.java.org;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.LinkedHashMap;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.Arrays;
import static java.util.Arrays.binarySearch;
import java.util.Vector; //obsolete?

public class JSON implements Cloneable {

    public static Object parse(String json) throws IOException {
        return parse(new StringReader(json));
    }

    public static Object parse(Reader reader) throws IOException {
        return (new Parser()).parse(reader);
    }

    public static String stringify(Object object) throws IllegalArgumentException {
        return stringifyObject(checkObjectType(object));
    }

    private final LinkedHashMap<String, Object> properties = new LinkedHashMap<>();

    public JSON() {
    }

    public String stringify() {
        return stringifyObject(this);
    }

    @Override
    public String toString() {
        return this.stringify();
    }

    @Override
    public JSON clone() throws CloneNotSupportedException {
        super.clone();
        try {
            return (JSON) JSON.parse(this.stringify());
        } catch (IOException e) {
            throw new CloneNotSupportedException();
        }
    }

    public LinkedHashMap<String, Object> listProperties() {
        return this.properties;
    }

    public boolean exists(String propName) {
        return listProperties().containsKey(propName);
    }

    public Object get(String propName) throws IllegalArgumentException {
        if (propName == null || propName.isEmpty() || !exists(propName)) {
            throw new IllegalArgumentException();
        }
        return listProperties().get(propName);
    }

    public JSON set(String propName, Object value) throws IllegalArgumentException {
        if (propName == null || propName.isEmpty()) {
            throw new IllegalArgumentException();
        }
        listProperties().put(propName, checkObjectType(value));
        return this;
    }

    public Object remove(String propName) {
        return this.listProperties().remove(propName);
    }

    static class Parser {

        private static final char[] WHITESPACES = " \n\r\t".toCharArray();
        private static final char[] NUMBERS = "+-0123456789eE.".toCharArray();
        private static final char[] LITERALS = "truefalsn".toCharArray();

        static {
            Arrays.sort(WHITESPACES);
            Arrays.sort(NUMBERS);
            Arrays.sort(LITERALS);
        }

        private Reader reader;
        private int lastChar = 0x20;

        char getChar() throws IOException {
            if (eot()) { // end of text
                throw new IOException("unexpected EOT");
            }
            this.lastChar = this.reader.read();
            return this.lastChar();
        }

        char lastChar() {
            return (char) this.lastChar;
        }

        boolean charIn(char[] chars, char key) {
            return binarySearch(chars, key) >= 0;
        }

        boolean eot() {// end of text?
            return this.lastChar == -1;
        }

        String nextChars(char[] chars) throws IOException {
            StringBuilder sb = new StringBuilder(64); // ???CharBuffer
            while (charIn(chars, lastChar())) {
                sb.append(Character.toString(lastChar()));
                getChar();
            }
            return sb.toString();
        }

        char skipWhitespaces() throws IOException {
            nextChars(WHITESPACES);
            return lastChar();
        }

        boolean expectedChar(char echar) throws IOException {
            if (skipWhitespaces() == echar) {
                getChar(); // skip expected char
                return true;
            }
            return false;
        }

        private Object parseObject() throws IOException {
//            skipWhitespaces(); // leading
            Object object = null;
            if (expectedChar('{')) { // JSON object
                object = new JSON();
                if (!expectedChar('}')) { // empty object
                    do {
                        Object propName = parseObject();
                        if ((propName instanceof String) && expectedChar(':')) {
                            ((JSON) object).set((String) propName, parseObject());
                        } else {
                            throw new IOException("property name expected");
                        }
                    } while (expectedChar(','));
                    if (!expectedChar('}')) {
                        throw new IOException("'}' expected");
                    }
                }
            } else if (expectedChar('[')) { // JSON array
                Vector<Object> list = new Vector<>(); //obsolete?
                if (!expectedChar(']')) { // empty array
                    do {
                        list.add(parseObject());
                    } while (expectedChar(','));
                    if (!expectedChar(']')) {
                        throw new IOException("']' expected");
                    }
                }
                object = list.toArray();
            } else if (lastChar() == '"') { // String
                StringBuilder sb = new StringBuilder(128); // ???CharBuffer
                while (getChar() != '"') {
                    sb.append(lastChar());
                    if (lastChar() == '\\') {
                        sb.append(getChar());
                    }
                }
                getChar(); // skip trailing double quote
                object = unescapeString(sb.toString());
            } else if (charIn(LITERALS, lastChar())) {
                String literal = nextChars(LITERALS);
                switch (literal) {
                    case "true":
                        object = (Boolean) true;
                        break;
                    case "false":
                        object = (Boolean) false;
                        break;
                    case "null":
                        object = null;
                        break;
                    default:
                        throw new IOException("unknown literal");
                }
            } else if (charIn(NUMBERS, lastChar())) {
                object = (Number) new BigDecimal(nextChars(NUMBERS)); // NumberFormatException
            } else {
                throw new IOException("unexpected char");
            }
            skipWhitespaces(); // trailing
            return object;
        }

        Object parse(Reader reader) throws IOException {
            this.reader = reader;
            this.lastChar = 0x20; //!!!
            Object object = parseObject();
            if (!eot()) { // not end of text
                throw new IOException("EOT expected");
            }
            return object;
        }
    }

    static String stringifyObject(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof JSON) {
            LinkedHashMap<String, Object> hmap
                    = ((JSON) value).listProperties();
            StringBuilder sb = new StringBuilder("{");
            String delimiter = "";
            for (Map.Entry<String, Object> entry : hmap.entrySet()) {
                sb.append(delimiter)
                        .append(stringifyObject(entry.getKey()))
                        .append(":")
                        .append(stringifyObject(entry.getValue()));
                delimiter = ",";
            }
            return sb.append("}").toString();
        } else if (value instanceof Object[]) {
            StringBuilder sb = new StringBuilder("[");
            String delimiter = "";
            for (Object object : (Object[]) value) {
                sb.append(delimiter).append(stringifyObject(object));
                delimiter = ",";
            }
            return sb.append("]").toString();
        } else if (value instanceof String) {
            return "\"" + escapeString((String) value) + "\"";
        }
        return value.toString(); // Number, Boolean
    }

    static Object checkObjectType(Object object) throws IllegalArgumentException {
        if (object == null
                || (object instanceof String)
                || (object instanceof Number)
                || (object instanceof Boolean)
                || (object instanceof JSON)) {
            return object;
        } else if (object instanceof Object[]) {
            for (Object entry : (Object[]) object) {
                checkObjectType(entry);
            }
            return object;
        }
        throw new IllegalArgumentException();
    }

    private static final char[] ESCAPED_CHARS = {'"', '/', '\\', 'b', 'f', 'n', 'r', 't'};
    private static final char[] CHARS_UNESCAPED = {0x22, 0x2F, 0x5C, 0x8, 0xC, 0xA, 0xD, 0x9};

    public static String unescapeString(String s) {
        StringBuilder sb = new StringBuilder(64);
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '\\') {
                c = chars[++i];
                int ei = binarySearch(ESCAPED_CHARS, c);
                if (ei >= 0) {
                    sb.append(CHARS_UNESCAPED[ei]);
                    continue;
                } else if (c == 'u') {
                    sb.append((char) Integer.parseInt(new String(chars, ++i, 4), 16));
                    i += 3;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static final int[] UNESCAPED_CHARS = {0x8, 0x9, 0xA, 0xC, 0xD, 0x22, 0x2F, 0x5C}; //
    private static final String[] CHARS_ESCAPED = {"\\b", "\\t", "\\n", "\\f", "\\r", "\\\"", "\\/", "\\\\"};

    public static String escapeString(String s) {
        StringBuilder sb = new StringBuilder(64);
        for (int i = 0; i < s.length(); i++) {
            int c = s.codePointAt(i);
            int ei = binarySearch(UNESCAPED_CHARS, c);
            if (ei >= 0) {
                sb.append(CHARS_ESCAPED[ei]);
            } else if (c <= 0x1F) {
                sb.append(String.format("\\u%04X", c));
            } else if (c >= 0xFFFF) {
                c -= 0x10000;
                sb.append(String.format("\\u%04X\\u%04X",
                        (c >>> 10) + 0xD800, (c & 0x3FF) + 0xDC00)); // surrogates
                i++;
            } else {
                sb.append((char) c);
            }
        }
        return sb.toString();
    }
/*
    public static void main(String[] args) throws Exception {
        JSON json = new JSON();
        json.set("Escaped", new String(new char[]{0x8, 0x9, 0xA, 0xC, 0xD, 0x22, 0x2F, 0x5C, 0, '-', 0x1F, 0xd834, 0xdd1e}))
                .set("EmptyJSON", new JSON())
                .set("EmptyArray", new Object[0])
                .set("Null", null)
                .set("False", (Boolean) false)
                .set("Double", 3.141592653589793238462643383279)
                .set("BigDecimal", new BigDecimal("3.141592653589793238462643383279"))
                .set("MaxLong", Long.MAX_VALUE);
        System.out.println(json);
        System.out.println((JSON) JSON.parse(json.stringify()));
        System.out.println(JSON.escapeString((String) json.get("Escaped")));
        System.out.println(json.clone());
        System.out.println(((Number) json.get("Double")).longValue());
// examples from RFC 8259        
        String example1 = "{\n"
                + "        \"Image\": {\n"
                + "            \"Width\":  800,\n"
                + "            \"Height\": 600,\n"
                + "            \"Title\":  \"View from 15th Floor\",\n"
                + "            \"Thumbnail\": {\n"
//??? in accordance with RFC, the solidus (/) MUST be escaped                
                + "                \"Url\":    \"http://www.example.com/image/481989943\",\n"
                + "                \"Height\": 125,\n"
                + "                \"Width\":  100\n"
                + "            },\n"
                + "            \"Animated\" : false,\n"
                + "            \"IDs\": [116, 943, 234, 38793]\n"
                + "          }\n"
                + "      } ";
        Object object = JSON.parse(example1);
        System.out.println(((JSON) object).stringify());
        System.out.println(((JSON) object).get("Image"));
        System.out.println(((JSON) (((JSON) object).get("Image"))).set("Thumbnail", (Number) 256));
        System.out.println(((JSON) (((JSON) object).get("Image"))).remove("Thumbnail"));
        System.out.println(((JSON) object).clone().stringify());
        String example2 = "[\n"
                + "        {\n"
                + "           \"precision\": \"zip\",\n"
                + "           \"Latitude\":  37.7668,\n"
                + "           \"Longitude\": -122.3959,\n"
                + "           \"Address\":   \"\",\n"
                + "           \"City\":      \"SAN FRANCISCO\",\n"
                + "           \"State\":     \"CA\",\n"
                + "           \"Zip\":       \"94107\",\n"
                + "           \"Country\":   \"US\"\n"
                + "        },\n"
                + "        {\n"
                + "           \"precision\": \"zip\",\n"
                + "           \"Latitude\":  37.371991,\n"
                + "           \"Longitude\": -122.026020,\n"
                + "           \"Address\":   \"\",\n"
                + "           \"City\":      \"SUNNYVALE\",\n"
                + "           \"State\":     \"CA\",\n"
                + "           \"Zip\":       \"94085\",\n"
                + "           \"Country\":   \"US\"\n"
                + "        }\n"
                + "      ]";
        object = JSON.parse(example2);
        System.out.println(JSON.stringify(((Object[]) object)[1]));
    }
*/
}
