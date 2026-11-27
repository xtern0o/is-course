ALTER TABLE app_user
    ADD COLUMN specialty varchar(120) NOT NULL DEFAULT '',
    ADD COLUMN bio varchar(1200) NOT NULL DEFAULT '',
    ADD COLUMN share_intro boolean NOT NULL DEFAULT false,
    ADD COLUMN profile_version bigint NOT NULL DEFAULT 0;
