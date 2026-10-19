const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const context = vm.createContext({window: {}});
vm.runInContext(fs.readFileSync(path.join(root, 'local-data-reset.js'), 'utf8'), context);
const resetter = context.window.RoadprintsLocalDataReset;

function storage() {
  const values = new Map([
    ['uk-road-tracker-progress-v1', 'achievements-and-coverage'],
    ['roadprints:classified-journeys-v1', 'flight-and-ferry'],
    ['roadprints:undetermined-journeys-v1', 'unknown'],
    ['roadprints-import-coordinator-session-v1', 'import-summary'],
    ['roadprints:onboarding-complete-v1', 'true'],
    ['roadprints:service-station-manual-visits:v1', 'visits'],
    ['unrelated-site-setting', 'keep']
  ]);
  return {values, get length() {return values.size;}, key: i => [...values.keys()][i],
    getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value),
    removeItem: key => values.delete(key)};
}
function database(names, {abort = false, deferCommit = false} = {}) {
  const records = new Map(names.map(name => [name, ['saved-personal-data']]));
  let tx, cleared = new Set(), committed = false, closed = false;
  const commit = () => {
    if (abort) {tx.error = new Error('Simulated failed transaction'); tx.onabort();}
    else {for (const name of cleared) records.set(name, []); committed = true; tx.oncomplete();}
  };
  const db = {objectStoreNames: names, transaction: (stores, mode) => {
    assert.equal(mode, 'readwrite'); assert.deepEqual([...stores], names);
    tx = {objectStore: name => ({clear: () => {cleared.add(name); return {};}})};
    if (!deferCommit) setImmediate(commit);
    return tx;
  }, close: () => {closed = true;}};
  return {db, records, commit, get committed() {return committed;}, get closed() {return closed;}};
}

test('delete all commits every store and clears journeys, achievements, visits and import metadata before reload', async () => {
  const saved = storage();
  const archive = database(['journeys', 'foot-activities', 'canonical-roads', 'canonical-a-roads', 'pending-road-import', 'road-discovery']);
  const legacy = database(['activities', 'segment-evidence', 'matching-jobs', 'network-tiles']);
  let stopped = false, reloads = 0;
  await resetter.reset({storage: saved, stopWork: () => {stopped = true;}, whenReady: async () => assert(stopped),
    openArchive: async () => archive.db, openLegacy: async () => legacy.db, reload: () => {
      assert(archive.committed && legacy.committed); reloads++;
      assert.deepEqual([...saved.values.keys()], ['unrelated-site-setting']);
    }});
  assert.equal(reloads, 1); assert(archive.closed && legacy.closed);
  for (const rows of [...archive.records.values(), ...legacy.records.values()]) assert.equal(rows.length, 0);
});

test('request success is insufficient: reset waits for transaction commit', async () => {
  const db = database(['journeys', 'foot-activities'], {deferCommit: true});
  let finished = false;
  const pending = resetter.clearDatabase(async () => db.db).then(() => {finished = true;});
  await new Promise(setImmediate); assert.equal(finished, false); assert.equal(db.closed, false);
  assert.equal(db.records.get('journeys').length, 1);
  db.commit(); await pending; assert.equal(finished, true); assert(db.closed);
});

test('failure retains recovery marker and progress; retry completes deletion', async () => {
  const saved = storage(); let reloads = 0;
  const failed = database(['journeys', 'foot-activities'], {abort: true});
  const args = {storage: saved, stopWork: () => {}, whenReady: async () => {},
    openArchive: async () => failed.db, openLegacy: async () => database([]).db, reload: () => reloads++};
  await assert.rejects(resetter.reset(args), /Simulated failed transaction/);
  assert.equal(reloads, 0); assert.equal(saved.getItem(resetter.RESET_KEY), 'pending');
  assert.equal(saved.getItem('uk-road-tracker-progress-v1'), 'achievements-and-coverage');
  assert.equal(failed.records.get('journeys').length, 1);
  args.openArchive = async () => database(['journeys', 'foot-activities']).db;
  await resetter.reset(args); assert.equal(reloads, 1); assert.equal(saved.getItem(resetter.RESET_KEY), null);
});

// Run the actual application functions with a delayed matcher/database, so
// navigation cancellation is checked at the asynchronous boundary that matters.
const app = fs.readFileSync(path.join(root, 'app.js'), 'utf8');
function functionSource(name) {
  const start = app.search(new RegExp('(?:async )?function ' + name + '\\('));
  assert(start >= 0, name);
  return app.slice(start, app.indexOf('\n}', start) + 2);
}

