from app.config import settings


def test_config_contract():
    assert settings.BLOCK_THRESHOLD == 90
    assert settings.BLOCK_MIN_CONFIDENCE == 70
    assert settings.PERMISSION_WEIGHTS["CAMERA"] == 15
    assert settings.PERMISSION_WEIGHTS["RECORD_AUDIO"] == 15
    assert settings.PERMISSION_WEIGHTS["FINE_LOCATION"] == 12
    assert settings.PERMISSION_WEIGHTS["READ_SMS"] == 10
    assert settings.PERMISSION_WEIGHTS["READ_CONTACTS"] == 10
    assert settings.PERMISSION_WEIGHTS["EXTERNAL_STORAGE"] == 5

    assert settings.STATE_MULTIPLIERS["foreground"] == 1
    assert settings.STATE_MULTIPLIERS["background"] == 10

    assert "com.privacyguard.app" in settings.NEVER_BLOCK_PACKAGES
    assert settings.PARENT_TOKEN_TTL_MINUTES > 0
    assert settings.RELEASE_CODE_TTL_MINUTES > 0
