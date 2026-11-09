UPDATE stylist_messages
SET proposal = jsonb_build_object(
        'title', proposal ->> 'title',
        'summary', proposal ->> 'title',
        'prompt', proposal -> 'searches' -> 0 ->> 'prompt',
        'matchProfile', COALESCE((proposal -> 'searches' -> 0 ->> 'matchProfile')::boolean, false))
WHERE proposal ? 'searches'
  AND jsonb_array_length(proposal -> 'searches') > 0;

UPDATE stylist_messages
SET proposal = NULL
WHERE proposal ? 'searches';
