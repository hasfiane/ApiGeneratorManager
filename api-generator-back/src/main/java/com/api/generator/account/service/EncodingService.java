package com.api.generator.account.service;

import org.springframework.stereotype.Service;

@Service
public class EncodingService {

    public String encode(String raw) {
        if (raw == null) return null;
        return raw.replaceAll("([?&])password=[^&]*", "$1password=***")
                .replaceAll("([?&])pass=[^&]*", "$1pass=***")
                .replaceAll("([?&])pwd=[^&]*", "$1pwd=***");
    }

    public String decode(String encoded) {
        return encoded;
    }
}
