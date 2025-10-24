import json
import sqlite3
import os
import sys

# Define file paths
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

# Now build paths relative to the script file
json_path = os.path.join(SCRIPT_DIR, "uad_lists.json")
db_path = os.path.join(SCRIPT_DIR, "app/src/main/assets/databases/apps.db")

print(f"JSON file: {json_path}")
print(f"DB file: {db_path}")

# Ensure parent directory exists
os.makedirs(os.path.dirname(db_path), exist_ok=True)

# Delete old DB if it exists
if os.path.exists(db_path):
    os.remove(db_path)

try:
    # Connect to SQLite
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    # Create table
    cursor.execute("""
        CREATE TABLE apps (
            id TEXT NOT NULL PRIMARY KEY,
            description TEXT
        )
    """)


    # Read JSON
    with open(json_path, "r", encoding="utf-8") as f:
        apps = json.load(f)


    cursor.executemany(
        "INSERT OR IGNORE INTO apps (id, description) VALUES (?, ?)",
        [(app["id"], app["description"]) for app in apps]
    )


    conn.commit()
    print(f"✅ Successfully inserted {len(apps)} apps into database")

except Exception as e:
    print(f"❌ Failed to generate prebuilt database: {e}")
    sys.exit(1)

finally:
    conn.close()
