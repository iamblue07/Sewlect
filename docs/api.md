# API reference

The interactive reference is at `/swagger-ui.html` when the app is running, and the OpenAPI spec is at `/v3/api-docs`. This page summarises every endpoint with its request and response shapes.

## Basics

- **Base path:** `/api/v1`
- **Format:** JSON in and out (`Content-Type: application/json`). Other content types get `415`.
- **Authentication:** send `Authorization: Bearer <accessToken>`. Only register, login, refresh and `/public/**` work without a token. Endpoints under `/admin/**` need the `ADMIN` role.
- **Tokens:** access tokens last 15 minutes; refresh tokens last 7 days and can be used once. Each refresh returns a new pair.
- **Ids** are UUIDs. **Money** is a number in EUR. **Scores** are numbers between 0 and 1.
- **Timestamps** in response bodies are ISO-8601 local date-times in UTC, e.g. `2026-11-10T15:14:31.933373`.
- **Correlation id:** every response has an `X-Correlation-Id` header. Send your own in the request to have it reused.

### Errors

Every error has the same shape:

```json
{
  "timestamp": "2026-11-10T15:03:11.922409576Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "path": "/api/v1/users/me/profile",
  "correlationId": "93eedb2a-e497-422d-b0ad-72de64deb890",
  "fieldErrors": { "averageBudgetPerOutfit": "must be greater than or equal to 0" }
}
```

`fieldErrors` is only present for body-validation errors. For what each status code means and every rule that triggers it, see [rules-and-limits.md](rules-and-limits.md).

### Shared objects

**Outfit** (`OutfitResponse`), returned by every endpoint that returns outfits:

```json
{
  "outfitId": "a8ddfa81-6981-4e45-b172-35ebd17d126c",
  "compatibilityBreakdown": {
    "colorScore": 1.0,
    "layeringScore": 1.0,
    "structuredScore": 1.0,
    "embeddingScore": 0.7985,
    "finalScore": 0.8388
  },
  "totalPrice": 138.0,
  "items": [
    {
      "itemId": "102d7f6e-29aa-4a47-8c87-acb9b28c5cc2",
      "productId": "2843ca70-421d-4e19-ae27-9ebb83821426",
      "productDisplayName": "Mineral Women Turquoise Blue Top",
      "imageUrl": "http://assets.myntassets.com/v1/images/style/properties/....jpg",
      "basePrice": 25.0,
      "slot": "TOP",
      "removable": false
    }
  ],
  "engagement": { "likeCount": 1, "likedByViewer": true, "savedByViewer": false }
}
```

`slot` is one of `TOP`, `BOTTOM`, `FULL_BODY`, `FOOTWEAR`, `OUTERWEAR`, `ACCESSORY`. `removable` is true only for `OUTERWEAR` and `ACCESSORY` items.

**Alternative** (`AlternativeCandidateResponse`): a product that could take a slot, with the outfit's score if it did:

```json
{
  "productId": "7635ee07-a092-46ae-a273-35db5ef63d91",
  "productDisplayName": "Jealous 21 Women Navy Blue Top",
  "imageUrl": "http://...",
  "basePrice": 30.0,
  "projectedBreakdown": { "colorScore": 1.0, "layeringScore": 1.0, "structuredScore": 1.0, "embeddingScore": 0.80, "finalScore": 0.84 }
}
```

**Token pair** (`AuthResponse`):

```json
{ "accessToken": "eyJ...", "refreshToken": "q3V...", "tokenType": "Bearer", "expiresIn": 900 }
```

`expiresIn` is the access token's lifetime in seconds.

---

## Authentication

### `POST /auth/register`
Public. Creates an account and returns the first token pair.

Request: `{ "email": "jane@example.com", "password": "at-least-8-chars" }`

Response `201`: token pair. `409` if the email is already registered.

### `POST /auth/login`
Public. Request: `{ "email": "jane@example.com", "password": "..." }`

Response `200`: token pair. `401` for a wrong email or password.

### `POST /auth/refresh`
Public. Request: `{ "refreshToken": "q3V..." }`

Response `200`: a new token pair; the old refresh token stops working. `401` if the token is unknown, expired or already used. Reusing an already-used token signs the user out everywhere.

### `POST /auth/logout`
Needs a Bearer token. Request: `{ "refreshToken": "q3V..." }`

Response `204`. Revokes that refresh token.

## Account and profile

### `POST /users/me/password`
Request: `{ "currentPassword": "...", "newPassword": "..." }`

Response `200`: a fresh token pair for this device. Every other session's refresh token stops working. A wrong current password is `400`, not `401`.

### `GET /users/me/profile`
Response `200`:

```json
{
  "birthDate": "1995-05-05",
  "sex": "FEMALE",
  "heightCm": 168,
  "weightKg": null,
  "footLengthCm": null,
  "averageBudgetPerOutfit": 150.0,
  "currency": "EUR",
  "styleTags": [ { "id": "f8958d26-379e-474e-897b-62eac3ba20bc", "name": "minimalist" } ]
}
```

