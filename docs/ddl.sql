CREATE TABLE IF NOT EXISTS `Apod`
(
    `apod_id`     INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `date`        INTEGER                           NOT NULL,
    `title`       TEXT                              NOT NULL COLLATE NOCASE,
    `description` TEXT                              NOT NULL,
    `copyright`   TEXT,
    `media_type`  INTEGER                           NOT NULL,
    `url`         TEXT                              NOT NULL,
    `hd_url`      TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS `index_Apod_date` ON `Apod` (`date`);

CREATE INDEX IF NOT EXISTS `index_Apod_title` ON `Apod` (`title`);

CREATE TABLE IF NOT EXISTS `Access`
(
    `access_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `apod_id`   INTEGER                           NOT NULL,
    `timestamp` INTEGER                           NOT NULL,
    FOREIGN KEY (`apod_id`) REFERENCES `Apod` (`apod_id`) ON UPDATE NO ACTION ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS `index_Access_apod_id` ON `Access` (`apod_id`);