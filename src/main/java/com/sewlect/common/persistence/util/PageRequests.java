package com.sewlect.common.persistence.util;

import com.sewlect.common.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;

public final class PageRequests {

    private PageRequests() {
    }

    public static PageRequest of(int page, int size) {
        int firstPage = Math.max(page, 0);
        if ((long) firstPage * size > Integer.MAX_VALUE) {
            throw new BadRequestException("Page " + page + " is out of range");
        }
        return PageRequest.of(firstPage, size);
    }
}
