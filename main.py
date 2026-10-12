"""
Smart Home Device Management Platform
Built with FastAPI and SQLite
Suitable for College Practical / System Design Submission
"""

import sqlite3
from datetime import datetime
from typing import List, Optional
from enum import Enum
from contextlib import contextmanager

from fastapi import FastAPI, HTTPException, status, Query
from pydantic import BaseModel, Field

# ==============================================================================
# CONFIGURATION & DATABASE INITIALIZATION
# ==============================================================================

DATABASE_FILE = "smart_home.db"

@contextmanager
def get_db_connection():
    """Context manager for thread-safe SQLite connection handling."""
    conn = sqlite3.connect(DATABASE_FILE)
    conn.row_factory = sqlite3.Row  # Access columns by name
    conn.execute("PRAGMA foreign_keys = ON;")
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()

def init_db():
    """Creates tables for devices and device activity logs if they don't exist."""
    with get_db_connection() as conn:
        cursor = conn.cursor()
        
        # 1. Devices Table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS devices (
                device_id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                device_type TEXT NOT NULL,
                room TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'OFF',
                is_online INTEGER NOT NULL DEFAULT 1,
                registered_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );
        """)

        # 2. Device Activity Logs Table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS device_activity (
                activity_id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT NOT NULL,
                action TEXT NOT NULL,
                previous_status TEXT,
                new_status TEXT,
                performed_by TEXT NOT NULL DEFAULT 'User',
                timestamp TEXT NOT NULL,
                details TEXT,
                FOREIGN KEY (device_id) REFERENCES devices (device_id) ON DELETE CASCADE
            );
        """)

# Initialize database schema on module load
init_db()

# ==============================================================================
# FASTAPI APP & ENUMS / SCHEMAS (PYDANTIC)
# ==============================================================================

app = FastAPI(
    title="Smart Home Device Management Platform",
    description="RESTful API for registering, monitoring, controlling, and removing smart devices.",
    version="1.0.0"
)

class DeviceCommand(str, Enum):
    ON = "ON"
    OFF = "OFF"

class DeviceStatus(str, Enum):
    ON = "ON"
    OFF = "OFF"

# Request Schemas
class DeviceRegisterRequest(BaseModel):
    device_id: str = Field(..., example="dev-101", description="Unique device identifier")
    name: str = Field(..., example="Living Room Light", description="Friendly device name")
    device_type: str = Field(..., example="Light", description="Type of device (e.g., Light, Fan, AC, Thermostat)")
    room: str = Field(..., example="Living Room", description="Location or room where device is placed")
    initial_status: Optional[DeviceStatus] = Field(default=DeviceStatus.OFF, description="Initial power status")

class DeviceControlRequest(BaseModel):
    command: DeviceCommand = Field(..., example="ON", description="Command to execute: ON or OFF")
    performed_by: Optional[str] = Field(default="Mobile App User", example="Mobile App User", description="Initiator of command")

# Response Schemas
class DeviceResponse(BaseModel):
    device_id: str
    name: str
    device_type: str
    room: str
    status: str
    is_online: bool
    registered_at: str
    updated_at: str

class DeviceStatusResponse(BaseModel):
    device_id: str
    name: str
    status: str
    is_online: bool
    last_updated: str

class DeviceControlResponse(BaseModel):
    message: str
    device_id: str
    previous_status: str
    current_status: str
    timestamp: str

class ActivityLogResponse(BaseModel):
    activity_id: int
    device_id: str
    action: str
    previous_status: Optional[str]
    new_status: Optional[str]
    performed_by: str
    timestamp: str
    details: Optional[str]

class MessageResponse(BaseModel):
    message: str
    device_id: str

# ==============================================================================
# REST API ENDPOINTS
# ==============================================================================

@app.get("/", tags=["Health Check"])
def root():
    return {
        "status": "online",
        "system": "Smart Home Device Management Platform",
        "docs_url": "/docs"
    }

