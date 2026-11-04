import datetime as dt
import unittest
import contextlib
import io
import json
import tempfile
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock, patch
import check_ncn_publication_quota as quota
from check_ncn_publication_quota import assess, METRICS


class QuotaTests(unittest.TestCase):
    now = dt.datetime(2026, 11, 4, 16, tzinfo=dt.timezone.utc)

    def metrics(self):
        def series(value):
            return [{'resource': {'labels': {'domain': 'a'}}, 'metric': {},
                     'points': [{'interval': {'endTime': self.now.isoformat()},
                                 'value': {'int64Value': str(value)}}]}]
        return dict(zip(METRICS, map(series, [100, 10_000_000_000, 100, 10_000_000_000])))

    def test_headroom_reserves_transfer_and_storage(self):
        self.assertEqual(100, assess(self.metrics(), self.now, 150_000_000, 150_000_000)['monthly_sent_bytes'])

    def test_no_data_is_not_zero_usage(self):
        for key in METRICS:
            m = self.metrics(); m[key] = []
            with self.assertRaises(ValueError): assess(m, self.now, 1, 1)

    def test_stale_and_future_data_fail_closed(self):
        for hours in [-1, 11]:
            m = self.metrics()
            m[METRICS[0]][0]['points'][0]['interval']['endTime'] = (self.now-dt.timedelta(hours=hours)).isoformat()
            with self.assertRaises(ValueError): assess(m, self.now, 1, 1)

    def test_insufficient_project_quota_blocks(self):
        for key in [METRICS[1], METRICS[3]]:
            m = self.metrics(); m[key][0]['points'][0]['value']['int64Value'] = '1000000000'
            with self.assertRaises(ValueError): assess(m, self.now, 150_000_000, 150_000_000)

    def test_all_domains_count_and_duplicate_series_rejected(self):
        m = self.metrics(); second = self.metrics()[METRICS[0]][0]
        second['resource']['labels']['domain'] = 'b'; m[METRICS[0]].append(second)
        self.assertEqual(200, assess(m, self.now, 1, 1)['monthly_sent_bytes'])
        m[METRICS[0]].append(second)
        with self.assertRaises(ValueError): assess(m, self.now, 1, 1)


