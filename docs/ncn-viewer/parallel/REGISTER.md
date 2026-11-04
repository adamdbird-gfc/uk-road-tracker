# Durable machine-readable register

The complete register is in [Roadprints_NCN_Corridor_Evidence.zip](https://chatgpt.com/library?file_id=libfile_8fa6e75900dc8191962d677a9e4def61&account_id=personal). It contains plain CSV and JSON plus the map viewer, policy, report, source observations and validation. The user-facing [standalone map viewer](https://chatgpt.com/library?file_id=libfile_540f079e5b188191867406d4e9e903e2&account_id=personal) is also preserved separately. These links require the owner's ChatGPT account.

Large generated registers are retained in that evidence artifact rather than copied into Git. To run the committed tests or regenerate the viewer, extract national-candidates.json from the pack and gzip it (mtime=0) to results/national-candidates.json.gz. The CSV is optional for viewer/test execution. Scripts and compact summary are preserved in this repository.

Compressed artifact SHA-256 pins from the validated local run:

```json
{
  "national-candidates.json.gz": "41c89d0b5248fd435774df7afdd189df4e061e8ff2a48d3cf4c9914828d09dd5",
  "national-candidates.csv.gz": "b91a36bd9d6b2c8185335a64c3627101ae3a15845723fca2ccee7b98f188131e",
  "national-summary.json": "42c31daa1123481c50f5c2671d2c189a3c2ea2c32df1245920476fed69b20517"
}
```
