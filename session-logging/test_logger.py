import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from logger import Collector


class TranscriptResumeTests(unittest.TestCase):
    def test_resumed_rollouts_and_legacy_cursor_do_not_replay_items(self):
        state = Path(__file__).parent / '.state'
        with tempfile.TemporaryDirectory(dir=state) as directory:
            home = Path(directory)
            sessions = home / '.codex/sessions/2026/11/10'
            sessions.mkdir(parents=True)
            session_id = 'test-resumed-session'
            first = sessions / f'rollout-01-{session_id}.jsonl'
            second = sessions / f'rollout-02-{session_id}_resumed.jsonl'
            meta = {'type': 'session_meta', 'payload': {'id': session_id}}
            prompt = {'type': 'event_msg', 'payload': {'type': 'item_completed', 'item': {
                'type': 'UserMessage', 'id': 'prompt-1', 'content': [{'text': 'fixture prompt'}]}}}
            response = {'type': 'event_msg', 'payload': {'type': 'item_completed', 'item': {
                'type': 'AgentMessage', 'id': 'reply-1', 'content': [{'text': 'fixture response'}]}}}
            first.write_text('\n'.join(json.dumps(row) for row in [meta, prompt]) + '\n')
            config = home / 'config.json'
            config.write_text(json.dumps({
                'session_id': session_id,
                'source_transcript': str(first),
                'output_dir': str(home / 'logs'),
                'state_dir': str(home / 'state'),
                'watch_roots': [],
            }))
            collector = Collector(config)
            try:
                with patch.object(Path, 'home', return_value=home):
                    with collector.db:
                        collector.read_transcript()
                        # Emulate the cursor written by the previous single-file collector.
                        collector.db.execute("DELETE FROM meta WHERE key='source_offsets'")
                    second.write_text('\n'.join(json.dumps(row) for row in [meta, prompt, response]) + '\n')
                    collector.next_source_discovery = 0
                    with collector.db:
                        collector.read_transcript()
                    counts = dict(collector.db.execute('SELECT category,count(*) FROM events GROUP BY category'))
                    self.assertEqual(5, counts['raw-session'])
                    self.assertEqual(1, counts['prompts'])
                    self.assertEqual(1, counts['responses'])
                    with collector.db:
                        collector.read_transcript()
                    self.assertEqual(counts, dict(collector.db.execute('SELECT category,count(*) FROM events GROUP BY category')))
                    offsets = collector.get('source_offsets')
                    self.assertEqual(first.stat().st_size, offsets[first.name])
                    self.assertEqual(second.stat().st_size, offsets[second.name])
            finally:
                for handle in collector.handles.values():
                    handle.close()
                collector.db.close()
                collector.lock.close()


if __name__ == '__main__':
    unittest.main()
