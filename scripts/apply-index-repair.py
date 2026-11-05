"""Apply a reviewed local repair after Minecraft releases its files. Never stops a process.

The case directory contains repair-config.json, recovery-report.json and the verified repair artifacts.
Only missing root snapshots/physical positions are imported; current observations are never replaced.
"""
import argparse
import ctypes
from ctypes import wintypes
import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import sqlite3
import time


def digest(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def readonly(path):
    connection = sqlite3.connect(Path(path).resolve().as_uri() + '?mode=ro', uri=True)
    connection.row_factory = sqlite3.Row
    return connection


def status(case, state, **details):
    data = dict(state=state, updated_at=datetime.datetime.now(datetime.timezone.utc).isoformat(), **details)
    temporary = case / 'repair-status.tmp'
    temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
    os.replace(temporary, case / 'repair-status.json')
    labels = {'waiting': '等待游戏完全退出；尚未替换模组或修改索引。',
              'installing': '正在备份旧版并安装修复版。',
              'restoring': '修复版已安装，正在合并缺失记录。',
              'completed': '修复版已安装，缺失记录已合并。可以启动游戏。',
              'expired': '等待已超时，尚未应用。请重新运行修复程序。',
              'failed': '应用未完成，请查看下方错误；备份仍在此目录。'}
    (case / 'repair-status.txt').write_text(labels[state] + '\n\n' + json.dumps(details, ensure_ascii=False, indent=2), encoding='utf-8')
    with (case / 'apply-repair.log').open('a', encoding='utf-8') as log:
        log.write(json.dumps(data, ensure_ascii=False) + '\n')


def validate(config):
    for name, checksum in [('repair_jar', 'repair_sha256'), ('prepared_db', 'prepared_sha256')]:
        if digest(config[name]) != config[checksum]:
            raise RuntimeError('Prepared artifact changed: ' + name)
    old = Path(config['old_jar'])
    installed = Path(config['installed_jar'])
    jars = set(old.parent.glob('litemfinder-neoforge-*.jar'))
    if old.exists():
        if digest(old) != config['old_sha256'] or jars != {old}:
            raise RuntimeError('Installed Mods changed since preparation; refusing to replace them')
    elif not installed.exists() or digest(installed) != config['repair_sha256'] or jars != {installed}:
        raise RuntimeError('Expected installed Mod was not found')
    if not Path(config['live_db']).is_file():
        raise RuntimeError('Live database is missing; refusing to create a new index')
    with readonly(config['prepared_db']) as source:
        if source.execute('pragma integrity_check').fetchone()[0] != 'ok' or source.execute('pragma foreign_key_check').fetchall():
            raise RuntimeError('Prepared database validation failed')


def exclusive_available(path):
    if not Path(path).exists():
        return True
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.CreateFileW.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD, ctypes.c_void_p,
                                  wintypes.DWORD, wintypes.DWORD, wintypes.HANDLE]
    kernel.CreateFileW.restype = wintypes.HANDLE
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    handle = kernel.CreateFileW(str(Path(path).resolve()), 0x80000000, 0, None, 3, 0, None)
    if handle == ctypes.c_void_p(-1).value:
        return False
    kernel.CloseHandle(handle)
    return True


def install(config, case):
    old, installed, source = (Path(config[name]) for name in ('old_jar', 'installed_jar', 'repair_jar'))
    if installed.exists() and not old.exists() and digest(installed) == config['repair_sha256']:
        return
    backup = case / old.name
    if backup.exists() and digest(backup) != config['old_sha256']:
        raise RuntimeError('Old Mod backup has an unexpected checksum')
    pending = installed.with_suffix('.jar.pending')
    shutil.copy2(source, pending)
    if digest(pending) != config['repair_sha256']:
        raise RuntimeError('Staged Mod checksum mismatch')
    if not backup.exists():
        shutil.copy2(old, backup)
    # Both files stay on the same volume. Roll back the old filename if publishing the new file fails.
    retired = old.with_suffix('.jar.retired')
    os.replace(old, retired)
    try:
        os.replace(pending, installed)
    except BaseException:
        os.replace(retired, old)
        raise
    retired.unlink()
    if digest(installed) != config['repair_sha256']:
        raise RuntimeError('Installed Mod checksum mismatch')


def position(row):
    return tuple(row[name] for name in ('scope', 'dimension_namespace', 'dimension_path', 'x', 'y', 'z'))


