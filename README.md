# YouTube Interest Analyzer — Linear Space with Exponential Temporal Decay

Code and data backing:

The system builds a per-user interest profile over YouTube's content categories
from a liked-video history, weighting each like by an exponential decay in its
age. The paper asks one question — does the decay actually improve prediction
of future likes, against an explicit no-decay control — and everything in this
repository exists to make that question checkable from raw script output
rather than from transcribed numbers.

This README documents how to reproduce every table and figure in the paper.
It does not repeat the paper's methodology; see the paper (or `docs/` if a
preprint is included) for that.

## Repository layout

```
.
├── data/                        four real accounts (see "Data" below)
│   ├── user_videos_u1.json
│   ├── user_videos_u2.json
│   ├── user_videos_u3.json
│   └── user_videos_u7.json
├── PythonSystem/                 analysis pipeline (Python)
│   ├── pipeline_common.py        single source of truth: cleaning, dedup, weight schemes
│   ├── chrono_split.py           Experiment 1 — rolling-origin decay evaluation
│   ├── evaluate_v2.py            Experiment 2 — clustering-component audit
│   ├── clusters.py               keyword extraction pipeline (LaBSE + HDBSCAN/DBSCAN)
│   ├── sweeps.py                 clustering (eps) sweep and N_eff (lambda) sweep
│   ├── decay_diagnostics.py      per-category N_eff / concentration diagnostic
│   └── make_figure1.py           renders Figure 1 from chrono_split.py's output
├── src/main/java/                 Java client: YouTube Data API, profile, recommendations
│   ├── YouTubeAuth.java
│   ├── YouTubeDataLoader.java
│   ├── JSON_Reader.java
│   ├── CategoryRegistry.java
│   ├── User.java
│   ├── MyVector.java
│   ├── Event.java
│   └── Main.java
├── logs/                         raw console output backing every table (see mapping below)
├── figure1.png                   Figure 1 as it appears in the paper
├── pom.xml                       Maven build for the Java client
└── README.md
```

## Data

Four personal accounts, `u1`, `u2`, `u3`, `u7`, exported directly through the
Java client (`YouTubeDataLoader.java`) against the account holders' own
liked-video history, with no synthetic or imputed fields. `u1` (9 videos) and
`u7` (16 videos) are automatically excluded from both experiments by fixed
thresholds inside the scripts (fewer than 30 usable videos; likes on fewer
than 20 distinct dates) — see Section 4 of the paper for exactly which
threshold excludes which account and why. Only `u2` and `u3` carry the scale
and temporal density either experiment needs.

Each `user_videos_uN.json` is a flat array of objects:

```json
{
  "video_id": "...", "title": "...", "category_id": "...", "category": "...",
  "liked_at": "YYYY-MM-DD", "duration_seconds": 0, "weight": 0
}
```

The `weight` field is what the Java client computed under its historical
`ORIGINAL` scheme at export time; every analysis in the paper **recomputes**
the weight from `duration_seconds` under the scheme it is testing
(`pipeline_common.WEIGHT_SCHEMES`), so `weight` in the raw file is not used
directly by any script and can be ignored.

## Setup

**Python 3.10+**

```bash
pip install "numpy>=1.24" "scipy>=1.10" "scikit-learn>=1.3" matplotlib \
            sentence-transformers
```

`scikit-learn>=1.3` is required for `HDBSCAN`. `sentence-transformers` is only
needed for `clusters.py` and for `evaluate_v2.py --embedder labse` /
`sweeps.py --mode clustering --embedder labse`; it downloads
`sentence-transformers/LaBSE` from Hugging Face on first use, so those two
steps need network access and take noticeably longer than the rest. Every
other script runs offline.

