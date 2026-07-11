-- Passkey (WebAuthn) credential storage for Spring Security's
-- JdbcPublicKeyCredentialUserEntityRepository / JdbcUserCredentialRepository.
-- Mirrors the framework's reference schema (user-entities-schema.sql,
-- user-credentials-schema.sql) with id columns sized so the primary keys fit
-- MySQL's utf8mb4 InnoDB index limit.

CREATE TABLE IF NOT EXISTS user_entities (
	id varchar(512) NOT NULL,
	`name` varchar(100) NOT NULL,
	display_name varchar(200) NULL,
	PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS user_credentials (
	credential_id varchar(512) NOT NULL,
	user_entity_user_id varchar(512) NOT NULL,
	public_key blob NOT NULL,
	signature_count bigint NULL,
	uv_initialized boolean NULL,
	backup_eligible boolean NOT NULL,
	authenticator_transports varchar(1000) NULL,
	public_key_credential_type varchar(100) NULL,
	backup_state boolean NOT NULL,
	attestation_object blob NULL,
	attestation_client_data_json blob NULL,
	created timestamp NULL,
	last_used timestamp NULL,
	label varchar(1000) NOT NULL,
	PRIMARY KEY (credential_id),
	INDEX idx_user_credentials_user_entity (user_entity_user_id)
);
