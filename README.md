# YouTube Interest Analyzer — Linear Space with Exponential Temporal Decay

Code and data backing:

> Kalinkin V. A., *Metric-Dependent Effects of Exponential Temporal Decay in a Single-User
> YouTube Interest Model: A Rolling-Origin Study with a No-Decay Control.*

The system builds a per-user interest profile over YouTube's content categories from a
liked-video history, weighting each like by an exponential decay in its age. The paper asks one
question — does the decay actually improve prediction of future likes, against an explicit
no-decay control — and everything in this repository exists to make that question checkable from
raw script output rather than from transcribed numbers.

This README documents how to reproduce every table and figure in the paper. It does not repeat
the paper's methodology; see the paper for that.

## Repository layout

```
.
├── data/                              four real accounts, plus derived artifacts
│   ├── user_videos_u{1,2,3,7}.json    exported histories (the only inputs)
│   ├── clusters_result_u{1,2,3,7}.json   \
│   ├── recommendations_u{1,2,3,7}.json    > written by the Java client, not used by the paper
│   └── run_manifest_u{1,2,3,7}.json      /
├── PythonSystem/                      analysis pipeline (Python)
│   ├── pipeline_common.py             single source of truth: cleaning, dedup, weight schemes
│   ├── chrono_split.py                Experiment 1 — rolling-origin decay evaluation
│   ├── window_control.py              Section 5.6 — decay vs. hard truncation window
│   ├── tie_sensitivity.py             Section 5.4 — sensitivity to the tie-breaking seed
│   ├── sign_flip_test.py              Section 5.4 — sign-flip randomisation test
│   ├── stratified_bootstrap_check.py  Section 5.4 — within-account block bootstrap
│   ├── evaluate_v2.py                 Experiment 2 — clustering-component audit
│   ├── clusters.py                    keyword extraction (LaBSE + HDBSCAN/DBSCAN)
│   ├── sweeps.py                      clustering (eps) sweep and N_eff (lambda) sweep
│   ├── decay_diagnostics.py           per-category N_eff / concentration diagnostic
│   └── make_figure1.py                renders Figure 1 from chrono_split.py's output
├── src/main/java/                     Java client: YouTube Data API, profile, recommendations
│   ├── YouTubeAuth.java  YouTubeDataLoader.java  JSON_Reader.java  CategoryRegistry.java
│   └── User.java  MyVector.java  Event.java  Main.java
├── logs/                              raw console output backing every table (mapping below)
├── figure1.png                        Figure 1 as it appears in the paper
├── pom.xml                            Maven build for the Java client
├── LICENSE
└── README.md
```

The Java sources carry no `package` declaration and therefore sit directly under
`src/main/java/`.

## Data

Four personal accounts — u1, u2, u3, u7 — exported directly through the Java client
(`YouTubeDataLoader.java`) against the account holders' own liked-video history, with no
synthetic or imputed fields. u1 (9 videos) and u7 (16 videos) are excluded from both experiments
by fixed thresholds inside the scripts: fewer than 30 usable videos (`evaluate_v2.py
--min-videos`) or likes on fewer than 20 distinct dates (`chrono_split.py
--min-distinct-dates`). Section 4 of the paper states which threshold excludes which account and
why. Only u2 and u3 carry the scale and temporal density either experiment needs; the commands
below pass all four accounts wherever the script can do the exclusion itself, so that the
`[skip]` lines appear in the logs.

Each `user_videos_uN.json` is a flat array of objects:

```json
{
  "video_id": "...", "title": "...", "category_id": "...", "category": "...",
  "liked_at": "YYYY-MM-DD", "duration_seconds": 0, "weight": 0
}
```

`duration_seconds` and `weight` are integers, the rest are strings. The `weight` field is what
the Java client computed under its historical `ORIGINAL` scheme at export time. Every analysis in
the paper recomputes the weight from `duration_seconds` under the scheme it is testing
(`pipeline_common.WEIGHT_SCHEMES`), so `weight` in the raw file is read by no script and can be
ignored.

`clusters_result_uN.json`, `recommendations_uN.json` and `run_manifest_uN.json` are outputs of a
demonstration run of the Java client (see "The Java client" below). **No table or figure in the
paper is computed from them** — Section 6 rebuilds its clusters inside `evaluate_v2.py` and
Section 7.1 inside `sweeps.py`. They are released so that the end-to-end tool can be inspected,
and each is accompanied by the `run_manifest` recording the parameters it was produced under
(`lambda = 0.95`, `max_days = 360`, `weight_scheme = ORIGINAL`, reference date 2026-08-26).

## Setup

Python 3.10+:

```
pip install "numpy>=1.24" "scipy>=1.10" "scikit-learn>=1.3" matplotlib \
            sentence-transformers
```

