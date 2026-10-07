import unittest
from build_motorway_service_collection import apply_reviewed_reference,enrich_achievement_metadata

class ServiceMetadataTest(unittest.TestCase):
    def test_renamed_source_preserves_identity_road_and_region(self):
        reference={'old-id':{'id':'old-id','source_ids':['osm:way:1'],'road':'M1','region':'GB'}}
        updated=apply_reviewed_reference({'id':'renamed-id','name':'Renamed Services','source_ids':['osm:way:1'],'road':None,'lat':52,'lng':-1,'operator':'RoadChef'},reference)
        self.assertEqual('old-id',updated['id']);self.assertEqual('M1',updated['road'])
        self.assertEqual('Roadchef',updated['operator_group']);self.assertEqual('England',updated['country'])
    def test_northern_ireland_does_not_count_as_england(self):
        site=enrich_achievement_metadata({'name':'Lisburn','road':'M1','region':'NI','operator':'Applegreen','lng':-6,'lat':54})
        self.assertEqual('Northern Ireland',site['country'])
    def test_unknown_assignment_is_rejected_without_overwriting_catalogue(self):
        with self.assertRaises(ValueError):apply_reviewed_reference({'id':'new','name':'New Services','road':None,'source_ids':[]}, {})

if __name__=='__main__':unittest.main()
