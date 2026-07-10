import json
import os
import sys
import unittest


HERE = os.path.dirname(__file__)
AGENT_DIR = os.path.abspath(os.path.join(HERE, ".."))
if AGENT_DIR not in sys.path:
    sys.path.insert(0, AGENT_DIR)

from dahua_camera import DahuaCameraListener


class DahuaCameraListenerTest(unittest.TestCase):
    def test_requires_human_object_type(self):
        captured = []
        cam = DahuaCameraListener(
            host="127.0.0.1",
            username="admin",
            password="x",
            require_human=True,
            on_crossing=lambda event: captured.append(event),
        )
        payload_without_object_type = json.dumps({"Direction": "LeftToRight"})
        cam._handle_event("Code=CrossLineDetection;action=Start;", payload_without_object_type)
        self.assertEqual([], captured)

    def test_accepts_human_object_type(self):
        captured = []
        cam = DahuaCameraListener(
            host="127.0.0.1",
            username="admin",
            password="x",
            require_human=True,
            on_crossing=lambda event: captured.append(event),
        )
        payload = json.dumps({"Direction": "LeftToRight", "Object": {"ObjectType": "Human"}})
        cam._handle_event("Code=CrossLineDetection;action=Start;", payload)
        self.assertEqual(1, len(captured))

    def test_accepts_object_type_at_root(self):
        captured = []
        cam = DahuaCameraListener(
            host="127.0.0.1",
            username="admin",
            password="x",
            require_human=True,
            on_crossing=lambda event: captured.append(event),
        )
        payload = json.dumps({"Direction": "LeftToRight", "ObjectType": "Human"})
        cam._handle_event("Code=CrossLineDetection;action=Start;", payload)
        self.assertEqual(1, len(captured))

    def test_accepts_action_stop(self):
        captured = []
        cam = DahuaCameraListener(
            host="127.0.0.1",
            username="admin",
            password="x",
            require_human=False,
            on_crossing=lambda event: captured.append(event),
        )
        payload = json.dumps({"Direction": "RightToLeft"})
        cam._handle_event("Code=CrossLineDetection;action=Stop;", payload)
        self.assertEqual(1, len(captured))

    def test_direction_filter_ignores_wrong_direction(self):
        captured = []
        cam = DahuaCameraListener(
            host="127.0.0.1",
            username="admin",
            password="x",
            inbound_direction="LeftToRight",
            require_human=False,
            on_crossing=lambda event: captured.append(event),
        )
        payload = json.dumps({"Direction": "RightToLeft"})
        cam._handle_event("Code=CrossLineDetection;action=Start;", payload)
        self.assertEqual([], captured)

    def test_consume_buffer_handles_partial_json(self):
        captured = []
        cam = DahuaCameraListener(
            host="127.0.0.1",
            username="admin",
            password="x",
            require_human=False,
            on_crossing=lambda event: captured.append(event),
        )
        cam._textbuf = "Code=CrossLineDetection;action=Start;data={\"Direction\":\"Left"
        cam._consume_buffer()
        self.assertEqual([], captured)
        cam._textbuf += "ToRight\"}\n"
        cam._consume_buffer()
        self.assertEqual(1, len(captured))


if __name__ == "__main__":
    unittest.main()

