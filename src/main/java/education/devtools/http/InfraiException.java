package education.devtools.http;

import java.util.Map;

public final class InfraiException extends RuntimeException {
    private final String code;
    private final int status;
    private final Map<String, Object> details;

    public InfraiException(String code, int status, Map<String, Object> details) {
        super(code + ": " + details);
        this.code = code;
        this.status = status;
        this.details = Map.copyOf(details);
    }

    public String code() { return code; }
    public int status() { return status; }
    public Map<String, Object> details() { return details; }
}