`scikit-learn>=1.3` is required for `HDBSCAN`. `sentence-transformers` is needed only by
`clusters.py`, `evaluate_v2.py --embedder labse` and `sweeps.py --mode clustering --embedder
labse`; it downloads `sentence-transformers/LaBSE` from Hugging Face on first use, so those steps
need network access. Every other script runs offline.

The logs in `logs/` were produced on Python 3.12 with numpy 2.4.4, scipy 1.17.1,
scikit-learn 1.8.0 and matplotlib 3.10.8. Steps 5, 6, 8, 9 and 10 below reproduce byte for byte
on that combination; the LaBSE-backed steps (1 and 2) depend on the model download and on
BLAS/threading details and should be expected to match to the printed precision, not exactly.

Java 11+ and Maven, needed only to rebuild the data-collection client, not to reproduce the
paper's numbers from the already-exported data:

```
mvn -f pom.xml package
```

## Reproducing the paper end to end

Run from `PythonSystem/`. The commands use bash/zsh array syntax; define the arrays with
`U=(...)`, not `U="..."`, or your shell will pass one argument instead of four.

```bash
cd PythonSystem
U=(../data/user_videos_u1.json ../data/user_videos_u2.json \
   ../data/user_videos_u3.json ../data/user_videos_u7.json)
A=(../data/user_videos_u2.json ../data/user_videos_u3.json)   # the two that qualify
mkdir -p ../logs
```

```bash
# 1. Component audit — Tables 6, 7 (Section 6)
python3 evaluate_v2.py --embedder labse --algorithm dbscan --eps 0.51 \
        --dedup-key title --seeds 20 --users "${U[@]}" \
        > ../logs/01_evaluate.txt
# also writes results_labse_dbscan_title.json

# 2. Clustering eps sweep, DBSCAN vs HDBSCAN — Section 7.1
python3 sweeps.py --mode clustering --embedder labse --verbose \
        --users "${A[@]}" > ../logs/02_clustering_sweep.txt
# also writes sweep_clustering.json

# 3. N_eff lambda sweep — Section 7.3
python3 sweeps.py --mode lambda --users "${U[@]}" \
        --reference-date 2026-08-26 > ../logs/03_lambda_sweep.txt
# also writes sweep_lambda.json

# 4. Weight-scheme diagnostic (profile concentration) — Section 7.2
for ws in original continuous_log uniform; do
  echo "### weight scheme: $ws ###"
  for u in 2 3; do
    echo "--- u$u / $ws ---"
    python3 decay_diagnostics.py --input ../data/user_videos_u$u.json \
            --reference-date 2026-08-26 --lambda 0.95 --max-days 360 \
            --weight-scheme "$ws" | head -26
  done
done > ../logs/04_weight_ablation.txt

# 5. Main result: rolling-origin decay evaluation — Tables 2, 3; Figure 1
python3 chrono_split.py --users "${U[@]}" --out chrono_split.json \
        > ../logs/05_chrono_split.txt
python3 make_figure1.py --input chrono_split.json --output ../figure1.png

# 6. Is decay just a smoothed window? — Table 5, Section 5.6
python3 window_control.py --users "${U[@]}" --block 2 --seeds 8 \
        > ../logs/06_window_control.txt

# 7. Robustness: weight scheme x truncation age — Table 4
for ws in original continuous_log uniform; do
  for W in 0 360; do
    echo "########## weight_scheme=$ws  W=$W ##########"
    python3 chrono_split.py --users "${U[@]}" --weight-scheme "$ws" \
            --max-age-days "$W" --out /tmp/abl_${ws}_${W}.json
    echo
  done
done > ../logs/07_weight_truncation_ablation.txt

# 8. Tie-breaking sensitivity of the confirmatory comparison — Section 5.4
python3 tie_sensitivity.py --users "${A[@]}" --seeds 40 --block 2 --focus 0.95 \
        > ../logs/08_tie_sensitivity.txt
```

Steps 9 and 10 are the two additional checks reported in Section 5.4. Both scripts resolve their
input paths relative to the current directory, so they are run from `data/`:

```bash
# 9. Within-account (stratified) block bootstrap — Section 5.4
cd ../data && python3 ../PythonSystem/stratified_bootstrap_check.py \
        > ../logs/09_stratified_bootstrap.txt

# 10. Sign-flip randomisation test — Section 5.4
python3 ../PythonSystem/sign_flip_test.py > ../logs/10_sign_flip.txt
cd ../PythonSystem
```

Runtime is dominated by step 1. `evaluate_v2.py` encodes the training titles twice and the test
titles once per seed, so 20 seeds over u2 and u3 is on the order of 2·10⁵ LaBSE encodings:
expect 20–60 minutes on a laptop CPU, longer on a cold model download. Step 2 adds a few minutes.
Steps 3–10 finish in seconds to a couple of minutes each.

## What backs what

