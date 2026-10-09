from fastapi import FastAPI, Depends, HTTPException, status
from sqlalchemy.orm import Session
from sqlalchemy import text
from app.config import settings
from app.database import get_db, engine, Base

app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    description="PrivacyGuard Backend API",
)


@app.get("/health", status_code=status.HTTP_200_OK)
def get_health(db: Session = Depends(get_db)):
    """Health check endpoint: verifies service status, version, and database reachability."""
    try:
        db.execute(text("SELECT 1"))
        db_status = "reachable"
    except Exception as e:
        db_status = f"unreachable: {str(e)}"
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={
                "status": "degraded",
                "version": settings.VERSION,
                "database": db_status,
            },
        )

    return {
        "status": "ok",
        "version": settings.VERSION,
        "database": db_status,
    }
