#!/usr/bin/env python3
"""Local, single-session Codex transcript + macOS filesystem audit collector."""
import argparse
import ctypes as C
import datetime as dt
import difflib
import fcntl
import hashlib
import json
import os
from pathlib import Path
import queue
import signal
import sqlite3
import stat
import threading
import time


def now():
    return dt.datetime.now(dt.timezone.utc).isoformat()


def dump(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def atomic_json(path, value):
    temp = path.with_suffix(path.suffix + ".tmp")
    temp.write_text(dump(value) + "\n")
    temp.replace(path)


class FileEvents:
    """FSEvents runs on its own CFRunLoop; callbacks only enqueue events."""
    def __init__(self, roots, since, pending, excluded):
        self.roots, self.since, self.pending = roots, since, pending
        self.excluded = excluded
        self.ready = threading.Event()
        self.error = None
        self.thread = threading.Thread(target=self.run, daemon=True)

    def start(self):
        self.thread.start()
        if not self.ready.wait(10) or self.error:
            raise RuntimeError(self.error or "FSEvents startup timeout")

    def run(self):
        try:
            cf = C.CDLL('/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation')
            fs = C.CDLL('/System/Library/Frameworks/CoreServices.framework/CoreServices')
            ptr = C.c_void_p
            cf.CFStringCreateWithCString.argtypes = [ptr, C.c_char_p, C.c_uint32]
            cf.CFStringCreateWithCString.restype = ptr
            cf.CFArrayCreate.argtypes = [ptr, C.POINTER(ptr), C.c_long, ptr]
            cf.CFArrayCreate.restype = ptr
            cf.CFRunLoopGetCurrent.restype = ptr
            callback_type = C.CFUNCTYPE(None, ptr, ptr, C.c_size_t, ptr,
                                       C.POINTER(C.c_uint32), C.POINTER(C.c_uint64))

            def callback(stream, info, count, paths, flags, ids):
                names = C.cast(paths, C.POINTER(C.c_char_p))
                for i in range(count):
                    path = os.fsdecode(names[i])
                    if not self.excluded(path):
                        self.pending.put((path, int(flags[i]), int(ids[i])))

            self.callback = callback_type(callback)
            strings = [cf.CFStringCreateWithCString(None, os.fsencode(p), 0x08000100)
                       for p in self.roots]
            array = cf.CFArrayCreate(None, (ptr * len(strings))(*strings), len(strings), None)
            fs.FSEventStreamCreate.argtypes = [ptr, callback_type, ptr, ptr,
                                              C.c_uint64, C.c_double, C.c_uint32]
            fs.FSEventStreamCreate.restype = ptr
            fs.FSEventStreamScheduleWithRunLoop.argtypes = [ptr, ptr, ptr]
            fs.FSEventStreamStart.argtypes = [ptr]
            fs.FSEventStreamStart.restype = C.c_bool
            self.stream = fs.FSEventStreamCreate(None, self.callback, None, array,
                                                 self.since, 0.05, 0x10 | 0x02 | 0x04)
            if not self.stream:
                raise RuntimeError("FSEventStreamCreate failed")
            mode = ptr.in_dll(cf, 'kCFRunLoopDefaultMode')
            fs.FSEventStreamScheduleWithRunLoop(self.stream, cf.CFRunLoopGetCurrent(), mode)
            if not fs.FSEventStreamStart(self.stream):
                raise RuntimeError("FSEventStreamStart failed")
            self.ready.set()
            cf.CFRunLoopRun()
        except BaseException as exc:
            self.error = repr(exc)
            self.ready.set()


class Collector:
    categories = ('raw-session', 'prompts', 'responses', 'commands', 'tools', 'files', 'collector')

    def __init__(self, config_path):
        os.umask(0o077)
        self.config_path = Path(config_path).resolve()
        self.cfg = json.loads(self.config_path.read_text())
        self.base = self.config_path.parent
        self.out = Path(self.cfg['output_dir']).resolve()
        self.state = Path(self.cfg.get('state_dir', self.base)).resolve()
        self.state.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.exclusions = [Path(p).resolve() for p in self.cfg.get('exclude_paths', [])]
        self.exclusions += [self.out, self.base / '__pycache__']
        if self.state != self.base:
            self.exclusions.append(self.state)
        else:
            self.exclusions.extend(self.base / name for name in
                ('collector.lock', 'status.json', 'status.json.tmp', 'stdout.log', 'stderr.log'))
        self.out.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.blobs = self.out / 'snapshots'
        self.blobs.mkdir(exist_ok=True, mode=0o700)
        self.lock = (self.state / 'collector.lock').open('a')
        fcntl.flock(self.lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        self.db = sqlite3.connect(self.out / 'audit.sqlite3')
        self.db.execute('PRAGMA journal_mode=WAL')
        self.db.executescript('''
            CREATE TABLE IF NOT EXISTS events (
              id INTEGER PRIMARY KEY, category TEXT NOT NULL, data TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS files (path TEXT PRIMARY KEY, data TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS normalized_items (kind TEXT, item_id TEXT,
              PRIMARY KEY(kind, item_id));
        ''')
        with self.db:
            for (value,) in self.db.execute("SELECT data FROM events WHERE category != 'raw-session'"):
                event = json.loads(value)
                item = event.get('item', {})
                if event.get('source') == 'codex_item_completed' and item.get('id'):
                    self.db.execute('INSERT OR IGNORE INTO normalized_items VALUES (?,?)',
                                    (item.get('type'), item['id']))
        self.handles = {}
        # SQLite is authoritative. Rebuild exports on restart to remove crash duplicates.
        for category in self.categories:
            handle = (self.out / (category + '.jsonl')).open('w')
            self.handles[category] = handle
            for (value,) in self.db.execute('SELECT data FROM events WHERE category=? ORDER BY id', (category,)):
                handle.write(value + '\n')
            handle.flush()
        self.export_cursor = self.db.execute('SELECT coalesce(max(id),0) FROM events').fetchone()[0]
        self.pending = queue.Queue()
        self.stopping = False
        self.source = Path(self.cfg['source_transcript'])
        self.sources = {self.source.name: self.source}
        self.next_source_discovery = 0
        self.roots = [Path(p).resolve() for p in self.cfg['watch_roots']]
        self.started = now()
        self.last_error = None

    def excluded(self, path):
        path = Path(os.path.abspath(path))
        return any(path == root or path.is_relative_to(root) for root in self.exclusions)

    def get(self, key, default=None):
        row = self.db.execute('SELECT value FROM meta WHERE key=?', (key,)).fetchone()
        return json.loads(row[0]) if row else default

    def set(self, key, value):
        self.db.execute('INSERT OR REPLACE INTO meta VALUES (?,?)', (key, dump(value)))

    def emit(self, category, value):
        if category != 'raw-session':
            value = {'timestamp': now(), 'session_id': self.cfg['session_id'], **value}
        self.db.execute('INSERT INTO events(category,data) VALUES (?,?)', (category, dump(value)))

    def export(self):
        for ident, category, value in self.db.execute(
                'SELECT id,category,data FROM events WHERE id>? ORDER BY id', (self.export_cursor,)):
            self.handles[category].write(value + '\n')
            self.export_cursor = ident
        for handle in self.handles.values():
            handle.flush()

    def normalize(self, row):
        p = row.get('payload', {})
        timestamp = row.get('timestamp', now())
        if row.get('type') == 'event_msg' and p.get('type') == 'item_completed':
            item = p.get('item', {})
            kind = item.get('type')
            if item.get('id'):
                inserted = self.db.execute('INSERT OR IGNORE INTO normalized_items VALUES (?,?)',
                                           (kind, item['id']))
                if not inserted.rowcount:
                    return
            base = {'timestamp': timestamp, 'turn_id': p.get('turn_id'),
                    'source': 'codex_item_completed', 'item': item}
            if kind in ('UserMessage', 'AgentMessage'):
                content = item.get('content', [])
                text = '\n'.join(x.get('text', '') for x in content if isinstance(x, dict))
                self.emit('prompts' if kind == 'UserMessage' else 'responses',
                          {**base, 'text': text, 'phase': item.get('phase')})
            elif kind == 'CommandExecution':
                self.emit('commands', {**base, 'command': item.get('command'),
                    'cwd': item.get('cwd'), 'exit_code': item.get('exit_code'),
                    'status': item.get('status'), 'output': item.get('aggregated_output'),
                    'started_at_ms': p.get('started_at_ms'), 'completed_at_ms': p.get('completed_at_ms')})
            elif kind == 'FileChange':
                self.emit('files', {**base, 'actor': 'codex', 'status': item.get('status')})
            elif kind not in ('Reasoning',):
                self.emit('tools', base)
        elif row.get('type') == 'response_item' and p.get('type') in (
                'function_call', 'custom_tool_call', 'function_call_output', 'custom_tool_call_output'):
            self.emit('tools', {'timestamp': timestamp, 'source': 'codex_response_item', 'item': p})

    def read_transcript(self):
        # A resumed chat can create additional rollout files with the same session id.
        if time.monotonic() >= self.next_source_discovery:
            for folder in ('sessions', 'archived_sessions'):
                for path in (Path.home() / '.codex' / folder).glob(f'**/*{self.cfg["session_id"]}*.jsonl'):
                    try:
                        with path.open('rb') as stream:
                            meta = json.loads(stream.readline())
                    except (OSError, ValueError):
                        continue
                    if meta.get('type') == 'session_meta' and meta.get('payload', {}).get('id') == self.cfg['session_id']:
                        self.sources[path.name] = path
            self.next_source_discovery = time.monotonic() + 10
        offsets = self.get('source_offsets', {self.source.name: self.get('source_offset', 0)})
        budget = 2000
        for name, path in sorted(self.sources.items()):
            offset = offsets.get(name, 0)
            if path.stat().st_size < offset:
                raise RuntimeError(f'Transcript shrank: {name}')
            with path.open('rb') as source:
                source.seek(offset)
                while budget > 0:
                    line = source.readline()
                    if not line or not line.endswith(b'\n'):
                        break
                    row = json.loads(line)
                    self.emit('raw-session', row)
                    self.normalize(row)
                    offsets[name] = source.tell()
                    budget -= 1
        self.set('source_offsets', offsets)
        self.set('source_offset', sum(offsets.values()))

    def snapshot(self, path):
        try:
            s = path.lstat()
        except FileNotFoundError:
            return None
        result = {'size': s.st_size, 'mode': stat.S_IMODE(s.st_mode),
                  'mtime_ns': s.st_mtime_ns, 'inode': s.st_ino}
        if stat.S_ISLNK(s.st_mode):
            result.update(kind='symlink', target=os.readlink(path))
        elif stat.S_ISDIR(s.st_mode):
            result['kind'] = 'directory'
        elif stat.S_ISREG(s.st_mode):
            result['kind'] = 'file'
            if s.st_size <= self.cfg['snapshot_max_bytes']:
                # Do not follow a symlink swapped into place between lstat/open.
                fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
                with os.fdopen(fd, 'rb') as stream:
                    if not stat.S_ISREG(os.fstat(stream.fileno()).st_mode):
                        result['snapshot_skipped'] = 'type_changed'
                        return result
                    data = stream.read(self.cfg['snapshot_max_bytes'] + 1)
                if len(data) <= self.cfg['snapshot_max_bytes']:
                    digest = hashlib.sha256(data).hexdigest()
                    blob = self.blobs / digest
                    if not blob.exists():
                        blob.write_bytes(data)
                    result.update(sha256=digest, snapshot=str(blob))
                else:
                    result['snapshot_skipped'] = 'size_limit'
            else:
                result['snapshot_skipped'] = 'size_limit'
        else:
            result['kind'] = 'special'
        return result

    def file_before(self, path):
        row = self.db.execute('SELECT data FROM files WHERE path=?', (str(path),)).fetchone()
        return json.loads(row[0]) if row else None

    def observe(self, path, flags=0, event_id=None, baseline=False):
        path = Path(os.path.abspath(path))
        if self.excluded(path):
            return
        if not any(path == root or path.is_relative_to(root) for root in self.roots):
            return
        before = self.file_before(path)
        after = self.snapshot(path)
        actions = [name for flag, name in [(0x100,'created'), (0x200,'deleted'),
                   (0x800,'renamed'), (0x1000,'modified'), (0x400,'metadata_changed'),
                   (0x4000,'owner_changed'), (0x8000,'xattr_changed')] if flags & flag]
        observed = ('created' if before is None and after is not None else
                    'deleted' if before is not None and after is None else
                    'modified' if before != after else 'event_only')
        if baseline or flags or before != after:
            value = {'source': 'filesystem_baseline' if baseline else 'macos_fsevents',
                     'actor': 'unknown', 'path': str(path), 'event_id': event_id,
                     'flags': flags, 'actions': actions, 'observed_change': observed,
                     'before': before, 'after': after}
            if not baseline and before != after:
                try:
                    old = Path(before['snapshot']).read_bytes() if before and 'snapshot' in before else b''
                    new = Path(after['snapshot']).read_bytes() if after and 'snapshot' in after else b''
                    complete = all(x is None or 'snapshot' in x for x in (before, after))
                    if complete and b'\x00' not in old + new:
                        value['diff'] = ''.join(difflib.unified_diff(
                            old.decode('utf-8').splitlines(keepends=True),
                            new.decode('utf-8').splitlines(keepends=True),
                            fromfile=str(path) + ' (before)', tofile=str(path) + ' (after)'))
                except UnicodeError:
                    pass
            self.emit('files', value)
        if after is None:
            self.db.execute('DELETE FROM files WHERE path=?', (str(path),))
        else:
            self.db.execute('INSERT OR REPLACE INTO files VALUES (?,?)', (str(path), dump(after)))

    def reconcile(self, baseline=False):
        seen = set()
        for root in self.roots:
            for parent, directories, files in os.walk(root, followlinks=False,
                    onerror=lambda e: self.emit('collector', {'event':'scan_error', 'error':str(e)})):
                directories[:] = [name for name in directories if not self.excluded(Path(parent) / name)]
                for name in directories + files:
                    path = Path(parent) / name
                    if self.excluded(path):
                        continue
                    seen.add(str(path))
                    self.observe(path, baseline=baseline)
        for (path,) in self.db.execute('SELECT path FROM files').fetchall():
            if self.excluded(path):
                self.db.execute('DELETE FROM files WHERE path=?', (path,))
                continue
            if path not in seen and Path(path) not in self.roots:
                self.observe(path)

    def heartbeat(self, status='running'):
        value = {'status': status, 'pid': os.getpid(), 'session_id': self.cfg['session_id'],
                 'started_at': self.started, 'updated_at': now(),
                 'source_transcript': str(self.source), 'source_offset': self.get('source_offset', 0),
                 'source_bytes': sum(p.stat().st_size for p in self.sources.values() if p.exists()),
                 'source_transcripts': [{'path': str(p), 'offset': self.get('source_offsets', {}).get(name, 0),
                                        'bytes': p.stat().st_size if p.exists() else None}
                                       for name, p in sorted(self.sources.items())],
                 'watch_roots': [str(p) for p in self.roots],
                 'excluded_paths': [str(p) for p in self.exclusions],
                 'filesystem_watcher_alive': self.watcher.thread.is_alive(),
                 'last_error': self.last_error,
                 'counts': dict(self.db.execute('SELECT category,count(*) FROM events GROUP BY category'))}
        atomic_json(self.state / 'status.json', value)

    def run(self):
        self.watcher = FileEvents([str(p) for p in self.roots],
                                self.get('fsevent_id', 0xffffffffffffffff), self.pending,
                                self.excluded)
        self.watcher.start()
        with self.db:
            self.reconcile(baseline=not self.get('baseline_complete', False))
            self.set('baseline_complete', True)
            self.emit('collector', {'event': 'started', 'pid': os.getpid(), 'watch_roots': self.cfg['watch_roots']})
        for sig in (signal.SIGTERM, signal.SIGINT):
            signal.signal(sig, lambda *_: setattr(self, 'stopping', True))
        last_heartbeat = 0
        while not self.stopping:
            if not self.watcher.thread.is_alive():
                raise RuntimeError(self.watcher.error or 'FSEvents thread stopped')
            try:
                with self.db:
                    self.read_transcript()
                self.last_error = None
            except Exception as exc:
                self.last_error = repr(exc)
                with self.db:
                    self.emit('collector', {'event': 'transcript_error', 'error': self.last_error})
            with self.db:
                for _ in range(10000):
                    try:
                        path, flags, event_id = self.pending.get_nowait()
                    except queue.Empty:
                        break
                    try:
                        self.observe(path, flags, event_id)
                        if flags & (0x01 | 0x02 | 0x04 | 0x20):
                            self.emit('collector', {'event':'fsevents_rescan', 'path':path, 'flags':flags})
                            self.reconcile()
                        self.set('fsevent_id', event_id)
                    except OSError as exc:
                        self.emit('collector', {'event':'file_read_error', 'path':path, 'error':str(exc)})
            self.export()
            if time.monotonic() - last_heartbeat >= self.cfg['heartbeat_seconds']:
                self.heartbeat('degraded' if self.last_error else 'running')
                last_heartbeat = time.monotonic()
            time.sleep(self.cfg['poll_seconds'])
        with self.db:
            self.read_transcript()
            self.emit('collector', {'event':'stopped'})
        self.export()
        self.heartbeat('stopped')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--config', default=str(Path(__file__).with_name('config.json')))
    parser.add_argument('--status', action='store_true')
    args = parser.parse_args()
    config_path = Path(args.config).resolve()
    if args.status:
        cfg = json.loads(config_path.read_text())
        status = json.loads((Path(cfg.get('state_dir', config_path.parent)) / 'status.json').read_text())
        try:
            os.kill(status['pid'], 0)
            status['process_alive'] = True
        except ProcessLookupError:
            status['process_alive'] = False
        age = (dt.datetime.now(dt.timezone.utc) - dt.datetime.fromisoformat(status['updated_at'])).total_seconds()
        status['heartbeat_age_seconds'] = round(age, 1)
        print(json.dumps(status, ensure_ascii=False, indent=2))
        return 0 if status['process_alive'] and age < 15 and status['status'] == 'running' else 1
    if not json.loads(config_path.read_text()).get('enabled', False):
        return 0
    Collector(config_path).run()
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
