import datetime as dt
import unittest
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


if __name__ == '__main__': unittest.main()