| Log | Paper | Command |
|---|---|---|
| `01_evaluate.txt` | Tables 6–7, Sections 6.3–6.4 | 1 |
| `02_clustering_sweep.txt` | Section 7.1 | 2 |
| `03_lambda_sweep.txt` | Section 7.3 | 3 |
| `04_weight_ablation.txt` | Section 7.2 | 4 |
| `05_chrono_split.txt` | Tables 2–3, Figure 1, Sections 5.1–5.5 | 5 |
| `06_window_control.txt` | Table 5, Section 5.6 | 6 |
| `07_weight_truncation_ablation.txt` | Table 4 | 7 |
| `08_tie_sensitivity.txt` | Section 5.4 (seed range) | 8 |
| `09_stratified_bootstrap.txt` | Section 5.4 (stratified bootstrap) | 9 |
| `10_sign_flip.txt` | Section 5.4 (sign-flip test) | 10 |

Every number quoted in the paper's text or tables is copy-checkable against one of these ten
files; none is transcribed by hand from an intermediate notebook.

## The Java client

The client is the data-collection and demonstration tool, not part of the paper's evaluation. Run
it from the repository root, so that it can find `PythonSystem/clusters.py`:

```
java -cp target/RecSystem-*.jar Main --user u2 --reference-date 2026-08-26 \
     --lambda 0.95 --max-days 360 --weight ORIGINAL --top 3 --max-searches 20 --out data
```

It authenticates against the YouTube Data API, exports the liked-video history to
`data/user_videos_uN.json`, shells out to `clusters.py` with the pipeline defaults (HDBSCAN,
`--min-samples 2`, `--top-k 5`) to produce `data/clusters_result_uN.json`, generates keyword-based
recommendations into `data/recommendations_uN.json`, and records every parameter of the run in
`data/run_manifest_uN.json`. OAuth client credentials are not distributed with this repository;
supply your own and keep them out of version control.

## Notes on reproducing exactly

- `--dedup-key title` in step 1 reproduces the audit as run for the paper (the released default
  is `--dedup-key video_id`; Section 7.4 quantifies the difference, which is under 2% of one
  account). Under the title key u3 contributes 4221 items rather than the 4299 it has under
  `video_id`.
- `--algorithm dbscan --eps 0.51` likewise reproduces the audit configuration; the pipeline
  default is `--algorithm hdbscan` (Section 7.1 explains the switch). Re-running step 1 with the
  defaults will not reproduce Table 6 exactly — this is expected and stated in the paper.
- The chronological experiment (`chrono_split.py`) always uses the `video_id` key and is affected
  by neither switch above.
- `--lambda-grid` must contain `1.0`. It is the no-decay control against which every difference
  in Section 5 is computed, and `chrono_split.py` has no meaning without it.
- `sweeps.py --mode lambda` in step 3 intentionally includes u1 and u7 even though
  `chrono_split.py` excludes them — the sweep is a diagnostic on whatever data it is given, not a
  predictive experiment, and Section 7.3 reports that it is not monotone on those two accounts,
  which is itself a finding.
- `sweeps.py` writes `sweep_clustering.json` / `sweep_lambda.json` to the current directory under
  fixed names; running both modes in the same directory is safe, running the same mode twice
  overwrites.

## Known limitations of this codebase

See the paper's Limitations section for the substantive ones (sample size, functional form, class
imbalance). The engineering notes:

- The clustering audit (`evaluate_v2.py`) and the decay experiment (`chrono_split.py`) use
  different deduplication conventions and different clustering algorithms by design, as described
  above; this is documented, not a bug.
- `chrono_split.py` carries its own copy of the loader and the weight schemes rather than
  importing `pipeline_common`. The two agree on all four released accounts, and this was verified
  event by event, but they order the "missing duration" and "already seen" checks differently and
  could diverge on an input with missing `duration_seconds`.
- `evaluate_v2.py --embedder tfidf` and `--embedder char` densify the feature matrix, which costs
  about a gigabyte per transform on u3 and is much slower than the LaBSE path. The paper's
  reported configuration is `labse`.
- `sign_flip_test.py` and `stratified_bootstrap_check.py` take no arguments; the account list is
  fixed in the source, and they must be run from `data/` as shown in steps 9 and 10.
- `make_figure1.py` expects the default lambda grid; changing `--lambda-grid` in step 5 requires
  editing the `LAMS` list.
- `sentence-transformers/LaBSE` is downloaded from Hugging Face on first run of any LaBSE-backed
  command; sandboxed or air-gapped environments need to pre-fetch or vendor the model.

## License and data provenance

Code and data in this repository are released under CC BY 4.0, matching the licence of the
article. All liked-video histories in `data/` were exported with the account holders' knowledge
and consent; no third-party account data was accessed. See the paper's Declarations section for
the full statement.
