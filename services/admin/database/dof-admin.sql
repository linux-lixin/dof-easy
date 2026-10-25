-- Apply only to the dedicated isolated Dof database, after verifying its UUID.
-- Never initializes, truncates, or alters native qk/player tables.
CREATE TABLE IF NOT EXISTS dnf_service.QINGKONG_INSTANCE_BINDING (
  instance_id VARCHAR(64) CHARACTER SET ascii NOT NULL PRIMARY KEY,
  environment VARCHAR(16) CHARACTER SET ascii NOT NULL,
  database_uuid CHAR(36) CHARACTER SET ascii NOT NULL,
  adapter_version VARCHAR(64) CHARACTER SET ascii NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS dnf_service.QINGKONG_CHANGE_AUDIT (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  instance_id VARCHAR(64) CHARACTER SET ascii NOT NULL,
  request_id CHAR(36) CHARACTER SET ascii NOT NULL,
  actor BIGINT NOT NULL,
  adapter_version VARCHAR(64) CHARACTER SET ascii NOT NULL,
  operation_name VARCHAR(16) CHARACTER SET ascii NOT NULL,
  target_request CHAR(36) CHARACTER SET ascii NULL,
  preset VARCHAR(32) CHARACTER SET ascii NULL,
  before_revision CHAR(64) CHARACTER SET ascii NOT NULL,
  after_revision CHAR(64) CHARACTER SET ascii NOT NULL,
  before_json MEDIUMTEXT NOT NULL,
  after_json MEDIUMTEXT NOT NULL,
  state VARCHAR(16) CHARACTER SET ascii NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at TIMESTAMP NULL DEFAULT NULL,
  UNIQUE KEY request_identity (instance_id,request_id),
  KEY pending_operations (instance_id,state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS dnf_service.QINGKONG_TEXT_AUDIT LIKE dnf_service.QINGKONG_CHANGE_AUDIT;

-- Separate snapshot format for native billing/config settings; preserves older UI/text audit history.
CREATE TABLE IF NOT EXISTS dnf_service.QINGKONG_SETTINGS_AUDIT LIKE dnf_service.QINGKONG_CHANGE_AUDIT;

-- Manual encrypted-PVF bundle application, independent from native auto-updates.
-- Before/after fields are private; HTTP never returns these JSON snapshots.
CREATE TABLE IF NOT EXISTS dnf_service.QINGKONG_SCRIPT_RELEASE (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  instance_id VARCHAR(64) CHARACTER SET ascii NOT NULL,
  request_id CHAR(36) CHARACTER SET ascii NOT NULL,
  actor BIGINT NOT NULL,
  adapter_version VARCHAR(64) CHARACTER SET ascii NOT NULL,
  bundle_id CHAR(36) CHARACTER SET ascii NULL,
  task_id CHAR(36) CHARACTER SET ascii NULL,
  operation_name VARCHAR(16) CHARACTER SET ascii NOT NULL,
  target_request CHAR(36) CHARACTER SET ascii NULL,
  before_revision CHAR(64) CHARACTER SET ascii NOT NULL,
  after_revision CHAR(64) CHARACTER SET ascii NOT NULL,
  before_json MEDIUMTEXT NOT NULL,
  after_json MEDIUMTEXT NOT NULL,
  state VARCHAR(16) CHARACTER SET ascii NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at TIMESTAMP NULL DEFAULT NULL,
  UNIQUE KEY request_identity (instance_id,request_id),
  KEY pending_operations (instance_id,state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
