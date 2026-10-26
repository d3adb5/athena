package com.limelight.preferences;

import android.net.Uri;

import okhttp3.HttpUrl;

/** Parses a manually entered host without mistaking the end of an IPv6 literal for a port. */
final class ManualHostAddress {
    private ManualHostAddress() {}

    static Uri parse(String input) {
        if (input == null) {
            return null;
        }
        input = input.trim();
        int queryStart = input.indexOf('?');
        String authority = queryStart < 0 ? input : input.substring(0, queryStart);
        String query = queryStart < 0 ? "" : input.substring(queryStart);
        if (authority.isEmpty() || authority.indexOf('/') >= 0 || authority.indexOf('@') >= 0 ||
                authority.indexOf('#') >= 0) {
            return null;
        }

        // An unbracketed string with multiple colons is always the entire IPv6
        // address. A custom port must be written as [address]:port.
        if (!authority.startsWith("[") && authority.indexOf(':') != authority.lastIndexOf(':')) {
            authority = "[" + authority + "]";
        }

        // Validate with the same URL parser used by NvHTTP. Android Uri alone
        // accepts malformed authorities and silently treats invalid ports as absent.
        HttpUrl url = HttpUrl.parse("http://" + authority);
        if (url == null || !url.username().isEmpty() || !url.password().isEmpty() ||
                url.port() == 0 || !url.encodedPath().equals("/") || url.fragment() != null) {
            return null;
        }
        return Uri.parse("art://" + authority + query);
    }
}
