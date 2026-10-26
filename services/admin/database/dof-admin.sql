-- Apply only to the dedicated isolated Dof database, after verifying its UUID.
-- Never initializes, truncates, or alters native dof_native/player tables.
CREATE TABLE IF NOT EXISTS dnf_service.DOF_INSTANCE_BINDING (
  instance_id VARCHAR(64) CHARACTER SET ascii NOT NULL PRIMARY KEY,
  environment VARCHAR(16) CHARACTER SET ascii NOT NULL,
  database_uuid CHAR(36) CHARACTER SET ascii NOT NULL,
  adapter_version VARCHAR(64) CHARACTER SET ascii NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS dnf_service.DOF_CHANGE_AUDIT (
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

CREATE TABLE IF NOT EXISTS dnf_service.DOF_TEXT_AUDIT LIKE dnf_service.DOF_CHANGE_AUDIT;

-- Separate snapshot format for native billing/config settings; preserves older UI/text audit history.
CREATE TABLE IF NOT EXISTS dnf_service.DOF_SETTINGS_AUDIT LIKE dnf_service.DOF_CHANGE_AUDIT;

-- Shared durable protocol for typed management operations. No passwords belong here.
-- PREVIEWED is not a native write; PREPARED must be reconciled before retrying.
CREATE TABLE IF NOT EXISTS dnf_service.DOF_OPERATION_JOURNAL (
  instance_id VARCHAR(64) CHARACTER SET ascii NOT NULL,
  request_id CHAR(36) CHARACTER SET ascii NOT NULL,
  actor BIGINT NOT NULL,
  namespace VARCHAR(64) CHARACTER SET ascii NOT NULL,
  resource_key VARCHAR(128) CHARACTER SET ascii NOT NULL,
  target_key VARCHAR(128) CHARACTER SET ascii NOT NULL,
  operation_name VARCHAR(16) CHARACTER SET ascii NOT NULL,
  target_request CHAR(36) CHARACTER SET ascii NULL,
  request_hash CHAR(64) CHARACTER SET ascii NOT NULL,
  reason VARCHAR(255) NOT NULL,
  before_hash CHAR(64) CHARACTER SET ascii NOT NULL,
  after_hash CHAR(64) CHARACTER SET ascii NOT NULL,
  before_json MEDIUMTEXT NOT NULL,
  after_json MEDIUMTEXT NOT NULL,
  state VARCHAR(16) CHARACTER SET ascii NOT NULL,
  checked_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (instance_id,request_id),
  KEY unresolved_resource (instance_id,resource_key,state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Manual encrypted-PVF bundle application, independent from native auto-updates.
-- Before/after fields are private; HTTP never returns these JSON snapshots.
CREATE TABLE IF NOT EXISTS dnf_service.DOF_SCRIPT_RELEASE (
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