# ------------------------------------------------------------------------------
# 1. REGISTER DEVICE
# ------------------------------------------------------------------------------
@app.post(
    "/devices",
    response_model=DeviceResponse,
    status_code=status.HTTP_201_CREATED,
    tags=["Device Management"],
    summary="Register a new smart device"
)
def register_device(payload: DeviceRegisterRequest):
    """
    Registers a new smart device in the platform.
    - Validates device details.
    - Checks for duplicate device ID (returns 400 Bad Request if already exists).
    - Logs registration in activity table.
    """
    now = datetime.utcnow().isoformat()
    with get_db_connection() as conn:
        cursor = conn.cursor()

        # Check duplicate
        cursor.execute("SELECT device_id FROM devices WHERE device_id = ?", (payload.device_id,))
        if cursor.fetchone():
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Device with ID '{payload.device_id}' is already registered."
            )

        # Insert device
        cursor.execute("""
            INSERT INTO devices (device_id, name, device_type, room, status, is_online, registered_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 1, ?, ?)
        """, (
            payload.device_id,
            payload.name,
            payload.device_type,
            payload.room,
            payload.initial_status.value,
            now,
            now
        ))

        # Log activity
        cursor.execute("""
            INSERT INTO device_activity (device_id, action, previous_status, new_status, performed_by, timestamp, details)
            VALUES (?, 'REGISTER', NULL, ?, 'User', ?, ?)
        """, (
            payload.device_id,
            payload.initial_status.value,
            now,
            f"Device registered in {payload.room}"
        ))

        # Retrieve newly created device
        cursor.execute("SELECT * FROM devices WHERE device_id = ?", (payload.device_id,))
        row = cursor.fetchone()

        return DeviceResponse(
            device_id=row["device_id"],
            name=row["name"],
            device_type=row["device_type"],
            room=row["room"],
            status=row["status"],
            is_online=bool(row["is_online"]),
            registered_at=row["registered_at"],
            updated_at=row["updated_at"]
        )

# ------------------------------------------------------------------------------
# 2. GET DEVICE STATUS
# ------------------------------------------------------------------------------
@app.get(
    "/devices/{device_id}/status",
    response_model=DeviceStatusResponse,
    status_code=status.HTTP_200_OK,
    tags=["Device Monitoring"],
    summary="Get current device status"
)
def get_device_status(device_id: str):
    """
    Retrieves the current power status and connectivity of a smart device.
    - Returns 404 Not Found if device is not registered.
    """
    with get_db_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
        row = cursor.fetchone()

        if not row:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Device '{device_id}' not found."
            )

        return DeviceStatusResponse(
            device_id=row["device_id"],
            name=row["name"],
            status=row["status"],
            is_online=bool(row["is_online"]),
            last_updated=row["updated_at"]
        )

