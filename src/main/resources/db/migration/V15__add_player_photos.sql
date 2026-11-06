ALTER TABLE player
    ADD COLUMN photo_data MEDIUMBLOB NULL AFTER photo_url,
    ADD COLUMN photo_content_type VARCHAR(100) NULL AFTER photo_data,
    ADD COLUMN photo_filename VARCHAR(255) NULL AFTER photo_content_type;
