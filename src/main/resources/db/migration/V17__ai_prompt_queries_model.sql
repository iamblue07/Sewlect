ALTER TABLE ai_prompt_queries ADD COLUMN model text;

UPDATE ai_prompt_queries SET model = 'gpt-oss:20b-cloud' WHERE model IS NULL;

ALTER TABLE ai_prompt_queries ALTER COLUMN model SET NOT NULL;
