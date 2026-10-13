import copy,json,tempfile,unittest
from pathlib import Path
from generate_venue_collections import ROOT,generate
from publish_venue_catalogue import validate,publish
class VenueCatalogueTest(unittest.TestCase):
    def setUp(self):
        self.config=json.loads((ROOT/'collections/venue-collections-v1.json').read_text());self.service=self.config['collections'][0]
        self.source=ROOT/'collections/published/services/catalogue-2026101301.json';self.root=json.loads(self.source.read_text())
    def test_existing_unknown_operator_is_preserved_without_adding_brand_eligibility(self):
        validate(self.root,self.service)
        unknown=[v for v in self.root['services'] if not v.get('operator_group')]
        self.assertTrue(unknown)
    def test_cross_collection_and_invalid_site_position_are_rejected(self):
        for mutate in [lambda r:r.update(collection_id='wetherspoons'),lambda r:r['services'][0].update(lat=0),lambda r:r['services'][0]['points'][0].update(lng=90),lambda r:r['services'].append(copy.deepcopy(r['services'][0]))]:
            root=copy.deepcopy(self.root);mutate(root)
            with self.assertRaises(ValueError):validate(root,self.service)
    def test_publication_is_immutable_and_cannot_downgrade(self):
        with tempfile.TemporaryDirectory() as td:
            revision=self.service['bundled_revision'];publish(self.source,self.service,revision,td)
            publish(self.source,self.service,revision,td)
            changed=Path(td)/'changed.json';root=copy.deepcopy(self.root);root['services'][0]['name']='Changed';changed.write_text(json.dumps(root))
            with self.assertRaises(ValueError):publish(changed,self.service,revision,td)
            with self.assertRaises(ValueError):publish(self.source,self.service,revision-1,td)
    def test_duplicate_achievement_or_collection_ids_do_not_compile(self):
        for mutate in [lambda c:c['collections'].append(copy.deepcopy(c['collections'][0])),lambda c:c['collections'][1]['achievements'].append(copy.deepcopy(c['collections'][0]['achievements'][0]))]:
            config=copy.deepcopy(self.config);mutate(config)
            with self.assertRaises(ValueError):generate(config)
if __name__=='__main__':unittest.main()
