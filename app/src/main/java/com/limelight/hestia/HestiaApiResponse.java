package com.limelight.hestia;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Raw result of one Hestia v1 request.
 *
 * Unlike the GameStream helpers in NvHTTP, an HTTP error is not turned into
 * an exception: Hermes explains every failure in a JSON body
 * ({@code {"ok":false,"error":{"code":...,"message":...}}}) and that
 * explanation is exactly what the user needs to see.
 */
public final class HestiaApiResponse {
    private final int httpCode;
    private final String body;

    public HestiaApiResponse(int httpCode, String body) {
        this.httpCode = httpCode;
        this.body = body != null ? body : "";
    }

    public int getHttpCode() { return httpCode; }
    public String getBody() { return body; }

    public boolean isHttpSuccess() {
        return httpCode >= 200 && httpCode < 300;
    }

    /** HTTP success and a JSON object body with {@code "ok": true}. */
    public boolean isOk() {
        JsonObject obj = jsonObject();
        return isHttpSuccess() && obj != null && HestiaCapabilities.isTrue(obj, "ok");
    }

    /** The body as a JSON object, or null if it is not one. */
    public JsonObject jsonObject() {
        try {
            JsonElement element = JsonParser.parseString(body);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (JsonParseException | IllegalStateException e) {
            return null;
        }
    }

    /** Machine-readable error code from the body, or null. */
    public String getErrorCode() {
        JsonObject error = errorObject();
        return error != null ? HestiaCapabilities.string(error, "code") : null;
    }

    /** Host-provided (English) error explanation, or null. */
    public String getErrorMessage() {
        JsonObject error = errorObject();
        return error != null ? HestiaCapabilities.string(error, "message") : null;
    }

    /** Short human-readable failure description for logs and dialogs. */
    public String describeError() {
        String message = getErrorMessage();
        String code = getErrorCode();
        if (message != null && !message.isEmpty()) {
            return code != null ? message + " (" + code + ", HTTP " + httpCode + ")"
                    : message + " (HTTP " + httpCode + ")";
        }
        if (code != null) {
            return code + " (HTTP " + httpCode + ")";
        }
        return "HTTP " + httpCode;
    }

    private JsonObject errorObject() {
        JsonObject obj = jsonObject();
        if (obj == null || !HestiaCapabilities.isObject(obj, "error")) {
            return null;
        }
        return obj.getAsJsonObject("error");
    }
}
