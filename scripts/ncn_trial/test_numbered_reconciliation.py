from copy import deepcopy
import unittest
from .numbered_reconciliation import numbered_candidates


def profile(id,network,ref,**tags):
    return {'relation_id':id,'tags':{'network':network,'ref':ref,**tags},'ways':[{'way_id':42}]}


class NumberedReconciliationTests(unittest.TestCase):
    def test_foreign_same_number_does_not_fill_a_missing_uk_route(self):
        result=numbered_candidates({'routes':[{'id':'NCN:410'}]},[profile(1,'ncn','410',cycle_network='US:US')],{1})
        self.assertEqual(['NCN:410'],result['unresolved_numbered_keys'])
        self.assertEqual('explicit_non_uk_namespace',result['same_number_or_classification_leads_held'][0]['reason'])

    def test_regional_classification_is_explicit_not_number_length(self):
        result=numbered_candidates({'routes':[{'id':'NCN:15'},{'id':'RCN:15'}]},[profile(1,'rcn','15')],{1})
        self.assertEqual(['NCN:15'],result['unresolved_numbered_keys'])
        self.assertEqual('RCN:15',result['routes'][0]['official_route_key'])

    def test_global_numeric_search_needs_an_independent_uk_anchor(self):
        result=numbered_candidates({'routes':[{'id':'NCN:700'}]},[profile(1,'ncn','700')],set())
        self.assertEqual(0,result['candidate_numbered_keys'])

    def test_numeric_agreement_is_only_a_lead_and_shared_ways_deduplicate(self):
        profiles=[profile(1,'ncn','1',cycle_network='UK:National Cycle Network'),profile(2,'ncn','2')]
        original=deepcopy(profiles)
        result=numbered_candidates({'routes':[{'id':'NCN:1'},{'id':'NCN:2'}]},profiles,{1,2})
        self.assertEqual(2,result['candidate_numbered_keys']);self.assertEqual(1,result['candidate_source_way_ids'])
        self.assertFalse(result['official_gis_association_approved']);self.assertEqual(original,profiles)
