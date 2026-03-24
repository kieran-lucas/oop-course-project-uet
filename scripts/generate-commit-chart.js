// scripts/generate-commit-chart.js
const fs = require("fs");

async function getFetch() {
  const { default: fetch } = await import("node-fetch");
  return fetch;
}

const token = process.env.GITHUB_TOKEN;
if (!token) {
  console.error("❌ Vui lòng đặt biến môi trường GITHUB_TOKEN trước khi chạy script.");
  process.exit(1);
}

const REPO = "kieran-lucas/oop-course-project-uet";
const CONCURRENCY = 6;

// ─── FETCH ───────────────────────────────────────────────────────────────────

async function fetchAllCommits(fetch) {
  let page = 1, all = [];
  while (true) {
    console.log(`📦 Fetching commits page ${page}...`);
    const res = await fetch(
      `https://api.github.com/repos/${REPO}/commits?per_page=100&page=${page}`,
      { headers: { Authorization: `token ${token}`, "User-Agent": "kny-commit-chart" } }
    );
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${await res.text()}`);
    const data = await res.json();
    if (!data.length) break;
    all = all.concat(data);
    page++;
  }
  console.log(`✅ Total commits: ${all.length}`);
  return all;
}

async function fetchCommitStats(fetch, sha) {
  try {
    const res = await fetch(
      `https://api.github.com/repos/${REPO}/commits/${sha}`,
      { headers: { Authorization: `token ${token}`, "User-Agent": "kny-commit-chart" } }
    );
    if (!res.ok) return { additions: 0, deletions: 0 };
    const data = await res.json();
    return { additions: data.stats?.additions || 0, deletions: data.stats?.deletions || 0 };
  } catch {
    return { additions: 0, deletions: 0 };
  }
}

async function batchFetchStats(fetch, commits) {
  const results = new Array(commits.length);
  for (let i = 0; i < commits.length; i += CONCURRENCY) {
    const batch = commits.slice(i, i + CONCURRENCY);
    process.stdout.write(`\r🔍 Stats ${i + 1}–${Math.min(i + CONCURRENCY, commits.length)} / ${commits.length}   `);
    const stats = await Promise.all(batch.map(c => fetchCommitStats(fetch, c.sha)));
    stats.forEach((s, j) => (results[i + j] = s));
    if (i + CONCURRENCY < commits.length) await new Promise(r => setTimeout(r, 180));
  }
  console.log("\n✅ Stats fetched.");
  return results;
}

// ─── DATA ────────────────────────────────────────────────────────────────────

function groupByDay(commits, statsArr) {
  const map = {};
  commits.forEach((c, i) => {
    const day = c.commit.author.date.slice(0, 10);
    if (!map[day]) map[day] = { commits: 0, additions: 0, deletions: 0 };
    map[day].commits++;
    map[day].additions += statsArr[i].additions;
    map[day].deletions += statsArr[i].deletions;
  });
  return map;
}

// ─── SVG UTILS ───────────────────────────────────────────────────────────────

function catmullRomToBezier(points) {
  if (points.length === 0) return "";
  if (points.length === 1) return `M ${points[0].x} ${points[0].y}`;
  let d = `M ${points[0].x.toFixed(2)} ${points[0].y.toFixed(2)}`;
  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[Math.max(i - 1, 0)];
    const p1 = points[i];
    const p2 = points[i + 1];
    const p3 = points[Math.min(i + 2, points.length - 1)];
    const cp1x = p1.x + (p2.x - p0.x) / 6;
    const cp1y = p1.y + (p2.y - p0.y) / 6;
    const cp2x = p2.x - (p3.x - p1.x) / 6;
    const cp2y = p2.y - (p3.y - p1.y) / 6;
    d += ` C ${cp1x.toFixed(2)} ${cp1y.toFixed(2)}, ${cp2x.toFixed(2)} ${cp2y.toFixed(2)}, ${p2.x.toFixed(2)} ${p2.y.toFixed(2)}`;
  }
  return d;
}

function formatLabel(dateStr) {
  const d = new Date(dateStr + "T00:00:00");
  return `${d.getDate()} ${["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"][d.getMonth()]}`;
}

function fmtK(v) {
  return v >= 1000 ? `${(v / 1000).toFixed(1)}k` : String(v);
}

