# Rules and limits

Every check the API makes, the limits it enforces, and the status code you get when a check fails. Numbers are the defaults from [`application.properties`](../src/main/resources/application.properties). Request and response shapes are in [api.md](api.md).

## Status codes

| Code | Meaning |
|---|---|
| `400` | The request is invalid: a field failed validation (`fieldErrors` lists which), a value is malformed (bad UUID, unknown enum, invalid cursor), or a business rule below was broken |
| `401` | No valid access token, or wrong email, password or refresh token |
| `403` | Signed in, but the endpoint needs the `ADMIN` role |
| `404` | The resource doesn't exist, or belongs to another user |
| `405` | The endpoint doesn't support that HTTP method |
| `409` | The request conflicts with the current state, e.g. a filled slot, a closed chat or an already-registered email |
| `415` | The body isn't JSON |
| `422` | The stylist's safety check blocked the message |
| `429` | A rate limit or daily limit was reached, or the stylist is paused for the account |
| `502` | An external AI or storage service failed or returned something unusable |
| `503` | The stylist's safety check or search queue is unavailable; try again shortly |

## Accounts

- **Email:** a valid address, stored lowercase, so `Jane@Example.com` and `jane@example.com` are the same account.
- **Password:** at least 8 characters and at most 72 bytes (BCrypt's limit; accented letters and symbols can take 2–4 bytes each).
- **Changing the password** needs the current one, and the new one must be different. It signs out every other device. Access tokens already issued elsewhere keep working until they expire, at most 15 minutes.
- **Refresh tokens** can each be used once. Presenting a used token again is treated as theft: every session of that user is revoked. Two refreshes of the same token at the same moment count as reuse too, so a client should never refresh in parallel.
- **Access tokens** are HS256 JWTs valid for 15 minutes, with issuer `https://sewlect.local` and audience `sewlect-api`.

## Profile

| Field | Rule |
|---|---|
| `birthDate` | In the past |
| `sex` | `MALE`, `FEMALE` or `OTHER`. Can be set once; sending a different value later is `400`, sending the same value is ignored |
| `heightCm` | Above 0, at most 300 |
| `weightKg` | Above 0, at most 500 |
| `footLengthCm` | Above 0, at most 50 |
| `averageBudgetPerOutfit` | 0 or more, at most 8 digits before the decimal point and 2 after. Empty means no budget limit |
| `currency` | Three uppercase letters, e.g. `EUR` |
| Style preferences | Every id must be an existing style tag. The list replaces the old one |

Fields left out of `PUT /users/me/profile` are not changed.

## Body photos

- Two photos per user: `FRONT` and `BACK`. Uploading again replaces the photo.
- An upload link is valid for 15 minutes and only accepts `Content-Type: image/jpeg`.
- Confirming an upload checks that the file is really in storage; if not, `400`.
- Replacing the front photo makes old try-on results stale, so the next try-on renders again.

## Who an outfit is for, and budget

- **Gender filter from the profile:** `MALE` sees Men and Unisex products, `FEMALE` sees Women and Unisex, `OTHER` sees everything.
- **Profile with no sex set:** the feed shows every gender. A prompt search for yourself reads who the outfit is for from the prompt instead.
- **Budget:** each product may cost at most the user's budget plus 10%. With no budget there is no limit.

## Outfits

- **Shape:** an outfit has either a top and a bottom, or one full-body piece; always footwear; and optionally outerwear and an accessory. Each role appears at most once.
- **Outfits never change.** Swapping, removing or adding returns a different outfit; if the same combination already exists, that outfit is returned.
- **Variety:** within one batch (a feed refill or a prompt search), a product appears in at most 3 outfits.

### Editing

| Action | Allowed when | Otherwise |
|---|---|---|
| Swap an item | The new product has the same garment role, is not already in that slot, is eligible for the outfit's gender (the item's gender plus Unisex; a Unisex item allows any), has been enriched, and fits the remaining budget | `400`; `404` for an unknown product or an item not in the outfit |
| Remove an item | The item is `OUTERWEAR` or `ACCESSORY` | `400` |
| Add to a slot | The role is `OUTERWEAR` or `ACCESSORY` | `400` |
| | The slot is empty | `409`; swap instead |

- **Remaining budget for a slot:** the user's budget plus 10%, minus what the rest of the outfit costs, never below 0.
- **Alternatives:** at most 20, ranked by how well they fit the rest of the outfit. For an occupied slot they share the current item's article type; for an empty slot they can be any product of that role.

## Feed

- Pages hold 20 outfits; an outfit is never shown to the same user twice.
- When fewer than 30 unseen outfits remain, 50 more are generated in the background. If none are left, the request generates them before answering.
- **Reset** discards only outfits not yet shown.
- A cursor that wasn't returned by the API is `400`.

## Prompt search

