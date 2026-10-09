import uuid
from datetime import datetime, timezone
import enum
from sqlalchemy import (
    Column,
    String,
    Integer,
    BigInteger,
    Boolean,
    Numeric,
    DateTime,
    ForeignKey,
    UniqueConstraint,
    JSON,
    Enum as SAEnum,
)
from sqlalchemy.dialects.postgresql import UUID as PGUUID, JSONB
from sqlalchemy.orm import relationship
from app.database import Base


def utc_now():
    return datetime.now(timezone.utc)


class AgeCategory(str, enum.Enum):
    UNDER_18 = "under_18"
    ADULT = "adult"


class ScanStatus(str, enum.Enum):
    PRELIMINARY = "preliminary"
    COMPLETE = "complete"
    FAILED = "failed"


class BlockState(str, enum.Enum):
    NONE = "none"
    WARNED = "warned"
    BLOCKED = "blocked"
    ALLOWED_BY_PARENT = "allowed_by_parent"
    ALLOWED_BY_USER = "allowed_by_user"
    NOT_BLOCKABLE = "not_blockable"


class ActorType(str, enum.Enum):
    SYSTEM = "system"
    ADULT_USER = "adult_user"
    PARENT = "parent"
    DEVICE_SYNC = "device_sync"


class BlockAction(str, enum.Enum):
    BLOCK = "block"
    UNBLOCK = "unblock"
    ALLOW = "allow"
    MANUAL_BLOCK = "manual_block"
    NOT_BLOCKABLE = "not_blockable"
    RELEASE_CONTROL = "release_control"


class TokenKind(str, enum.Enum):
    PARENT_ALLOW = "parent_allow"
    RELEASE_CODE = "release_code"


class User(Base):
    __tablename__ = "users"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    email = Column(String, unique=True, nullable=False)
    age_category = Column(SAEnum(AgeCategory, name="age_category", values_callable=lambda x: [e.value for e in x]), nullable=False)
    created_at = Column(DateTime(timezone=True), nullable=False, default=utc_now)


class ParentLink(Base):
    __tablename__ = "parent_links"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    child_user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    parent_email = Column(String, nullable=False)
    verified_at = Column(DateTime(timezone=True), nullable=True)
    revoked_at = Column(DateTime(timezone=True), nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False, default=utc_now)

    __table_args__ = (
        UniqueConstraint("child_user_id", "parent_email", name="uq_parent_links_child_parent"),
    )


class Device(Base):
    __tablename__ = "devices"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    label = Column(String, nullable=True)
    sdk_int = Column(Integer, nullable=False)
    device_owner_active = Column(Boolean, nullable=False, default=False)
    token_hash = Column(String, nullable=False)
    last_seen_at = Column(DateTime(timezone=True), nullable=True)
    last_sync_at = Column(DateTime(timezone=True), nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False, default=utc_now)


class App(Base):
    __tablename__ = "apps"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    package_name = Column(String, nullable=False)
    label = Column(String, nullable=True)
    version_code = Column(BigInteger, nullable=True)
    version_name = Column(String, nullable=True)
    apk_sha256 = Column(String, nullable=True)
    first_seen_at = Column(DateTime(timezone=True), nullable=False, default=utc_now)

    __table_args__ = (
        UniqueConstraint("package_name", "version_code", "apk_sha256", name="uq_apps_package_version_hash"),
    )


class Scan(Base):
    __tablename__ = "scans"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    app_id = Column(String(36), ForeignKey("apps.id"), nullable=False)
    device_id = Column(String(36), ForeignKey("devices.id"), nullable=True)
    status = Column(SAEnum(ScanStatus, name="scan_status", values_callable=lambda x: [e.value for e in x]), nullable=False)
    ml_risk = Column(Numeric(5, 2), nullable=True)
    heuristic_risk = Column(Numeric(5, 2), nullable=True)
    static_risk = Column(Numeric(5, 2), nullable=True)
    runtime_risk = Column(Numeric(5, 2), nullable=True)
    final_score = Column(Numeric(5, 2), nullable=True)
    confidence = Column(Numeric(5, 2), nullable=True)
    verdict = Column(String, nullable=True)
    block_state = Column(SAEnum(BlockState, name="block_state", values_callable=lambda x: [e.value for e in x]), nullable=False, default=BlockState.NONE)
    block_reason = Column(JSON, nullable=True)
    blocked_at = Column(DateTime(timezone=True), nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False, default=utc_now)
    completed_at = Column(DateTime(timezone=True), nullable=True)