test('a walking response arriving after delete-all cannot save or restart its queue', async () => {
  let respond; let saves = 0;
  const activity = {points: [{lat: 51, lng: 0}, {lat: 51.1, lng: 0.1}]};
  const sandbox = vm.createContext({
    footMatching: false, localDataResetActive: false, trackingSessionId: 1,
    footBatches: [{id: 'foot', activities: [activity]}], easyImportPaused: false,
    API_BASE_URL: 'test', showFootMap: async () => {}, updateImportStatusButton: () => {},
    setImportReadiness: () => {}, renderFootQueue: () => {},
    fetch: () => new Promise(resolve => {respond = resolve;}),
    saveFootActivityMatch: async () => {saves++;},
    setTimeout: () => {throw new Error('Cancelled queue must not restart');},
    assessMatchQuality: () => ({})
  });
  vm.runInContext(functionSource('startNextFootBatch'), sandbox);
  const running = vm.runInContext('startNextFootBatch()', sandbox);
  await new Promise(setImmediate); assert.equal(typeof respond, 'function');
  sandbox.localDataResetActive = true; sandbox.trackingSessionId++;
  respond({ok: true, json: async () => ({geojson: {features: []}})});
  await running; assert.equal(saves, 0); assert.equal(activity.matchedGeoJson, undefined);
});

test('a writer waiting for IndexedDB open cannot repopulate a reset archive', async () => {
  for (const name of ['mapArchiveOperation', 'footArchiveOperation', 'pendingRoadImportOperation', 'roadDiscoveryArchiveOperation', 'canonicalRoadArchiveOperation', 'canonicalARoadArchiveOperation']) {
    let opened; let closed = false; let writes = 0;
    const sandbox = vm.createContext({localDataResetActive: false,
      openMapArchiveDatabase: () => new Promise(resolve => {opened = resolve;})});
    vm.runInContext(functionSource(name), sandbox);
    const pending = vm.runInContext(name + "('readwrite', () => {})", sandbox);
    sandbox.localDataResetActive = true;
    opened({close: () => {closed = true;}, transaction: () => {writes++; throw Error('Unexpected write');}});
    await pending; assert.equal(writes, 0, name); assert(closed, name);
  }
});


test('road-only deletion still permits archive clearing', async () => {
  let writes = 0;
  const db = database(['journeys']);
  const sandbox = vm.createContext({localDataResetActive: false, localProgressDeletionRunning: true,
    MAP_ARCHIVE_STORE_NAME: 'journeys', openMapArchiveDatabase: async () => ({
      transaction: () => ({objectStore: () => ({clear: () => {writes++; const request = {}; setImmediate(() => request.onsuccess()); return request;}})}),
      close: () => {}
    })});
  vm.runInContext(functionSource('mapArchiveOperation'), sandbox);
  await vm.runInContext("mapArchiveOperation('readwrite', store => store.clear())", sandbox);
  assert.equal(writes, 1);
});


test('delete-all remains available when only classified journeys survive an old partial reset', () => {
  const saved = storage();
  const sandbox = vm.createContext({localStorage: saved, hasSavedLocalProgress: () => false,
    persistedAchievements: new Map(), undeterminedJourneys: [], classifiedJourneys: [{id: 'flight'}],
    persistedMapJourneys: new Map(), persistedFootActivities: new Map(), persistedCoverageByRef: new Map(),
    persistedARoadCoverageByRef: new Map(), pendingRoadImportCandidates: () => [],
    LOCAL_PROGRESS_KEY: 'uk-road-tracker-progress-v1', UNDETERMINED_JOURNEYS_KEY: 'roadprints:undetermined-journeys-v1',
    CLASSIFIED_JOURNEYS_KEY: 'roadprints:classified-journeys-v1', IMPORT_COORDINATOR_SESSION_KEY: 'roadprints-import-coordinator-session-v1',
    clearRoadData: {}, clearFootData: {}, clearImportedData: {}});
  vm.runInContext(functionSource('hasDeletableLocalData') + '\n' + functionSource('updateDataDeletionControls'), sandbox);
  vm.runInContext('updateDataDeletionControls()', sandbox);
  assert.equal(sandbox.clearImportedData.disabled, false);
  assert.equal(sandbox.clearRoadData.disabled, true);
  assert.equal(sandbox.clearFootData.disabled, true);
});