# ------------------------------------------------------------------------------
# 3. CONTROL DEVICE (TURN ON / OFF)
# ------------------------------------------------------------------------------
@app.post(
    "/devices/{device_id}/control",
    response_model=DeviceControlResponse,
    status_code=status.HTTP_200_OK,
    tags=["Device Control"],
    summary="Control device (Turn ON or OFF)"
)
def control_device(device_id: str, payload: DeviceControlRequest):
    """
    Sends a control command (ON/OFF) to the smart device.
    - Updates device status in database.
    - Adds an audit record into device_activity.
    - Returns 404 if device is not found.
    - Returns 400 if device is offline.
    """
    with get_db_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
        device = cursor.fetchone()

        if not device:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Device '{device_id}' not found."
            )

        if not bool(device["is_online"]):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Cannot control device '{device_id}' because it is currently OFFLINE."
            )

        previous_status = device["status"]
        new_status = payload.command.value
        now = datetime.utcnow().isoformat()

        # Update status
        cursor.execute("""
            UPDATE devices 
            SET status = ?, updated_at = ?
            WHERE device_id = ?
        """, (new_status, now, device_id))

        # Log activity
        cursor.execute("""
            INSERT INTO device_activity (device_id, action, previous_status, new_status, performed_by, timestamp, details)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, (
            device_id,
            f"COMMAND_{new_status}",
            previous_status,
            new_status,
            payload.performed_by,
            now,
            f"Device turned {new_status} via API"
        ))

        return DeviceControlResponse(
            message=f"Device {device_id} successfully turned {new_status}.",
            device_id=device_id,
            previous_status=previous_status,
            current_status=new_status,
            timestamp=now
        )

# ------------------------------------------------------------------------------
# 4. GET DEVICE ACTIVITY
# ------------------------------------------------------------------------------
@app.get(
    "/devices/{device_id}/activity",
    response_model=List[ActivityLogResponse],
    status_code=status.HTTP_200_OK,
    tags=["Device Monitoring"],
    summary="View activity and command history for a device"
)
def get_device_activity(
    device_id: str,
    limit: int = Query(default=20, ge=1, le=100, description="Max logs to return")
):
    """
    Retrieves the chronological activity log for a specific device.
    - Returns 404 if device does not exist.
    """
    with get_db_connection() as conn:
        cursor = conn.cursor()
        
        # Verify device exists
        cursor.execute("SELECT device_id FROM devices WHERE device_id = ?", (device_id,))
        if not cursor.fetchone():
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Device '{device_id}' not found."
            )

        cursor.execute("""
            SELECT * FROM device_activity 
            WHERE device_id = ? 
            ORDER BY activity_id DESC 
            LIMIT ?
        """, (device_id, limit))
        rows = cursor.fetchall()

        logs = [
            ActivityLogResponse(
                activity_id=row["activity_id"],
                device_id=row["device_id"],
                action=row["action"],
                previous_status=row["previous_status"],
                new_status=row["new_status"],
                performed_by=row["performed_by"],
                timestamp=row["timestamp"],
                details=row["details"]
            )
            for row in rows
        ]
        return logs

# ------------------------------------------------------------------------------
# 5. REMOVE DEVICE
# ------------------------------------------------------------------------------
@app.delete(
    "/devices/{device_id}",
    response_model=MessageResponse,
    status_code=status.HTTP_200_OK,
    tags=["Device Management"],
    summary="Remove a registered device"
)
def remove_device(device_id: str):
    """
    Removes a smart device and its associated activity logs.
    - Returns 404 if device does not exist.
    """
    with get_db_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT device_id FROM devices WHERE device_id = ?", (device_id,))
        if not cursor.fetchone():
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Device '{device_id}' not found."
            )

        # Deleting device will cascade delete activities due to FOREIGN KEY ON DELETE CASCADE
        cursor.execute("DELETE FROM devices WHERE device_id = ?", (device_id,))

        return MessageResponse(
            message=f"Device '{device_id}' and all associated logs were successfully removed.",
            device_id=device_id
        )

# ------------------------------------------------------------------------------
# HELPER ENDPOINT: LIST ALL DEVICES
# ------------------------------------------------------------------------------
@app.get(
    "/devices",
    response_model=List[DeviceResponse],
    status_code=status.HTTP_200_OK,
    tags=["Device Management"],
    summary="List all registered devices"
)
def list_devices():
    """Lists all registered smart devices."""
    with get_db_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM devices ORDER BY registered_at DESC")
        rows = cursor.fetchall()
        return [
            DeviceResponse(
                device_id=row["device_id"],
                name=row["name"],
                device_type=row["device_type"],
                room=row["room"],
                status=row["status"],
                is_online=bool(row["is_online"]),
                registered_at=row["registered_at"],
                updated_at=row["updated_at"]
            )
            for row in rows
        ]

if __name__ == "__main__":
    import uvicorn
    print("Starting Smart Home Device Management Platform on http://127.0.0.1:8000 ...")
    uvicorn.run("main:app", host="127.0.0.1", port=8000, reload=True)