// ─── SVG GENERATION ──────────────────────────────────────────────────────────

function generateSVG(dayData) {
  const days    = Object.keys(dayData).sort();
  const n       = days.length;
  if (n === 0) { console.error("No data"); return ""; }

  /* Series arrays */
  let cumulative = 0;
  const totalLines   = [];   // cumulative additions – deletions
  const commitsArr   = [];
  const addArr       = [];
  const delArr       = [];
  const changesArr   = [];   // (adds + dels) / commits

  days.forEach(day => {
    const d = dayData[day];
    cumulative = Math.max(0, cumulative + d.additions - d.deletions);
    totalLines.push(cumulative);
    commitsArr.push(d.commits);
    addArr.push(d.additions);
    delArr.push(d.deletions);
    changesArr.push(d.commits > 0 ? Math.round((d.additions + d.deletions) / d.commits) : 0);
  });

  /* Layout */
  const ML = 72, MR = 55, MT = 100, MB = 95;
  const innerW = Math.max(920, n * 52);
  const W      = innerW + ML + MR;
  const H      = 520;
  const innerH = H - MT - MB;

  /* Scale helpers */
  const maxLines   = Math.max(...totalLines,  1);
  const maxCommits = Math.max(...commitsArr,  1);
  const maxChanges = Math.max(...changesArr,  1);
  const maxBar     = Math.max(...addArr.map((a, i) => a + delArr[i]), 1);

  const xOf  = i  => ML + i * (innerW / Math.max(n - 1, 1));
  const yOf  = (v, max) => MT + innerH - (v / max) * innerH;

  /* Points */
  const ptLines   = days.map((_, i) => ({ x: xOf(i), y: yOf(totalLines[i],  maxLines)   }));
  const ptCommits = days.map((_, i) => ({ x: xOf(i), y: yOf(commitsArr[i],  maxCommits) }));
  const ptChanges = days.map((_, i) => ({ x: xOf(i), y: yOf(changesArr[i],  maxChanges) }));

  /* Paths */
  const pathLines   = catmullRomToBezier(ptLines);
  const pathCommits = catmullRomToBezier(ptCommits);
  const pathChanges = catmullRomToBezier(ptChanges);
  const areaLines   = `${pathLines} L ${ptLines[n-1].x.toFixed(2)} ${MT + innerH} L ${ptLines[0].x.toFixed(2)} ${MT + innerH} Z`;

  /* Bar width */
  const bw = Math.max(3, Math.min(18, (innerW / n) * 0.55));

  /* ── Bars (additions / deletions) ── */
  let bars = "";
  days.forEach((_, i) => {
    const total = addArr[i] + delArr[i];
    if (!total) return;
    const fullH = (total / maxBar) * innerH * 0.78;
    const addH  = (addArr[i] / total) * fullH;
    const delH  = fullH - addH;
    const bx    = xOf(i) - bw / 2;
    const by    = MT + innerH - fullH;
    // deletions on top (red), additions on bottom (green)
    if (delH > 0.5)
      bars += `<rect x="${bx.toFixed(1)}" y="${by.toFixed(1)}" width="${bw}" height="${delH.toFixed(1)}" fill="#ef4444" opacity="0.72" rx="1"/>`;
    if (addH > 0.5)
      bars += `<rect x="${bx.toFixed(1)}" y="${(by + delH).toFixed(1)}" width="${bw}" height="${addH.toFixed(1)}" fill="#22c55e" opacity="0.75" rx="1"/>`;
  });

  /* ── Circles ── */
  let ciCommits = "", ciChanges = "";
  ptCommits.forEach((pt, i) => {
    ciCommits += `<circle cx="${pt.x.toFixed(1)}" cy="${pt.y.toFixed(1)}" r="3.8" fill="#f97316" filter="url(#gOrange)"><title>${days[i]}: ${commitsArr[i]} commits</title></circle>`;
  });
  ptChanges.forEach((pt, i) => {
    ciChanges += `<circle cx="${pt.x.toFixed(1)}" cy="${pt.y.toFixed(1)}" r="3.2" fill="#60a5fa" filter="url(#gBlue)"><title>${days[i]}: ${changesArr[i]} changes/commit</title></circle>`;
  });

  /* ── Y-axis grid (left – total lines scale) ── */
  let yGrid = "", yLabelsRight = "";
  const gridN = 5;
  for (let i = 0; i <= gridN; i++) {
    const y   = MT + (i / gridN) * innerH;
    const val = Math.round(((gridN - i) / gridN) * maxLines);
    yGrid += `<line x1="${ML}" y1="${y.toFixed(1)}" x2="${ML + innerW}" y2="${y.toFixed(1)}"
      stroke="#1e3a8a" stroke-width="${i === gridN ? 1 : 0.5}"
      stroke-opacity="${i === gridN ? 0.7 : 0.3}"
      stroke-dasharray="${i === gridN ? "none" : "6,5"}"/>`;
    if (i < gridN)
      yGrid += `<text x="${ML - 8}" y="${(y + 4).toFixed(1)}" font-size="10" fill="#3b82f6"
        text-anchor="end" font-family="Lexend, sans-serif">${fmtK(val)}</text>`;
  }
  // right axis – commits scale
  for (let i = 0; i < gridN; i++) {
    const y   = MT + (i / gridN) * innerH;
    const val = Math.round(((gridN - i) / gridN) * maxCommits);
    yLabelsRight += `<text x="${ML + innerW + 8}" y="${(y + 4).toFixed(1)}" font-size="10" fill="#f97316"
      font-family="Lexend, sans-serif" opacity="0.75">${val}</text>`;
  }

  /* ── X-axis labels ── */
  let xLabels = "";
  const xStep = Math.ceil(n / 18);
  days.forEach((day, i) => {
    if (i % xStep !== 0 && i !== n - 1) return;
    const x = xOf(i);
    xLabels += `<text x="${x.toFixed(1)}" y="${MT + innerH + 18}"
      font-size="9.5" fill="#3b82f6" text-anchor="middle"
      font-family="Lexend, sans-serif" font-weight="300"
      transform="rotate(-42, ${x.toFixed(1)}, ${MT + innerH + 18})">${formatLabel(day)}</text>`;
  });

  /* ── Totals ── */
  const totalCommits   = commitsArr.reduce((a, b) => a + b, 0);
  const totalAdditions = addArr.reduce((a, b) => a + b, 0);
  const totalDeletions = delArr.reduce((a, b) => a + b, 0);

  /* ── Legend (top, matches image structure) ── */
  // Items: Total lines | Commits | Changes per commit | Additions | Deletions
  const legendY = 78;
  const lItems = [
    { type: "area",  color: "#94a3b8", label: "Total lines"         },
    { type: "line",  color: "#f97316", label: "Commits"             },
    { type: "line",  color: "#60a5fa", label: "Changes per commit"  },
    { type: "rect",  color: "#22c55e", label: "Additions"           },
    { type: "rect",  color: "#ef4444", label: "Deletions"           },
  ];
  // measure approximate total width to center
  const charW    = 7.2;
  const spacing  = 28;
  const itemW    = i => lItems[i].label.length * charW + spacing + 22;
  const totalLW  = lItems.reduce((a, _, i) => a + itemW(i), 0);
  let lx         = (W - totalLW) / 2;
  let legendSVG  = "";
  lItems.forEach(item => {
    if (item.type === "area") {
      legendSVG += `<rect x="${lx.toFixed(1)}" y="${legendY - 9}" width="22" height="11" rx="2" fill="${item.color}" opacity="0.35" stroke="${item.color}" stroke-width="0.8"/>`;
      lx += 26;
    } else if (item.type === "line") {
      legendSVG += `<line x1="${lx.toFixed(1)}" y1="${legendY - 3}" x2="${(lx + 20).toFixed(1)}" y2="${legendY - 3}" stroke="${item.color}" stroke-width="2.2"/>`;
      legendSVG += `<circle cx="${(lx + 10).toFixed(1)}" cy="${legendY - 3}" r="3.5" fill="${item.color}"/>`;
      lx += 26;
    } else {
      legendSVG += `<rect x="${lx.toFixed(1)}" y="${legendY - 9}" width="12" height="11" rx="2" fill="${item.color}" opacity="0.85"/>`;
      lx += 17;
    }
    legendSVG += `<text x="${lx.toFixed(1)}" y="${legendY - 1}" font-size="11" fill="#93c5fd"
      font-family="Lexend, sans-serif" font-weight="400">${item.label}</text>`;
    lx += item.label.length * charW + spacing;
  });

  /* ── Corner brackets (KNY style) ── */
  const bLen = 18;
  const corners = [
    [ML, MT, 1, 1], [ML + innerW, MT, -1, 1],
    [ML, MT + innerH, 1, -1], [ML + innerW, MT + innerH, -1, -1]
  ].map(([cx, cy, dx, dy]) =>
    `<path d="M ${cx} ${cy + dy * bLen} L ${cx} ${cy} L ${cx + dx * bLen} ${cy}"
      fill="none" stroke="#3b82f6" stroke-width="1.8" stroke-opacity="0.8"/>`
  ).join("");

  /* ── Peak annotation ── */
  const peakI    = commitsArr.indexOf(Math.max(...commitsArr));
  const peakPt   = ptCommits[peakI];
  const peakAnnot = `
    <line x1="${peakPt.x.toFixed(1)}" y1="${(peakPt.y - 14).toFixed(1)}"
          x2="${peakPt.x.toFixed(1)}" y2="${(peakPt.y - 38).toFixed(1)}"
          stroke="#60a5fa" stroke-width="1" stroke-dasharray="3,3" opacity="0.7"/>
    <rect x="${(peakPt.x - 44).toFixed(1)}" y="${(peakPt.y - 56).toFixed(1)}"
          width="88" height="20" rx="4"
          fill="#020b1a" stroke="#3b82f6" stroke-width="0.8" opacity="0.92"/>
    <text x="${peakPt.x.toFixed(1)}" y="${(peakPt.y - 42).toFixed(1)}"
          font-size="10" fill="#93c5fd" text-anchor="middle"
          font-family="Lexend, sans-serif">⬡ peak: ${commitsArr[peakI]} commits</text>`;

  /* ── KNY Diamond grid pattern (subtle) ── */
  /* ── Water-wave bottom decoration ── */
  const waves = Array.from({length: 3}, (_, k) => {
    const yo  = H - 10 - k * 5;
    const amp = 6 - k * 1.5;
    const seg = W / 6;
    let d = `M 0 ${yo}`;
    for (let s = 0; s < 6; s++)
      d += ` Q ${(s * seg + seg * 0.5).toFixed(1)} ${(yo - amp).toFixed(1)} ${((s + 1) * seg).toFixed(1)} ${yo}`;
    return `<path d="${d}" fill="none" stroke="#1d4ed8" stroke-width="${1 - k * 0.25}" stroke-opacity="${0.28 - k * 0.06}"/>`;
  }).join("\n");

  /* ── Stats badge (top-right) ── */
  const bx = W - MR - 102, by2 = 10;
  const badge = `
    <rect x="${bx}" y="${by2}" width="98" height="62" rx="6"
          fill="#050e24" fill-opacity="0.85" stroke="#1d4ed8" stroke-width="0.9"/>
    <text x="${bx + 49}" y="${by2 + 16}" font-size="8.5" fill="#3b82f6"
          text-anchor="middle" font-family="Lexend, sans-serif" letter-spacing="1.5">TOTAL COMMITS</text>
    <text x="${bx + 49}" y="${by2 + 36}" font-size="22" font-weight="600" fill="#93c5fd"
          text-anchor="middle" font-family="Lexend, sans-serif" filter="url(#gTitle)">${totalCommits}</text>
    <text x="${bx + 49}" y="${by2 + 52}" font-size="8.5" fill="#86efac"
          text-anchor="middle" font-family="Lexend, sans-serif">+${fmtK(totalAdditions)}</text>
    <text x="${bx + 49}" y="${by2 + 52}" font-size="8.5" fill="#fca5a5"
          text-anchor="middle" font-family="Lexend, sans-serif" dx="36">-${fmtK(totalDeletions)}</text>`;

  /* ════════════════════ FULL SVG ════════════════════ */
  return `<svg width="${W}" height="${H}" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}">
<defs>
  <style>
    @import url('https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;600;700&amp;display=swap');
  </style>

  <!-- Backgrounds -->
  <linearGradient id="bg" x1="0%" y1="0%" x2="60%" y2="100%">
    <stop offset="0%"   stop-color="#020b1a"/>
    <stop offset="45%"  stop-color="#030e22"/>
    <stop offset="100%" stop-color="#010918"/>
  </linearGradient>

  <!-- Area fill (total lines) -->
  <linearGradient id="areaFill" x1="0%" y1="0%" x2="0%" y2="100%">
    <stop offset="0%"   stop-color="#475569" stop-opacity="0.5"/>
    <stop offset="60%"  stop-color="#334155" stop-opacity="0.15"/>
    <stop offset="100%" stop-color="#1e293b" stop-opacity="0"/>
  </linearGradient>

  <!-- Commits line gradient -->
  <linearGradient id="gCommits" x1="0%" y1="0%" x2="100%" y2="0%">
    <stop offset="0%"   stop-color="#fb923c"/>
    <stop offset="100%" stop-color="#f97316"/>
  </linearGradient>

  <!-- Changes line gradient -->
  <linearGradient id="gChanges" x1="0%" y1="0%" x2="100%" y2="0%">
    <stop offset="0%"   stop-color="#2563eb"/>
    <stop offset="50%"  stop-color="#3b82f6"/>
    <stop offset="100%" stop-color="#93c5fd"/>
  </linearGradient>

  <!-- Border gradient -->
  <linearGradient id="gBorder" x1="0%" y1="0%" x2="100%" y2="100%">
    <stop offset="0%"   stop-color="#1d4ed8" stop-opacity="0.9"/>
    <stop offset="50%"  stop-color="#3b82f6" stop-opacity="0.55"/>
    <stop offset="100%" stop-color="#60a5fa" stop-opacity="0.9"/>
  </linearGradient>

  <!-- Header fill -->
  <linearGradient id="gHeader" x1="0%" y1="0%" x2="100%" y2="0%">
    <stop offset="0%"   stop-color="#0c1e4a" stop-opacity="0"/>
    <stop offset="25%"  stop-color="#0f2257" stop-opacity="0.85"/>
    <stop offset="75%"  stop-color="#0f2257" stop-opacity="0.85"/>
    <stop offset="100%" stop-color="#0c1e4a" stop-opacity="0"/>
  </linearGradient>

  <!-- Filters -->
  <filter id="gOrange" x="-80%" y="-80%" width="260%" height="260%">
    <feGaussianBlur stdDeviation="3" result="b"/>
    <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
  <filter id="gBlue" x="-80%" y="-80%" width="260%" height="260%">
    <feGaussianBlur stdDeviation="2.5" result="b"/>
    <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
  <filter id="gTitle">
    <feGaussianBlur stdDeviation="3" result="b"/>
    <feMerge><feMergeNode in="b"/><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
  <filter id="gLine" x="-5%" y="-80%" width="110%" height="260%">
    <feGaussianBlur stdDeviation="4" result="b"/>
    <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>

  <!-- KNY diamond tile pattern -->
  <pattern id="knyTile" x="0" y="0" width="24" height="24" patternUnits="userSpaceOnUse">
    <path d="M12 1 L23 12 L12 23 L1 12 Z"
          fill="none" stroke="#1e3a8a" stroke-width="0.35" stroke-opacity="0.22"/>
  </pattern>

  <!-- Clip -->
  <clipPath id="chartClip">
    <rect x="${ML}" y="${MT}" width="${innerW}" height="${innerH}"/>
  </clipPath>
</defs>

<!-- ── Background ── -->
<rect width="${W}" height="${H}" fill="url(#bg)" rx="14"/>
<rect width="${W}" height="${H}" fill="url(#knyTile)" rx="14" opacity="0.7"/>

<!-- ── Outer border ── -->
<rect width="${W}" height="${H}" fill="none" rx="14"
      stroke="url(#gBorder)" stroke-width="1.6"/>
<!-- inner accent line -->
<rect x="5" y="5" width="${W - 10}" height="${H - 10}" fill="none" rx="11"
      stroke="#1d4ed8" stroke-width="0.5" stroke-opacity="0.35"/>

<!-- ── Header band ── -->
<rect x="0" y="0" width="${W}" height="${MT - 4}" fill="url(#gHeader)" rx="14"/>
<line x1="${ML}" y1="${MT - 4}" x2="${ML + innerW}" y2="${MT - 4}"
      stroke="#1d4ed8" stroke-width="0.8" stroke-opacity="0.55"/>

<!-- ── Title ── -->
<text x="${(ML + innerW / 2).toFixed(1)}" y="44"
      font-size="17" font-weight="600" fill="#bfdbfe"
      text-anchor="middle" font-family="Lexend, sans-serif"
      filter="url(#gTitle)" letter-spacing="3.5">⬡  COMMIT ANALYTICS  ⬡</text>
<text x="${(ML + innerW / 2).toFixed(1)}" y="61"
      font-size="9.5" fill="#3b82f6" text-anchor="middle"
      font-family="Lexend, sans-serif" letter-spacing="3" font-weight="300">${REPO}</text>

<!-- ── Stats badge ── -->
${badge}

<!-- ── Legend ── -->
<g>${legendSVG}</g>

<!-- ── Axis labels ── -->
<text x="13" y="${(MT + innerH / 2).toFixed(1)}" font-size="9" fill="#3b82f6"
      text-anchor="middle" font-family="Lexend, sans-serif" letter-spacing="2"
      transform="rotate(-90, 13, ${(MT + innerH / 2).toFixed(1)})">LINES</text>
<text x="${W - 12}" y="${(MT + innerH / 2).toFixed(1)}" font-size="9" fill="#f97316"
      text-anchor="middle" font-family="Lexend, sans-serif" letter-spacing="2" opacity="0.75"
      transform="rotate(90, ${W - 12}, ${(MT + innerH / 2).toFixed(1)})">COMMITS</text>

<!-- ── Grid ── -->
${yGrid}
${yLabelsRight}

<!-- ── Axis lines ── -->
<line x1="${ML}" y1="${MT}" x2="${ML}" y2="${MT + innerH}"
      stroke="#1e40af" stroke-width="0.8" stroke-opacity="0.45"/>
<line x1="${ML}" y1="${MT + innerH}" x2="${ML + innerW}" y2="${MT + innerH}"
      stroke="#1e40af" stroke-width="1" stroke-opacity="0.65"/>
<line x1="${ML + innerW}" y1="${MT}" x2="${ML + innerW}" y2="${MT + innerH}"
      stroke="#f97316" stroke-width="0.8" stroke-opacity="0.3"/>

<!-- ── X Labels ── -->
${xLabels}

<!-- ── Chart (clipped) ── -->
<g clip-path="url(#chartClip)">

  <!-- Total lines area -->
  <path d="${areaLines}" fill="url(#areaFill)"/>
  <path d="${pathLines}" fill="none" stroke="#64748b" stroke-width="1.4" stroke-opacity="0.65"/>

  <!-- Bars (deletions top / additions bottom) -->
  ${bars}

  <!-- Commits line -->
  <path d="${pathCommits}" fill="none" stroke="#f97316"
        stroke-width="6" stroke-opacity="0.12" filter="url(#gLine)"/>
  <path d="${pathCommits}" fill="none" stroke="url(#gCommits)"
        stroke-width="2.2" stroke-linecap="round"/>

  <!-- Changes per commit line -->
  <path d="${pathChanges}" fill="none" stroke="#3b82f6"
        stroke-width="6" stroke-opacity="0.12" filter="url(#gLine)"/>
  <path d="${pathChanges}" fill="none" stroke="url(#gChanges)"
        stroke-width="2.2" stroke-linecap="round"/>

</g>

<!-- ── Circles (on top of clip) ── -->
${ciCommits}
${ciChanges}

<!-- ── Peak annotation ── -->
${peakAnnot}

<!-- ── Corner brackets (KNY style) ── -->
${corners}

<!-- ── Wave decoration ── -->
${waves}

</svg>`;
}

// ─── MAIN ─────────────────────────────────────────────────────────────────────

(async () => {
  try {
    console.log("⬡ Starting KNY-style commit chart...");
    const fetch   = await getFetch();
    const commits = await fetchAllCommits(fetch);
    console.log(`📊 Fetching per-commit stats (${commits.length} commits, ${CONCURRENCY} concurrent)...`);
    const stats   = await batchFetchStats(fetch, commits);
    const dayData = groupByDay(commits, stats);
    const svg     = generateSVG(dayData);
    fs.writeFileSync("scripts/commit-chart.svg", svg);
    console.log("✅ scripts/commit-chart.svg generated — KNY anime style ⬡");
  } catch (err) {
    console.error("❌ Error:", err);
    process.exit(1);
  }
})();