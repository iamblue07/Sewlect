package com.sewlect.stylist.support;

import com.sewlect.common.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.text.Normalizer;

@Component
public class StylistInputSanitizer {

    public String sanitize(String raw) {
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        StringBuilder cleaned = new StringBuilder(normalized.length());
        normalized.codePoints().filter(this::isAllowed).forEach(cleaned::appendCodePoint);
        String sanitized = cleaned.toString().strip();
        if (sanitized.isEmpty()) {
            throw new BadRequestException("The message is empty once invisible characters are removed");
        }
        return sanitized;
    }

    private boolean isAllowed(int codePoint) {
        if (codePoint == '\n' || codePoint == '\t') {
            return true;
        }
        int type = Character.getType(codePoint);
        return type != Character.CONTROL
                && type != Character.FORMAT
                && type != Character.PRIVATE_USE
                && type != Character.SURROGATE
                && type != Character.UNASSIGNED;
    }
}