- **Prompt length:** 1 to 1,000 characters; the same applies to refine prompts.
- **What a search does:** the AI proposes up to 2 outfit shapes; each garment is matched to the 3 closest products; up to 200 combinations are scored and the best 50 are returned.
- **Failures:** if the AI's answer is unusable, or no combination can be built, the request fails with `502`. Every search is logged, failed ones included.
- **Refinement** returns at most 20 candidates and changes nothing until a swap or add is made.

## Pagination

For `/outfits/saved` and `/stylist/conversations`:

- `page` below 0 is treated as 0.
- `size` below 1 becomes 20, and above 100 becomes 100.
- A page so large it can't be computed is `400`.

## Stylist chat

### Flow

`OPEN` → (confirm) → `EXECUTING` → `COMPLETE` if the search succeeded, otherwise `FAILED`.

- While `OPEN`, you can send messages and ask for the summary.
- After confirming, the chat is closed for good; start a new chat to continue.
- A search that hasn't finished after 10 minutes is stopped. A chat stuck in `EXECUTING` for 15 minutes, e.g. after a server restart, is marked `FAILED`.

### Checks on each message, in order

1. If the account is paused: `429`.
2. Hourly and daily message limits, plus the daily chat limit when starting a chat: `429`.
3. Invisible and control characters are removed. An empty result is `400`. The message must then be at most 1,000 characters (`400`).
4. If the chat isn't yours: `404`. If it's closed: `409`.
5. At most 15 messages from you per chat: `409`. Asking for the summary still works after that.
6. A content-safety model checks the message. Unsafe: `422`, nothing is saved, and it counts as a strike. If the safety check itself is down: `503`.

### Checks on the stylist's reply

- At most 1,500 characters. A summary's title is at most 60 characters, its summary text at most 800, and its hidden search prompt at most 400.
- It must not reveal the stylist's instructions.
- It must pass the same safety check.
- A reply that fails any of these is retried once. If it still fails, you get a standard refusal message. If the model returns nothing usable, `502` (nothing is saved, so you can resend).

### Confirming

- The confirmed message must be the latest one in the chat, written by the stylist, and contain a summary. Otherwise `409`.
- The chat must be `OPEN` (`409`). If two confirms arrive together, only one succeeds.
- Confirming uses 1 unit of the hourly prompt-search budget. If it's used up, `429` and the chat stays open.
- If the search queue is full: `503`, and the chat stays open.

### Deleting

Allowed in any state except `EXECUTING` (`409`). It removes the messages and searches too.

### Strikes

Each blocked message, and each request the stylist declines as off-topic, is a strike. 3 strikes within 24 hours pause the stylist for that account for 24 hours. Only the user id and the safety categories are logged, never the message text.

## Virtual try-on

- The outfit must exist (`404`) and contain at least one item (`400`).
- **No duplicate renders:** if the same outfit is already rendering for you, that request is returned. If it was rendered after your current front photo was uploaded, the finished result is returned. Neither counts against the rate limit.
- **Order:** garments are rendered one after another: full body, bottom, top, outerwear, footwear, accessory. Tops, bottoms, full-body pieces and outerwear use FASHN `tryon-v1.6`; footwear and accessories use `tryon-max`.
- **Retries and timeouts:** each garment is retried once on a temporary error. A single step stops after 3 minutes, and the whole job after 15 minutes.
- **Stuck jobs:** a job with no progress for 20 minutes is marked `FAILED`.
- **Ownership:** you can only see your own try-ons; another user's is `404`.

## Rate limits

| What | Limit | Counted per |
|---|---|---|
| Register, login, refresh | 20 per 15 minutes | IP address |
| Login | 10 per 15 minutes | Email address |
| Public outfit view | 60 per minute | IP address |
| Password change | 5 per 15 minutes | User |
| Prompt search and stylist confirm (shared) | 200 per hour | User (`ADMIN`: unlimited) |
| Refine | 200 per hour | User (`ADMIN`: unlimited) |
| Feed reset | 20 per hour | User |
| Stylist messages | 60 per hour and 200 per day | User |
| New stylist chats | 10 per day | User |
| New try-on renders | 20 per hour | User |

- Limits use fixed time windows. A refused request never counts against the limit.
- IP addresses and emails are hashed before they're used as rate-limit keys.
- If Redis is unreachable, rate-limited requests are refused with `429` rather than allowed through.

## Security

- Every endpoint needs a valid access token, except register, login, refresh, `/public/**`, `/actuator/health` and the API docs.
- `/admin/**` and every other `/actuator` endpoint need the `ADMIN` role.
- Users can only reach their own profile, photos, chats and try-ons. Outfits are shared and visible to every signed-in user.
- **Cross-origin browser requests** are blocked unless the origin is listed in `CORS_ALLOWED_ORIGINS`; none is by default.
- **Response headers:** `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store`, and HSTS over HTTPS.
