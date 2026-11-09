package com.sewlect.feed.controller;

import com.sewlect.common.exception.dto.ErrorResponse;
import com.sewlect.common.openapi.annotation.StandardApiErrors;
import com.sewlect.feed.dto.FeedItemResponse;
import com.sewlect.feed.dto.FeedPage;
import com.sewlect.feed.dto.FeedResponse;
import com.sewlect.feed.service.FeedGenerationService;
import com.sewlect.feed.properties.FeedProperties;
import com.sewlect.feed.support.FeedResponseAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/feed")
@AllArgsConstructor
@Tag(name = "Feed", description = "The caller's personalised, never-repeating outfit feed")
public class FeedController {

    private final FeedGenerationService feedGenerationService;
    private final FeedResponseAssembler feedResponseAssembler;
    private final FeedProperties feedProperties;

    @Operation(summary = "Get one page of the feed; pass the returned nextCursor to fetch the following page")
    @StandardApiErrors
    @GetMapping
    public ResponseEntity<FeedResponse> getFeed(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(required = false) String cursor) {
        UUID userId = UUID.fromString(jwt.getSubject());
        FeedPage page = feedGenerationService.getPage(userId, cursor, feedProperties.pageSize());

        List<FeedItemResponse> items = feedResponseAssembler.toResponses(page.entries(), userId);

        return ResponseEntity.ok(new FeedResponse(items, page.nextCursor()));
    }

    @Operation(summary = "Discard the caller's unseen feed entries, e.g. after a budget or style-tag change",
            description = "Nothing is regenerated here; the next GET of the feed regenerates synchronously, so it is slower than usual")
    @StandardApiErrors
    @ApiResponse(responseCode = "204", description = "The unseen feed was discarded")
    @ApiResponse(responseCode = "429",
            description = "The caller's hourly feed-reset budget is exhausted",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/reset")
    public ResponseEntity<Void> reset(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        feedGenerationService.reset(userId);
        return ResponseEntity.noContent().build();
    }
}