**Java 17+ and Maven** (only needed to rebuild the data-collection client,
not to reproduce the paper's numbers from the already-exported data):

```bash
mvn -f pom.xml package
```

## Reproducing the paper end to end

Run from `PythonSystem/`. All commands below use `zsh`/`bash` array syntax;
if your shell splits `$U` into one string instead of four arguments (this
happens in `zsh` if you forget the parentheses), define the array with
`U=(...)`, not `U="..."`.

```bash
cd PythonSystem
U=(../data/user_videos_u1.json ../data/user_videos_u2.json \
   ../data/user_videos_u3.json ../data/user_videos_u7.json)
A=(../data/user_videos_u2.json ../data/user_videos_u3.json)   # the two that qualify
mkdir -p ../logs

# 1. Component audit — Tables 6, 7 (Section 6)
python3 evaluate_v2.py --embedder labse --algorithm dbscan --eps 0.51 \
        --dedup-key title --seeds 20 --users "${A[@]}" \
        > ../logs/01_evaluate.txt

# 2. Clustering eps sweep, DBSCAN vs HDBSCAN — Section 7.1
python3 sweeps.py --mode clustering --embedder labse --verbose \
        --users "${A[@]}" > ../logs/02_clustering_sweep.txt

# 3. N_eff lambda sweep — Section 7.3
python3 sweeps.py --mode lambda --users "${U[@]}" \
        --reference-date 2026-08-26 > ../logs/03_lambda_sweep.txt

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

# 6. Robustness: weight scheme x truncation age — Table 4
for ws in original continuous_log uniform; do
  for W in 0 360; do
    echo "########## weight_scheme=$ws  W=$W ##########"
    python3 chrono_split.py --users "${U[@]}" --weight-scheme "$ws" \
            --max-age-days "$W" --out /tmp/abl_${ws}_${W}.json
    echo
  done
done > ../logs/07_weight_truncation_ablation.txt
```

Total runtime is dominated by step 1 (LaBSE encoding of u3's 4299 titles,
20 seeds) — a few minutes on a laptop CPU. Everything else finishes in
seconds.

## What backs what

| Log | Paper | Command above |
|---|---|---|
| `01_evaluate.txt` | Tables 6–7, Section 6 | 1 |
| `02_clustering_sweep.txt` | Section 7.1 | 2 |
| `03_lambda_sweep.txt` | Section 7.3 | 3 |
| `04_weight_ablation.txt` | Section 7.2 | 4 |
| `05_chrono_split.txt` | Tables 2–3, Figure 1, Sections 5.1–5.6 | 5 |
| `07_weight_truncation_ablation.txt` | Table 4 | 6 |

Every number quoted in the paper's text or tables is copy-checkable against
one of these six files; none is transcribed by hand from an intermediate
notebook.

## Notes on reproducing exactly

- **`--dedup-key title`** in step 1 reproduces the audit as run for the paper
  (the released pipeline default is `--dedup-key video_id`; Section 7.4
  quantifies the difference, which is under 2% of one account).
- **`--algorithm dbscan --eps 0.51`** likewise reproduces the audit
  configuration; the pipeline default is `--algorithm hdbscan` (Section 7.1
  explains the switch). Re-running step 1 with the defaults will not
  reproduce Table 6 exactly — this is expected and stated in the paper.
- The chronological experiment (`chrono_split.py`) always uses
  `--dedup-key video_id` and is not affected by either switch above.
- `sweeps.py --mode lambda` in step 3 intentionally includes `u1` and `u7`
  even though `chrono_split.py` excludes them — the sweep is a diagnostic on
  whatever data it is given, not a predictive experiment, and Section 7.3
  reports that it is *not* monotone on those two accounts, which is itself a
  finding.

## Known limitations of this codebase

See the paper's Limitations section for the substantive ones (sample size,
functional form, class imbalance, etc.). Two purely engineering notes:

- The clustering audit (`evaluate_v2.py`) and the decay experiment
  (`chrono_split.py`) use different deduplication conventions and different
  clustering algorithms by design (see above); this is documented, not a bug.
- `sentence-transformers/LaBSE` is downloaded from Hugging Face on first
  run of any LaBSE-backed command; sandboxed or air-gapped environments will
  need to pre-fetch the model or vendor it locally.

## License and data provenance

All liked-video histories in `data/` were exported with the account holders'
knowledge and consent; no third-party account data was accessed. See the
paper's Declarations section for the full statement.
