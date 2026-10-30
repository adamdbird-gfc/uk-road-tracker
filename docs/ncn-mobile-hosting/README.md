# Mobile HTTPS NCN diagnostic viewer — 30 October 2026

**Live:** https://roadprints-ncn-diagnostics.littlearia.chatgpt.site/

On Android, tap that link, choose **Open in Chrome** if ChatGPT opens an in-app window, and optionally use Chrome's **Add to Home screen**. No ZIP extraction, file/HTML viewer or login is required. Select a case and source section, then use **Locate selection**, **40 m close-up**, +/−, drag to pan and the layer checkboxes. Map buttons and layer labels have at least 44 px tap areas. **Download offline copy** retains the exact tested #249 standalone file.

## Hosting decision

The existing repository has GitHub Pages enabled and its root URL serves the retired web-prototype notice. There is no gh-pages branch or tracked Pages deployment workflow. Public Pages administration metadata is unavailable (404); its exact configured source is unverified. The current GitHub connector exposes no Pages configuration/deploy mutation. No Pages settings, retired entrypoint, existing legacy reference assets or main commit have been changed.

To meet the requested directly accessible HTTPS/no-login outcome without merging #249 or waiting for a main/Pages change, this public static-only copy uses the available Sites host. This is a separate deployment, not Firebase or Render and not an Android release. No new commercial mapping service, tile provider or runtime backend is used. The original offline downloadable viewer remains available; `site/offline.html` is byte-identical to the delivered #249 artifact.

## Exact source and publication

- Roadprints development baseline: main `a87d60d4d5b2c4651c571e7f4316e514154734eb`.
- Public viewer source: PR #249 head `e274261bf4b60219ba473f4d8f0c78ef4ffb4154`; offline file SHA-256 `b2da01d979afa696ca9affedef721cbb96cdc932c6128c2a591708e54e0bbba7`.
- Hosted copy: unchanged embedded public evidence and JavaScript; CSS adds larger controls and a same-origin offline-download link. Committed alongside this record under `site/`.
- Sites project: `appgprj_6ae51ddd58908191ac680599ab5e11dc`.
- Hosting source commit: `721c0d1daadbafe8df8262704995c79f6499d57f`.
- Saved version: 1, `appgprj_6ae51ddd58908191ac680599ab5e11dc~appgver_4f70799ae1248191a595cee5d0251cd2`.
- Deployment: `appgdep_6ae51e26b634819194f570497331278e`, succeeded; public access explicitly enabled for the requested no-login viewer.
- Static deployment archive SHA-256: `21dd5a342fbfcb3e4034e9c82a5b3a31d53e641e466dbd0e7089ac73c5a2d51e`.

## Privacy, data and validation

Only the already-public pinned NCN geometry/diagnostic payload is present. No private journeys, GPS captures, incident exports, home coordinates, account tokens, uploads or APIs are included. The page has no external asset requests and blocks connection attempts with its content security policy. No Firebase Hosting bytes or national references are fetched. NCN admission, distances, completion calculations and the demonstrated 0 m additional eligible recovery are unchanged.

The native deployment result confirms success and public access. The live HTTPS page was opened in the browser without signing in, source selection and 40 m close-up were exercised, and a corridor checkbox visibly changed state. The original offline viewer had 78 Chromium browser checks; this hosting task adds four contract/privacy/fallback checks:

```sh
python docs/ncn-mobile-hosting/test_mobile.py -q
```

Physical Android touch handling and the user's Chrome session remain owner acceptance checks; this is not a real-phone acceptance claim. Hosting rollback uses the Sites saved version/access controls. Do not change main's retired Pages root to republish the old prototype. #249 and the capture fix remain separate, unmerged PRs.
