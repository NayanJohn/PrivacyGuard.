from typing import Dict, List
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    PROJECT_NAME: str = "PrivacyGuard Backend"
    VERSION: str = "0.1.0"

    # Blocking threshold configuration (strictly greater than BLOCK_THRESHOLD)
    BLOCK_THRESHOLD: int = 90
    BLOCK_MIN_CONFIDENCE: int = 70

    # Risk weights and multipliers (from ARCHITECTURE.md and required.md)
    PERMISSION_WEIGHTS: Dict[str, int] = {
        "CAMERA": 15,
        "RECORD_AUDIO": 15,
        "FINE_LOCATION": 12,
        "READ_SMS": 10,
        "READ_CONTACTS": 10,
        "EXTERNAL_STORAGE": 5,
    }

    STATE_MULTIPLIERS: Dict[str, int] = {
        "foreground": 1,
        "background": 10,
    }

    # TTLs
    PARENT_TOKEN_TTL_MINUTES: int = 60
    RELEASE_CODE_TTL_MINUTES: int = 15

    # Server-side never-block package list
    NEVER_BLOCK_PACKAGES: List[str] = [
        "com.android.settings",
        "com.google.android.settings",
        "com.android.systemui",
        "com.privacyguard.app",
        "moe.shizuku.privileged.api",
    ]

    # Database
    DATABASE_URL: str = "sqlite:///./privacyguard.db"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


settings = Settings()
