package os.vortex.tv;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** XMLTV timestamp parser, compatible with Android 7.1 without java.time desugaring. */
public final class XmltvClock {
    private static final Pattern DATE = Pattern.compile("^(\\d{12})(\\d{2})?\\s*([+-]\\d{4})?$");
    private XmltvClock() {}

    /** Returns epoch milliseconds or -1 for malformed/unusable timestamps. */
    public static long parseMillis(String value) {
        if (value == null) return -1;
        Matcher match = DATE.matcher(value.trim());
        if (!match.matches()) return -1;
        String date = match.group(1) + (match.group(2) == null ? "00" : match.group(2));
        String offset = match.group(3);
        if (offset != null) {
            int hours = Integer.parseInt(offset.substring(1, 3));
            int minutes = Integer.parseInt(offset.substring(3, 5));
            if (hours > 23 || minutes > 59) return -1;
        }
        SimpleDateFormat parser = new SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT);
        parser.setLenient(false);
        // XMLTV feeds should supply a numeric offset. Assume UTC only if omitted.
        TimeZone zone = TimeZone.getTimeZone("UTC");
        if (offset != null) {
            String tz = "GMT" + offset.substring(0, 3) + ":" + offset.substring(3, 5);
            zone = TimeZone.getTimeZone(tz);
        }
        parser.setTimeZone(zone);
        ParsePosition position = new ParsePosition(0);
        java.util.Date parsed = parser.parse(date, position);
        return parsed == null || position.getIndex() != date.length() ? -1 : parsed.getTime();
    }
}