def merge_database(config, case):
    report = json.loads(Path(config['recovery_report']).read_text(encoding='utf-8'))
    recovery_ids = {row['root_id'] for row in report['restored']}
    before_file = case / 'live-before-apply.db'
    if before_file.exists():
        before_file = case / ('live-before-apply-' + str(time.time_ns()) + '.db')
    with readonly(config['live_db']) as source:
        with sqlite3.connect(before_file) as backup:
            source.backup(backup)
    added = skipped = added_slots = 0
    with readonly(config['prepared_db']) as source, sqlite3.connect(config['live_db'], timeout=5) as target:
        target.row_factory = sqlite3.Row
        target.execute('pragma foreign_keys=ON')
        target.execute('begin immediate')
        target.execute('pragma defer_foreign_keys=ON')
        current_ids = {row[0] for row in target.execute('select root_container_id from snapshot_roots')}
        current_positions = {position(row) for row in target.execute("select * from containers where parent_container_id is null and location_kind='WORLD'")}
        for root in source.execute('select * from containers where parent_container_id is null order by root_container_id'):
            rid = root['root_container_id']
            if rid not in recovery_ids:
                continue
            if rid in current_ids or position(root) in current_positions:
                skipped += 1
                continue
            target.execute('insert into snapshot_roots values (?,?)', (rid, root['captured_at']))
            for table in ('containers', 'container_metadata', 'slots'):
                rows = source.execute('select * from ' + table + ' where root_container_id=?', (rid,)).fetchall()
                for row in rows:
                    target.execute('insert into ' + table + ' values (' + ','.join('?' * len(row)) + ')', tuple(row))
                if table == 'slots':
                    added_slots += len(rows)
            current_positions.add(position(root))
            added += 1
        if target.execute('pragma integrity_check').fetchone()[0] != 'ok' or target.execute('pragma foreign_key_check').fetchall():
            raise RuntimeError('Merged database validation failed; transaction will roll back')
        # Verify every pre-existing snapshot is unchanged, including its nested containers and metadata.
        with readonly(before_file) as before:
            before_ids = {row[0] for row in before.execute('select root_container_id from snapshot_roots')}
            for rid in before_ids:
                for table in ('snapshot_roots', 'containers', 'container_metadata', 'slots'):
                    query = 'select * from ' + table + ' where root_container_id=?'
                    previous = sorted(tuple(row) for row in before.execute(query, (rid,)))
                    current = sorted(tuple(row) for row in target.execute(query, (rid,)))
                    if previous != current:
                        raise RuntimeError('An existing observation changed; rolling back')
        target.commit()
        total = target.execute('select count(*) from snapshot_roots').fetchone()[0]
        with sqlite3.connect(case / 'live-after-apply.db') as backup:
            target.backup(backup)
    return dict(restored_containers=added, newer_or_existing_skipped=skipped,
                restored_slot_records=added_slots, total_root_records=total, before_backup=str(before_file))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('case', type=Path)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--check', action='store_true')
    mode.add_argument('--apply', action='store_true')
    mode.add_argument('--wait-for-pid', type=int)
    args = parser.parse_args()
    case = args.case.resolve()
    config = json.loads((case / 'repair-config.json').read_text(encoding='utf-8'))
    lock = None
    if not args.check:
        import msvcrt
        lock = (case / 'apply-repair.lock').open('a+b')
        if lock.tell() == 0:
            lock.write(b'0'); lock.flush()
        lock.seek(0)
        try:
            msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
        except OSError:
            lock.close()
            print('Another repair helper is already active for this case')
            return
    try:
        validate(config)
        if args.check:
            print('Prepared repair artifacts and current Mod checksums verified; no changes applied')
            return
        if args.wait_for_pid:
            kernel = ctypes.WinDLL('kernel32', use_last_error=True)
            kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
            kernel.OpenProcess.restype = wintypes.HANDLE
            kernel.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
            kernel.CloseHandle.argtypes = [wintypes.HANDLE]
            process = kernel.OpenProcess(0x00100000, False, args.wait_for_pid)
            deadline = time.monotonic() + 24 * 3600
            status(case, 'waiting', game_process=args.wait_for_pid, expires_after_hours=24)
            try:
                quiet = 0
                while time.monotonic() < deadline:
                    if (case / 'cancel-repair').exists():
                        raise RuntimeError('Repair cancelled via cancel-repair file')
                    exited = not process or kernel.WaitForSingleObject(process, 0) == 0
                    free = exited and all(exclusive_available(config[name]) for name in ('old_jar', 'installed_jar', 'live_db'))
                    quiet = quiet + 1 if free else 0
                    if quiet >= 2:
                        break
                    time.sleep(5)
                else:
                    status(case, 'expired')
                    return
            finally:
                if process:
                    kernel.CloseHandle(process)
        validate(config)
        if not all(exclusive_available(config[name]) for name in ('old_jar', 'installed_jar', 'live_db')):
            raise RuntimeError('Game files are still in use; nothing applied')
        status(case, 'installing')
        install(config, case)
        status(case, 'restoring', installed_version='0.2.3-beta.1-SNAPSHOT')
        result = merge_database(config, case)
        status(case, 'completed', installed_version='0.2.3-beta.1-SNAPSHOT', **result)
    except BaseException as error:
        status(case, 'failed', error=str(error))
        raise
    finally:
        if lock:
            lock.close()


if __name__ == '__main__':
    main()
