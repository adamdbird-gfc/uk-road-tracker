"""One-off public reference-data ingestion entry point.

Run only in a temporary administrative job. It reads public sources and writes
shared reference data to Postgres; no Timeline file, Journey, user identifier
or correction is accepted or persisted.
"""

import json
import logging
import sys

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    force=True,
)

from database import database_state, initialise_database, reference_catalogue_status


def main() -> int:
    initialise_database()
    if database_state["status"] != "ready":
        print(json.dumps({"status": database_state["status"], "detail": database_state["detail"]}))
        return 1
    print(json.dumps(reference_catalogue_status(), sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
