CREATE INDEX idx_outfit_items_outfit_id ON outfit_items (outfit_id);

CREATE INDEX idx_product_style_tags_style_tag_id ON product_style_tags (style_tag_id);

CREATE INDEX idx_user_outfit_interactions_outfit_id_interaction_type
    ON user_outfit_interactions (outfit_id, interaction_type);

CREATE INDEX idx_tryon_requests_user_id_outfit_id ON tryon_requests (user_id, outfit_id);

CREATE INDEX idx_tryon_request_items_tryon_request_id ON tryon_request_items (tryon_request_id);