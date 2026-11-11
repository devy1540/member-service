import unittest

from export_submission import Masker


class MaskerTests(unittest.TestCase):
    def test_masks_file_paths_and_email_addresses_used_as_dictionary_keys(self):
        result = Masker().apply({
            "changes": {"/Users/test-account/project/file.kt": {"path": "/Users/test-account/project/file.kt"}},
            "contacts": {"person@example.test": "value"},
        })
        self.assertEqual({"/Users/***/project/file.kt": {"path": "/Users/***/project/file.kt"}}, result["changes"])
        self.assertEqual({"***": "value"}, result["contacts"])

    def test_does_not_silently_drop_entries_when_masked_keys_collide(self):
        with self.assertRaises(ValueError):
            Masker().apply({"/Users/first/project": 1, "/Users/second/project": 2})

    def test_masks_credentials_but_preserves_opaque_payloads(self):
        result = Masker().apply({"password": "private-value", "encrypted_content": "/Users/test-account/opaque"})
        self.assertEqual("***", result["password"])
        self.assertEqual("/Users/test-account/opaque", result["encrypted_content"])


if __name__ == "__main__":
    unittest.main()
