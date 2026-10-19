import unittest
from unittest.mock import Mock
from firebase_existing_testers import existing_emails


class ExistingTesterTest(unittest.TestCase):
    def response(self,testers,token=''):
        result=Mock(status_code=200)
        result.json.return_value={'testers':[{'name':name} for name in testers],'nextPageToken':token}
        return result

    def test_paginates_and_only_returns_existing_project_identities(self):
        session=Mock();session.get.side_effect=[self.response(['projects/123/testers/test%40example.invalid'],'next'),
                                               self.response(['projects/123/testers/test@example.invalid'])]
        self.assertEqual(['test@example.invalid'],existing_emails(session,'123'))
        self.assertEqual(2,session.get.call_count)

    def test_rejects_empty_foreign_or_malformed_identity(self):
        for names in [[],['projects/other/testers/test@example.invalid'],['projects/123/testers/a@example.invalid,b@example.invalid']]:
            session=Mock();session.get.return_value=self.response(names)
            with self.assertRaises(ValueError):existing_emails(session,'123')

    def test_rejects_failed_lookup_and_repeated_page(self):
        session=Mock();session.get.return_value=Mock(status_code=403)
        with self.assertRaises(ValueError):existing_emails(session,'123')
        session.get.return_value=self.response(['projects/123/testers/test@example.invalid'],'loop')
        with self.assertRaises(ValueError):existing_emails(session,'123')
