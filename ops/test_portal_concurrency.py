from concurrent.futures import ThreadPoolExecutor
from textwrap import dedent
from time import monotonic, sleep


def observe(sql, statement, description):
    deadline = monotonic() + 10
    while monotonic() < deadline:
        rows = sql(statement).stdout.strip()
        if rows:
            return rows
        sleep(0.05)
    waits = sql("SELECT * FROM performance_schema.data_lock_waits;").stdout.strip()
    locks = sql("SELECT THREAD_ID,OBJECT_NAME,INDEX_NAME,LOCK_MODE,LOCK_STATUS,LOCK_DATA "
                "FROM performance_schema.data_locks;").stdout.strip()
    raise AssertionError(f"MySQL did not report {description}.\nWaits:\n{waits}\nLocks:\n{locks}")


def verify_concurrent_delivery(sql, gate, send, first_address, second_address, event):
    gate_name = "portal_receipt_commit_gate"
    event_id = event["eventId"]
    sql(dedent(f"""
        UPDATE performance_schema.setup_instruments SET ENABLED='YES'
        WHERE NAME='wait/lock/metadata/sql/mdl';
        DELIMITER //
        CREATE TRIGGER budget.pause_summary_insert
        BEFORE INSERT ON budget.budget_claim_summary FOR EACH ROW
        BEGIN
            IF COALESCE(GET_LOCK('{gate_name}', 20), 0) <> 1 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Test commit gate timed out';
            END IF;
            DO RELEASE_LOCK('{gate_name}');
        END//
        DELIMITER ;
    """))
    columns = ("requestingConnection", "blockingConnection", "requestingTransaction", "blockingTransaction",
               "reportedRequestingThread", "reportedBlockingThread", "table", "index", "lockType",
               "waitingMode", "waitingStatus", "blockingMode", "blockingStatus", "lockData", "waitingStatement")
    with ThreadPoolExecutor(max_workers=2) as requests:
        gate.stdin.write(f"SELECT GET_LOCK('{gate_name}', 0);\n")
        gate.stdin.flush()
        try:
            observe(sql, f"SELECT IS_USED_LOCK('{gate_name}') WHERE IS_USED_LOCK('{gate_name}') IS NOT NULL;",
                    "the test gate owner")
            first = requests.submit(send, event, first_address, 25)
            first_connection = observe(sql, f"""
                SELECT t.PROCESSLIST_ID FROM performance_schema.metadata_locks m
                JOIN performance_schema.threads t ON t.THREAD_ID=m.OWNER_THREAD_ID
                WHERE m.OBJECT_TYPE='USER LEVEL LOCK' AND m.OBJECT_NAME='{gate_name}' AND m.LOCK_STATUS='PENDING';
            """, "the first request at the test gate")
            started = monotonic()
            second = requests.submit(send, event, second_address, 25)
            row = observe(sql, f"""
                SELECT req.trx_mysql_thread_id, blocker.trx_mysql_thread_id,
                       w.REQUESTING_ENGINE_TRANSACTION_ID, w.BLOCKING_ENGINE_TRANSACTION_ID,
                       w.REQUESTING_THREAD_ID, w.BLOCKING_THREAD_ID, r.OBJECT_NAME, r.INDEX_NAME,
                       r.LOCK_TYPE, r.LOCK_MODE, r.LOCK_STATUS, b.LOCK_MODE, b.LOCK_STATUS, r.LOCK_DATA,
                       SUBSTRING_INDEX(req.trx_query, ' VALUES', 1)
                FROM performance_schema.data_lock_waits w
                JOIN performance_schema.data_locks r
                  ON r.ENGINE=w.ENGINE AND r.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID
                JOIN performance_schema.data_locks b
                  ON b.ENGINE=w.ENGINE AND b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID
                JOIN information_schema.innodb_trx req ON req.trx_id=w.REQUESTING_ENGINE_TRANSACTION_ID
                JOIN information_schema.innodb_trx blocker ON blocker.trx_id=w.BLOCKING_ENGINE_TRANSACTION_ID
                WHERE r.OBJECT_SCHEMA='budget' AND r.OBJECT_NAME='budget_event_receipt'
                  AND blocker.trx_mysql_thread_id={int(first_connection)};
            """, "the duplicate receipt row lock wait")
            observed_ms = round((monotonic() - started) * 1000, 1)
            lock = dict(zip(columns, row.split("\t"), strict=True))
            if (lock["requestingConnection"] == lock["blockingConnection"] or lock["index"] != "PRIMARY"
                    or lock["lockType"] != "RECORD" or lock["waitingStatus"] != "WAITING"
                    or lock["blockingStatus"] != "GRANTED" or lock["lockData"].strip("'") != event_id
                    or lock["waitingStatement"] != "INSERT INTO budget_event_receipt(event_id,payload_hash)"):
                raise AssertionError(lock)
            before = sql("""
                SELECT (SELECT COUNT(*) FROM budget.budget_event_receipt),
                       (SELECT COUNT(*) FROM budget.budget_claim_summary);
            """).stdout.strip()
            if before != "0\t0" or first.done() or second.done():
                raise AssertionError("Both requests must remain pending with no committed rows")
        finally:
            gate.stdin.write(f"DO RELEASE_LOCK('{gate_name}');\n")
            gate.stdin.close()
        statuses = [first.result(), second.result()]
    conflicting_status = send(dict(event, amount=event["amount"] - 1), second_address)
    rows = sql("""
        SELECT COUNT(*) FROM budget.budget_event_receipt;
        SELECT COUNT(*), MIN(sequence_no), MIN(event_type), MIN(amount) FROM budget.budget_claim_summary;
    """).stdout.splitlines()
    if statuses != [204, 204] or conflicting_status != 409 or rows != ["1", "1\t1\tAPPROVAL\t200000"]:
        raise AssertionError((statuses, conflicting_status, rows))
    version, isolation = sql("SELECT VERSION(), @@session.transaction_isolation;").stdout.strip().split("\t")
    return {
        "mysqlVersion": version,
        "observerIsolation": isolation,
        "eventId": event_id,
        "firstRequestConnectionAtGate": int(first_connection),
        "rowLock": lock,
        "secondSubmissionToObservationMs": observed_ms,
        "visibleRowsWhileWaiting": {"receipts": 0, "summaries": 0},
        "identicalHttpStatuses": statuses,
        "conflictingHttpStatus": conflicting_status,
        "finalRows": {"receipts": 1, "summaries": 1, "sequence": 1, "type": "APPROVAL", "amount": 200000},
    }
