"""Build the shared ONS settlement metadata catalogue (no geometry)."""
import json, urllib.parse, urllib.request

URL='https://services1.arcgis.com/ESMARspQHYMw9BZ9/arcgis/rest/services/BUA_2022_GB/FeatureServer/0/query'
features=[]
for offset in range(0,9000,1000):
    query=urllib.parse.urlencode({'where':'1=1','outFields':'BUA22CD,BUA22NM,LONG,LAT','returnGeometry':'false','resultOffset':offset,'resultRecordCount':1000,'f':'json'})
    page=json.load(urllib.request.urlopen(URL+'?'+query))['features']
    features.extend(page)
    if len(page)<1000: break
available={'E63003709','E63005058','E63005204'}
aliases={'E63005204':['Gillingham']}
settlements=[{'code':f['attributes']['BUA22CD'],'name':f['attributes']['BUA22NM'],'aliases':aliases.get(f['attributes']['BUA22CD'],[]),'centre':[f['attributes'].get('LAT'),f['attributes'].get('LONG')],'status':'available' if f['attributes']['BUA22CD'] in available else 'pending'} for f in features]
json.dump({'version':1,'source':'ONS Built Up Areas (December 2022) Boundaries GB','settlements':settlements},open('settlement-catalogue-v1.json','w'),separators=(',',':'))