### `PUT /users/me/profile`
Partial update: fields you leave out stay as they are. Body: any of `birthDate`, `sex` (`MALE`, `FEMALE`, `OTHER`), `heightCm`, `weightKg`, `footLengthCm`, `averageBudgetPerOutfit`, `currency`.

Response `200`: the updated profile, same shape as `GET`.

### `PUT /users/me/style-preferences`
Replaces the whole set. Request: `{ "styleTagIds": ["f8958d26-...", "8971a2ea-..."] }`

Response `200`: the resulting list of `{ "id", "name" }`. `400` if an id is unknown.

### `GET /style-tags`
Response `200`: every style tag, `[{ "id": "...", "name": "minimalist" }, ...]`.

## Body photos

The photo bytes go straight to object storage, not through the API.

### `POST /users/me/photos/upload-url`
Request: `{ "photoType": "FRONT" }` (`FRONT` or `BACK`)

Response `200`: `{ "uploadUrl": "https://...", "expiresAt": "2026-11-10T16:29:43" }`

Upload the JPEG with `PUT <uploadUrl>` and `Content-Type: image/jpeg` within 15 minutes.

### `POST /users/me/photos/confirm`
Request: `{ "photoType": "FRONT" }`

Response `200`: `{ "photoType": "FRONT", "downloadUrl": "https://..." }`. `400` if nothing was uploaded.

### `GET /users/me/photos`
Response `200`: `[{ "photoType": "FRONT", "downloadUrl": "https://..." }]`. Download URLs are valid for 15 minutes.

## Feed

### `GET /feed?cursor=<cursor>`
Returns 20 outfits per page. Omit `cursor` for the first page, then pass the previous response's `nextCursor`. When the user has nothing queued, the first page is generated on the spot, which can take around 30 seconds.

Response `200`:

```json
{
  "items": [
    {
      "outfitId": "...",
      "compatibilityBreakdown": { "colorScore": 1.0, "layeringScore": 1.0, "structuredScore": 1.0, "embeddingScore": 0.79, "finalScore": 0.83 },
      "rankScore": 0.8891,
      "totalPrice": 138.0,
      "items": [ { "itemId": "...", "productId": "...", "productDisplayName": "...", "imageUrl": "...", "basePrice": 25.0, "slot": "TOP", "removable": false } ],
      "engagement": { "likeCount": 0, "likedByViewer": false, "savedByViewer": false }
    }
  ],
  "nextCursor": "MC43NTY4OmFjYmRhN2RlLWI5NTctNDYyNy1hNDE5LWY3MWVlNzAyNmNlMg"
}
```

`nextCursor` is `null` on the last page. An outfit is never shown to the same user twice.

### `POST /feed/reset`
Discards the outfits the user hasn't seen yet, for example after changing budget or style; the next `GET /feed` builds a new batch. Response `204`.

## Outfits

### `GET /outfits/{outfitId}`
Response `200`: an outfit.

### `GET /public/outfits/{outfitId}`
Public, for shared links. Response `200`:

```json
{
  "outfitId": "...",
  "totalPrice": 138.0,
  "likeCount": 1,
  "items": [ { "productId": "...", "productDisplayName": "...", "imageUrl": "...", "basePrice": 25.0, "slot": "TOP" } ]
}
```

## Prompt search

### `POST /outfits/prompt`
Request: `{ "prompt": "black jeans, white sneakers and a denim jacket for the weekend", "matchProfile": true }`

`matchProfile` defaults to `true`, which uses the user's own sex and budget. With `false`, the AI reads who the outfit is for and the budget from the prompt itself ("a dress for my sister, around 80 euros").

Response `200`: up to 50 outfits, best first. Takes about 10–20 seconds. `502` if the AI or the search fails.

### `POST /outfits/{outfitId}/items/{itemId}/refine`
Finds replacements for one item from a description. Request: `{ "prompt": "something in dark green" }`

Response `200`: up to 20 alternatives, best first. Nothing changes until you swap one in.

### `POST /outfits/{outfitId}/slots/{role}/refine`
The same as the previous endpoint, for an empty `OUTERWEAR` or `ACCESSORY` slot. Commit a result with the matching `/add` endpoint.

## Outfit editing

Edits never change an outfit; each one returns a new (or existing identical) outfit with its own `outfitId`.

### `GET /outfits/{outfitId}/items/{itemId}/alternatives`
Response `200`: up to 20 alternatives of the same article type, ranked by how well they fit the rest of the outfit.

### `POST /outfits/{outfitId}/items/{itemId}/swap`
Request: `{ "productId": "..." }`. The product must have the same garment role as the item.

Response `200`: the new outfit.

### `POST /outfits/{outfitId}/items/{itemId}/remove`
No body. Only `OUTERWEAR` and `ACCESSORY` items can be removed. Response `200`: the new outfit.