class ScanSignal(Base):
    __tablename__ = "scan_signals"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    scan_id = Column(String(36), ForeignKey("scans.id", ondelete="CASCADE"), nullable=False)
    component = Column(String, nullable=False)
    signal_key = Column(String, nullable=False)
    weight = Column(Numeric, nullable=True)
    contribution = Column(Numeric, nullable=True)
    explanation = Column(String, nullable=False)


class DeviceAppState(Base):
    __tablename__ = "device_app_state"

    device_id = Column(String(36), ForeignKey("devices.id", ondelete="CASCADE"), primary_key=True)
    package_name = Column(String, primary_key=True)
    block_state = Column(SAEnum(BlockState, name="block_state", values_callable=lambda x: [e.value for e in x]), nullable=False)
    scan_id = Column(String(36), ForeignKey("scans.id"), nullable=True)
    updated_at = Column(DateTime(timezone=True), nullable=False, default=utc_now)


class BlockEvent(Base):
    __tablename__ = "block_events"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    device_id = Column(String(36), ForeignKey("devices.id", ondelete="CASCADE"), nullable=False)
    package_name = Column(String, nullable=False)
    scan_id = Column(String(36), ForeignKey("scans.id"), nullable=True)
    action = Column(SAEnum(BlockAction, name="block_action", values_callable=lambda x: [e.value for e in x]), nullable=False)
    actor_type = Column(SAEnum(ActorType, name="actor_type", values_callable=lambda x: [e.value for e in x]), nullable=False)
    actor_id = Column(String(36), nullable=True)
    actor_label = Column(String, nullable=True)
    detail = Column(JSON, nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False, default=utc_now)


class ActionToken(Base):
    __tablename__ = "action_tokens"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    kind = Column(SAEnum(TokenKind, name="token_kind", values_callable=lambda x: [e.value for e in x]), nullable=False)
    token_hash = Column(String, unique=True, nullable=False)
    parent_link_id = Column(String(36), ForeignKey("parent_links.id", ondelete="CASCADE"), nullable=False)
    device_id = Column(String(36), ForeignKey("devices.id", ondelete="CASCADE"), nullable=False)
    package_name = Column(String, nullable=True)
    expires_at = Column(DateTime(timezone=True), nullable=False)
    used_at = Column(DateTime(timezone=True), nullable=True)
    attempts = Column(Integer, nullable=False, default=0)
    created_at = Column(DateTime(timezone=True), nullable=False, default=utc_now)


class RuntimeSummary(Base):
    __tablename__ = "runtime_summaries"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    device_id = Column(String(36), ForeignKey("devices.id", ondelete="CASCADE"), nullable=False)
    package_name = Column(String, nullable=False)
    permission = Column(String, nullable=False)
    state = Column(String, nullable=False)
    event_count = Column(Integer, nullable=False)
    total_duration_ms = Column(BigInteger, nullable=True)
    rejected_count = Column(Integer, nullable=False, default=0)
    window_start = Column(DateTime(timezone=True), nullable=False)
    window_end = Column(DateTime(timezone=True), nullable=False)

    __table_args__ = (
        UniqueConstraint(
            "device_id",
            "package_name",
            "permission",
            "state",
            "window_start",
            "window_end",
            name="uq_runtime_summaries_window",
        ),
    )


class Alert(Base):
    __tablename__ = "alerts"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    device_id = Column(String(36), ForeignKey("devices.id", ondelete="CASCADE"), nullable=True)
    type = Column(String, nullable=False)
    block_event_id = Column(String(36), ForeignKey("block_events.id"), nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False, default=utc_now)
    read_at = Column(DateTime(timezone=True), nullable=True)