class PreflightTests(unittest.TestCase):
    now = QuotaTests.now
    metrics = QuotaTests.metrics
    def response(self, status, body):
        return SimpleNamespace(status_code=status, json=lambda: body)

    def execute(self, responses, env=None):
        session = Mock()
        session.get.side_effect = responses
        settings = {'FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON': json.dumps(
            {'project_id': quota.PROJECT, 'type': 'service_account'}),
            'ROADPRINTS_DATA_HOSTING_SITE': quota.SITE} if env is None else env
        with tempfile.TemporaryDirectory() as folder, patch.dict('os.environ', settings, clear=True), \
                patch.object(quota, 'authenticated_session', return_value=session) as auth, \
                contextlib.redirect_stdout(io.StringIO()) as output:
            try:
                quota.run(Path(folder))
                code = 0
            except SystemExit as error:
                code = error.code
            result = json.loads((Path(folder)/'quota.json').read_text())
            self.assertNotIn('PRIVATE_SECRET', output.getvalue())
            self.assertNotIn('PRIVATE_SECRET', json.dumps(result))
        return code, result, session, auth

    def live(self, site=quota.SITE):
        return self.response(200, {'release': {'version': {'name': f'sites/{site}/versions/baseline'}}})

    def fresh_metrics(self):
        metrics = self.metrics()
        for rows in metrics.values():
            rows[0]['points'][0]['interval']['endTime'] = dt.datetime.now(dt.timezone.utc).isoformat()
        return metrics

    def test_valid_quota_permits_only_after_all_metrics(self):
        metrics = self.fresh_metrics()
        code, result, session, _ = self.execute([self.live()] + [
            self.response(200, {'timeSeries': metrics[name]}) for name in METRICS])
        self.assertEqual(0, code)
        self.assertEqual('quota_verified', result['status'])
        self.assertEqual(145856489 + 65536, result['planned_verification_bytes'])
        self.assertEqual(5, session.get.call_count)
        for call in session.get.call_args_list:
            self.assertFalse(call.kwargs['allow_redirects'])
        self.assertEqual(quota.QUOTA_URL, session.get.call_args.args[0])

    def test_insufficient_quota_stays_blocked(self):
        metrics = self.fresh_metrics()
        metrics[METRICS[1]][0]['points'][0]['value']['int64Value'] = '1000000000'
        code, result, session, _ = self.execute([self.live()] + [
            self.response(200, {'timeSeries': metrics[name]}) for name in METRICS])
        self.assertEqual(1, code)
        self.assertEqual('blocked', result['status'])
        self.assertIn('Insufficient measured', result['blocker'])
        self.assertFalse(result['hosting_writes'])

    def test_http_failures_block_before_payload_and_redact_body(self):
        for status in [401, 403, 404, 429, 500, 503]:
            with self.subTest(status=status):
                code, result, session, _ = self.execute([self.live(), self.response(status,
                    {'error': {'status': 'PERMISSION_DENIED', 'message': 'PRIVATE_SECRET'}})])
                self.assertEqual(1, code)
                self.assertEqual(status, result['quota_error']['http_status'])
                self.assertEqual(2, session.get.call_count)
                self.assertEqual(0, result['payload_bytes_downloaded'])
                self.assertFalse(result['hosting_writes'])

    def test_known_error_reasons_are_classified_without_raw_metadata(self):
        for reason, cause in [('SERVICE_DISABLED', 'monitoring_api_disabled'),
                              ('IAM_PERMISSION_DENIED', 'monitoring_permission_denied'),
                              ('ACCESS_TOKEN_SCOPE_INSUFFICIENT', 'oauth_scope_insufficient')]:
            error = {'status': 'PERMISSION_DENIED', 'message': 'PRIVATE_SECRET', 'details': [{
                '@type': 'type.googleapis.com/google.rpc.ErrorInfo', 'domain': 'googleapis.com',
                'reason': reason, 'metadata': {'consumer': 'PRIVATE_SECRET'}}]}
            code, result, _, _ = self.execute([self.live(), self.response(403, {'error': error})])
            self.assertEqual(1, code)
            self.assertEqual(cause, result['quota_error']['cause'])

    def test_permission_message_is_classified_but_not_echoed(self):
        _, result, _, _ = self.execute([self.live(), self.response(403, {'error': {
            'status': 'PERMISSION_DENIED',
            'message': 'Permission monitoring.timeSeries.list denied PRIVATE_SECRET'}})])
        self.assertEqual('monitoring_permission_denied', result['quota_error']['cause'])

    def test_missing_or_mismatched_identity_never_creates_session(self):
        for settings in [{}, {'FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON': 'PRIVATE_SECRET'},
                         {'FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON': '{"project_id":"wrong"}'}]:
            code, result, session, auth = self.execute([], settings)
            self.assertEqual(1, code)
            self.assertEqual('blocked', result['status'])
            auth.assert_not_called()
            session.get.assert_not_called()

    def test_hosting_access_or_site_failure_never_queries_quota(self):
        for response in [self.response(403, {}), self.live('wrong-site')]:
            code, result, session, _ = self.execute([response])
            self.assertEqual(1, code)
            self.assertEqual(1, session.get.call_count)
            self.assertNotIn('quota_request', result)

    def test_unexpected_api_shapes_or_private_values_fail_safely(self):
        for body in [[], {'error': {}}, {'timeSeries': {}}, {'nextPageToken': []},
                     {'timeSeries': ['PRIVATE_SECRET']}, {'timeSeries': []}]:
            code, result, _, _ = self.execute([self.live(), self.response(200, body)])
            self.assertEqual(1, code)
            self.assertEqual('blocked', result['status'])
        for body in [[], {'error': None}, {'error': {'details': [None]}}, 'PRIVATE_SECRET']:
            self.assertNotIn('PRIVATE_SECRET', json.dumps(quota.quota_error(self.response(403, body))))

    def test_repeated_pagination_blocks(self):
        page = self.response(200, {'timeSeries': [], 'nextPageToken': 'same'})
        code, result, session, _ = self.execute([self.live(), page, page])
        self.assertEqual(1, code)
        self.assertEqual('Repeated quota page', result['blocker'])
        self.assertEqual(3, session.get.call_count)

    def test_workflow_contracts_keep_publication_gates(self):
        root = Path(__file__).resolve().parents[1]
        publish = (root/'.github/workflows/ncn-reference-publication.yml').read_text()
        diagnostic = (root/'.github/workflows/ncn-quota-preflight.yml').read_text()
        self.assertLess(publish.index('run: python scripts/check_ncn_publication_quota.py'),
                        publish.index('uses: actions/cache/restore@v4'))
        self.assertIn("assert json.loads(raw)==json.loads", publish)
        self.assertIn('--promote', publish)
        for text in [publish, diagnostic]:
            self.assertIn('group: firebase-reference-publication', text)
            self.assertIn('cancel-in-progress: false', text)
            self.assertIn("github.event_name == 'push' && github.ref == 'refs/heads/main'", text)
        for forbidden in ['publish_ncn_reference.py', 'prepare_ncn_publication', 'cache/restore', 'web.app/']:
            self.assertNotIn(forbidden, diagnostic)


if __name__ == '__main__': unittest.main()