### `GET /outfits/{outfitId}/slots/{role}/alternatives`
`role` is `OUTERWEAR` or `ACCESSORY` (uppercase), and the slot must be empty. Response `200`: up to 20 alternatives.

### `POST /outfits/{outfitId}/slots/{role}/add`
Request: `{ "productId": "..." }`. Response `200`: the new outfit. `409` if the slot is already filled.

## Likes and saves

### `POST` and `DELETE /outfits/{outfitId}/like`; `POST` and `DELETE /outfits/{outfitId}/save`
No body. Calling them twice does no harm. Response `200`:

```json
{ "active": true, "engagement": { "likeCount": 2, "likedByViewer": true, "savedByViewer": false } }
```

### `GET /outfits/saved?page=0&size=20`
Response `200`:

```json
{ "items": [ /* outfits */ ], "page": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
```

Most recently saved first. `size` is capped at 100.

### `GET /outfits/interactions/mine?type=LIKE`
`type` is `LIKE` or `SAVE`. Response `200`: every outfit id the user has liked or saved, as a JSON array of UUIDs, so an app can show heart and bookmark state.

## Stylist chat

The stylist asks questions until it knows what you want, then shows a summary. Confirming the summary runs an outfit search in the background and closes the chat.

**Message** (`MessageResponse`):

```json
{
  "messageId": "...",
  "sequence": 1,
  "role": "ASSISTANT",
  "content": "Who is the outfit for, and what's your budget?",
  "proposal": null,
  "createdAt": "2026-11-10T13:28:12.037557"
}
```

On a summary, `proposal` is `{ "title": "Elegant baptism outfit", "summary": "An elegant men's outfit in light colours..." }`.

**Turn** (`TurnResponse`), returned by the three chat endpoints below:

```json
{
  "conversationId": "...",
  "status": "OPEN",
  "userTurnsRemaining": 14,
  "userMessage": { /* message */ },
  "assistantMessage": { /* message */ }
}
```

`userMessage` is `null` when you ask for the summary without sending a message.

### `POST /stylist/conversations`
Request: `{ "message": "I need an outfit for a summer wedding" }`. Response `201`: a turn.

### `POST /stylist/conversations/{conversationId}/messages`
Request: `{ "content": "For me, budget around 150 euros" }`. Response `200`: a turn.

### `POST /stylist/conversations/{conversationId}/proposal`
No body. Asks the stylist for the summary now. Response `200`: a turn.

### `POST /stylist/conversations/{conversationId}/confirm`
Request: `{ "messageId": "<id of the latest summary message>" }`

Response `202`:

```json
{ "conversationId": "...", "title": "Romantic wedding outfit", "status": "EXECUTING", "createdAt": "...", "updatedAt": "..." }
```

Poll the results endpoint until `status` is `COMPLETE` or `FAILED`.

### `GET /stylist/conversations/{conversationId}/results`
Response `200`:

```json
{
  "conversationId": "...",
  "status": "COMPLETE",
  "searches": [
    {
      "searchId": "...",
      "position": 0,
      "title": "Romantic wedding outfit",
      "matchProfile": true,
      "status": "SUCCEEDED",
      "errorMessage": null,
      "queryId": "...",
      "outfits": [ /* outfits */ ]
    }
  ]
}
```

### `GET /stylist/conversations?page=0&size=20`
Response `200`: `{ "items": [ { "conversationId", "title", "status", "createdAt", "updatedAt" } ], "page", "size", "totalElements", "totalPages" }`, most recently active first.

### `GET /stylist/conversations/{conversationId}`
Response `200`:

```json
{
  "conversationId": "...",
  "title": "...",
  "status": "OPEN",
  "userTurnsRemaining": 13,
  "messages": [ /* messages, oldest first */ ],
  "createdAt": "...",
  "updatedAt": "...",
  "completedAt": null
}
```

### `DELETE /stylist/conversations/{conversationId}`
Response `204`. `409` while its search is running.

## Virtual try-on

### `POST /tryon`
Request: `{ "outfitId": "..." }`. The user needs a front body photo; without one the job ends `FAILED`.

Response `202`:

```json
{ "id": "...", "status": "PENDING", "resultImageUrl": null, "errorMessage": null }
```

If the same outfit is already rendering, or was rendered after your current front photo was uploaded, you get that request back instead of a new render.

### `GET /tryon/{requestId}`
Response `200`: the same shape. `status` goes `PENDING` → `PROCESSING` → `COMPLETE` or `FAILED`. On `COMPLETE`, `resultImageUrl` is a download link valid for 15 minutes; on `FAILED`, `errorMessage` says why.

## Admin

### `POST /admin/catalog/enrich-next`
`ADMIN` only, and only when local Ollama is enabled. Enriches the next product that hasn't been enriched yet.

Response `200`: `{ "enriched": true, "productId": "...", "productDisplayName": "..." }`; `enriched` is `false` when none are left.

### `GET /actuator/health`
Public. Response `200`: `{ "status": "UP" }`. All other `/actuator` endpoints are `ADMIN` only.
