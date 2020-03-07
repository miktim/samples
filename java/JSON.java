/**
 * my JSON, java 1.7+
 * MIT (c) 2020 miktim@mail.ru (sorry, could not resist - invented the wheel)
 * 
 * rfc8259 https://datatracker.ietf.org/doc/rfc8259/?include_text=1
 * Limitations:
 * - Numbers: implements double precision;
 * - Strings: escape/unescape double quotes only;
 * - JSON object properties in unpredictable order
 *
 * Supported types:
 * JSON object, Object[] array, String, Number, Boolean, null
 * Usage: see main method at the end of code
 *
 * Updated: 2020.03.07
 */
package samples.miktim.org;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.HashMap;
import java.io.StringReader;
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

    private final HashMap<String, Object> properties = new HashMap<>(10);

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

    public HashMap<String, Object> listProperties() {
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

    public Object set(String propName, Object value) throws IllegalArgumentException {
        if (propName == null || propName.isEmpty()) {
            throw new IllegalArgumentException();
        }
        return listProperties().put(propName, checkObjectType(value));
    }

    public Object remove(String propName) {
        return this.listProperties().remove(propName);
    }

    private static class Parser {

        private static final char[] WHITESPACES = " \n\r\t".toCharArray();
        private static final char[] NUMBERS = "+-0123456789eE.".toCharArray();
        private static final char[] LITERALS = "truefalsn".toCharArray();

        static {
            Arrays.sort(WHITESPACES);
            Arrays.sort(NUMBERS);
            Arrays.sort(LITERALS);
        }

        private Reader reader;
        private char lastChar = ' ';

        char getChar() throws IOException {
            if (eot()) { // end of text
                syntaxException();
            }
            this.lastChar = (char) this.reader.read();
            return this.lastChar;
        }

        char lastChar() {
            return this.lastChar;
        }

        boolean charIn(char[] chars, char key) {
            return binarySearch(chars, key) >= 0;
        }

        boolean eot() {// end of text?
            return lastChar() == (char) -1; 
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

        void syntaxException() throws IOException {
            throw new IOException(); //???java.text.ParseException
        }

        String unescapeString(String s) {
            return s.replaceAll("\\\"", "\"");
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
                            syntaxException();
                        }
                    } while (expectedChar(','));
                    if (!expectedChar('}')) {
                        syntaxException();
                    }
                }
            } else if (expectedChar('[')) { // JSON array
                Vector<Object> list = new Vector<>(); //obsolete?
                if (!expectedChar(']')) { // empty array
                    do {
                        list.add(parseObject());
                    } while (expectedChar(','));
                    if (!expectedChar(']')) {
                        syntaxException();
                    }
                }
                object = list.toArray();
            } else if (charIn(NUMBERS, lastChar())) {
                object = (Number) Double.parseDouble(nextChars(NUMBERS));
            } else if (lastChar() == '"') { // String
                StringBuilder sb = new StringBuilder(1024); // ???CharBuffer
                while ((lastChar() == '\\' && getChar() == '"')
                        || getChar() != '"') {
                    sb.append(Character.toString(lastChar()));
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
                        syntaxException();
                }
            } else {
                syntaxException();
            }
            skipWhitespaces(); // trailing
            return object;
        }

        Object parse(Reader reader) throws IOException {
            this.reader = reader;
            this.lastChar = ' '; //!!!
            Object object = parseObject();
            if (!eot()) { // not end of text
                syntaxException();
            }
            return object;
        }
    }

// stringify
    static String escapeString(String s) {
        return s.replaceAll("\"", "\\\"");
    }

    static String stringifyObject(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof JSON) {
            HashMap<String, Object> hmap
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
        return value.toString().replace(".0", ""); // remove trailing .0 if any
    }

    private static Object checkObjectType(Object object) throws IllegalArgumentException {
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
/*
    public static void main(String[] args) throws Exception {
        JSON json = new JSON();
        json.set("Escaped", "Size 1.44\"");
        json.set("EmptyJSON", new JSON());
        json.set("EmptyArray", new Object[0]);
        json.set("Null", null);
        json.set("False", (Boolean) false);
        json.set("VeryLongNumber", (Number) 3.141592653589793238462643383279);
        System.out.println(json);

        String example1 = "{\n"
                + "        \"Image\": {\n"
                + "            \"Width\":  800,\n"
                + "            \"Height\": 600,\n"
                + "            \"Title\":  \"View from 15th Floor\",\n"
                + "            \"Thumbnail\": {\n"
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
