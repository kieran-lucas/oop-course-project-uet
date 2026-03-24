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

function niceTicks(maxVal, count = 5) {
  const raw = maxVal / count;
  const mag = Math.pow(10, Math.floor(Math.log10(raw)));
  const nice = [1, 2, 2.5, 5, 10].find(f => f * mag >= raw) || 10;
  const step = nice * mag;
  return Array.from({ length: count + 1 }, (_, i) => i * step).filter(v => v <= maxVal * 1.05);
}

// ─── SVG GENERATION ──────────────────────────────────────────────────────────

function generateSVG(dayData) {
  const days = Object.keys(dayData).sort();
  const n = days.length;
  if (n === 0) { console.error("No data"); return ""; }

  // ── Series ──
  let cumulative = 0;
  const totalLines = [], commitsArr = [], addArr = [], delArr = [], changesArr = [];

  days.forEach(day => {
    const d = dayData[day];
    cumulative = Math.max(0, cumulative + d.additions - d.deletions);
    totalLines.push(cumulative);
    commitsArr.push(d.commits);
    addArr.push(d.additions);
    delArr.push(d.deletions);
    changesArr.push(d.commits > 0 ? Math.round((d.additions + d.deletions) / d.commits) : 0);
  });

  // ── Layout ──
  // Generous margins to prevent any label clipping
  const ML = 82;   // left  – line count labels
  const MR = 74;   // right – commit count labels
  const MT = 124;  // top   – title + legend area
  const MB = 108;  // bottom – x-axis date labels

  const minInnerW = 900;
  const innerW = Math.max(minInnerW, n * 58);
  const W = innerW + ML + MR;
  const H = 570;
  const innerH = H - MT - MB;

  // ── Scales ──
  const maxLines   = Math.max(...totalLines,  1);
  const maxCommits = Math.max(...commitsArr,  1);
  const maxChanges = Math.max(...changesArr,  1);
  const maxBar     = Math.max(...addArr.map((a, i) => a + delArr[i]), 1);

  const xOf = i  => ML + i * (innerW / Math.max(n - 1, 1));
  const yOf = (v, max) => MT + innerH - (v / max) * innerH;

  // ── Point arrays ──
  const ptLines   = days.map((_, i) => ({ x: xOf(i), y: yOf(totalLines[i],  maxLines)   }));
  const ptCommits = days.map((_, i) => ({ x: xOf(i), y: yOf(commitsArr[i],  maxCommits) }));
  const ptChanges = days.map((_, i) => ({ x: xOf(i), y: yOf(changesArr[i],  maxChanges) }));

  // ── Smooth paths ──
  const pathLines   = catmullRomToBezier(ptLines);
  const pathCommits = catmullRomToBezier(ptCommits);
  const pathChanges = catmullRomToBezier(ptChanges);
  const areaLines   = `${pathLines} L ${ptLines[n-1].x.toFixed(2)} ${MT + innerH} L ${ptLines[0].x.toFixed(2)} ${MT + innerH} Z`;

  // ── Bar width: always readable, never too fat ──
  const bw = Math.max(4, Math.min(22, (innerW / n) * 0.58));

  // ── Bars ──
  let bars = "";
  days.forEach((_, i) => {
    const total = addArr[i] + delArr[i];
    if (!total) return;
    const fullH = (total / maxBar) * innerH * 0.72;
    const addH  = (addArr[i] / total) * fullH;
    const delH  = fullH - addH;
    const bx    = xOf(i) - bw / 2;
    const by    = MT + innerH - fullH;
    if (delH > 0.8)
      bars += `<rect x="${bx.toFixed(1)}" y="${by.toFixed(1)}" width="${bw}" height="${delH.toFixed(1)}" fill="url(#gDelBar)" rx="2" opacity="0.82"/>`;
    if (addH > 0.8)
      bars += `<rect x="${bx.toFixed(1)}" y="${(by + delH).toFixed(1)}" width="${bw}" height="${addH.toFixed(1)}" fill="url(#gAddBar)" rx="2" opacity="0.85"/>`;
  });

  // ── Circles ──
  let ciCommits = "", ciChanges = "";
  ptCommits.forEach((pt, i) => {
    ciCommits += `<circle cx="${pt.x.toFixed(1)}" cy="${pt.y.toFixed(1)}" r="4.8" fill="#ff8c42" stroke="#ffd0a0" stroke-width="1.2" filter="url(#glowOrange)"><title>${days[i]}: ${commitsArr[i]} commits</title></circle>`;
  });
  ptChanges.forEach((pt, i) => {
    ciChanges += `<circle cx="${pt.x.toFixed(1)}" cy="${pt.y.toFixed(1)}" r="4" fill="#00d4f5" stroke="#80eeff" stroke-width="1" filter="url(#glowCyan)"><title>${days[i]}: ${changesArr[i]} changes/commit</title></circle>`;
  });

  // ── Y-axis grid + labels (left = lines, right = commits) ──
  const leftTicks  = niceTicks(maxLines,   5);
  const rightTicks = niceTicks(maxCommits, 5);

  let yGrid = "", yLabelsLeft = "", yLabelsRight = "";

  leftTicks.forEach((val, i) => {
    const y      = yOf(val, maxLines);
    const isBase = val === 0;
    yGrid += `<line x1="${ML}" y1="${y.toFixed(1)}" x2="${ML + innerW}" y2="${y.toFixed(1)}"
      stroke="#1a3f7a" stroke-width="${isBase ? 1.4 : 0.6}"
      stroke-opacity="${isBase ? 0.95 : 0.38}"
      stroke-dasharray="${isBase ? "none" : "6,6"}"/>`;
    yLabelsLeft += `<text x="${ML - 12}" y="${(y + 4).toFixed(1)}"
      font-size="11.5" fill="#5aa8ff" text-anchor="end"
      font-family="'Lexend', 'Segoe UI', sans-serif" font-weight="300">${fmtK(val)}</text>`;
  });

  rightTicks.forEach(val => {
    const y = yOf(val, maxCommits);
    yLabelsRight += `<text x="${ML + innerW + 12}" y="${(y + 4).toFixed(1)}"
      font-size="11.5" fill="#ff9a55" text-anchor="start"
      font-family="'Lexend', 'Segoe UI', sans-serif" font-weight="300" opacity="0.88">${val}</text>`;
  });

  // ── X-axis labels: max 13, evenly spaced, always include first+last ──
  let xLabels = "";
  const MAX_X_LABELS = 13;
  // Build set of indices to render
  const labelIndices = new Set([0, n - 1]);
  if (n > 2) {
    const step = Math.ceil((n - 1) / (MAX_X_LABELS - 1));
    for (let i = step; i < n - 1; i += step) labelIndices.add(i);
  }

  // Check minimum pixel gap between labels to avoid overlap (estimated label width ~36px at -42°)
  const sortedIndices = [...labelIndices].sort((a, b) => a - b);
  const filtered = [sortedIndices[0]];
  for (let k = 1; k < sortedIndices.length; k++) {
    const prev = filtered[filtered.length - 1];
    const curr = sortedIndices[k];
    if (xOf(curr) - xOf(prev) >= 44) filtered.push(curr);
  }

  const labelBaseY = MT + innerH + 24;
  filtered.forEach(i => {
    const x = xOf(i);
    xLabels += `<text
      x="${x.toFixed(1)}" y="${labelBaseY}"
      font-size="10.5" fill="#5aa8ff" text-anchor="end"
      font-family="'Lexend', 'Segoe UI', sans-serif" font-weight="300"
      transform="rotate(-42, ${x.toFixed(1)}, ${labelBaseY})">${formatLabel(days[i])}</text>`;
  });

  // ── Totals ──
  const totalCommits   = commitsArr.reduce((a, b) => a + b, 0);
  const totalAdditions = addArr.reduce((a, b) => a + b, 0);
  const totalDeletions = delArr.reduce((a, b) => a + b, 0);

  // ── Legend: 5 items, fixed pixel geometry, centered ──
  // Each entry: [symbol, color, label]
  const legendDefs = [
    { sym: "area",  color: "#2a6cb0",  label: "Total Lines"       },
    { sym: "line",  color: "#ff8c42",  label: "Commits"           },
    { sym: "line",  color: "#00d4f5",  label: "Changes / Commit"  },
    { sym: "bar",   color: "#22c55e",  label: "Additions"         },
    { sym: "bar",   color: "#ef4444",  label: "Deletions"         },
  ];

  // Fixed pixel widths: symW + symGap + textW + itemGap
  const SYM_W    = 26;   // symbol icon width
  const SYM_GAP  = 9;    // gap between icon and label text
  const ITEM_GAP = 36;   // gap between successive items
  const CH_W     = 7.8;  // approximate px per character at font-size 12

  const itemTotalW = legendDefs.map(d => SYM_W + SYM_GAP + d.label.length * CH_W);
  const legendTotalW = itemTotalW.reduce((a, b) => a + b, 0) + ITEM_GAP * (legendDefs.length - 1);

  const legendY  = MT - 32;   // sits inside header strip
  let   lx       = (W - legendTotalW) / 2;
  let   legendSVG = "";

  legendDefs.forEach((d, idx) => {
    const symCX = lx + SYM_W / 2;
    const symCY = legendY - 4;

    if (d.sym === "area") {
      legendSVG += `<rect x="${lx.toFixed(1)}" y="${(legendY - 11).toFixed(1)}" width="${SYM_W}" height="13" rx="3"
        fill="${d.color}" fill-opacity="0.5" stroke="${d.color}" stroke-width="1.5"/>`;
    } else if (d.sym === "line") {
      legendSVG += `<line x1="${lx.toFixed(1)}" y1="${symCY.toFixed(1)}"
        x2="${(lx + SYM_W).toFixed(1)}" y2="${symCY.toFixed(1)}"
        stroke="${d.color}" stroke-width="3" stroke-linecap="round"/>`;
      legendSVG += `<circle cx="${symCX.toFixed(1)}" cy="${symCY.toFixed(1)}" r="4"
        fill="${d.color}" stroke="#fff" stroke-width="0.8" stroke-opacity="0.4"/>`;
    } else {
      // bar symbol: narrow tall rect
      legendSVG += `<rect x="${(lx + 7).toFixed(1)}" y="${(legendY - 11).toFixed(1)}" width="12" height="13" rx="2"
        fill="${d.color}" opacity="0.9"/>`;
    }

    lx += SYM_W + SYM_GAP;
    legendSVG += `<text x="${lx.toFixed(1)}" y="${legendY.toFixed(1)}"
      font-size="12" fill="#a8d4ff"
      font-family="'Lexend', 'Segoe UI', sans-serif" font-weight="400">${d.label}</text>`;
    lx += d.label.length * CH_W + ITEM_GAP;
  });

  // ── Stats badge (top-right, inside header) ──
  const BD_W = 116, BD_H = 74;
  const bdX = W - MR - BD_W - 6;
  const bdY = 10;

  const badge = `
    <rect x="${bdX}" y="${bdY}" width="${BD_W}" height="${BD_H}" rx="9"
          fill="#030e26" fill-opacity="0.92" stroke="#1d52c0" stroke-width="1.3"/>
    <text x="${bdX + BD_W/2}" y="${bdY + 18}" font-size="8.5" fill="#4a90e2"
          text-anchor="middle" font-family="'Lexend', sans-serif" letter-spacing="2.2" font-weight="400">TOTAL  COMMITS</text>
    <text x="${bdX + BD_W/2}" y="${bdY + 46}" font-size="28" font-weight="700" fill="#d4eeff"
          text-anchor="middle" font-family="'Lexend', sans-serif" filter="url(#glowTitle)">${totalCommits}</text>
    <line x1="${bdX + 16}" y1="${bdY + 53}" x2="${bdX + BD_W - 16}" y2="${bdY + 53}"
          stroke="#1d4ed8" stroke-width="0.8" stroke-opacity="0.5"/>
    <text x="${bdX + BD_W/2 - 18}" y="${bdY + 66}" font-size="9.5" fill="#4ade80"
          text-anchor="middle" font-family="'Lexend', sans-serif" font-weight="400">+${fmtK(totalAdditions)}</text>
    <text x="${bdX + BD_W/2 + 20}" y="${bdY + 66}" font-size="9.5" fill="#f87171"
          text-anchor="middle" font-family="'Lexend', sans-serif" font-weight="400">-${fmtK(totalDeletions)}</text>`;

  // ── Peak annotation (smart positioning to avoid edge clipping) ──
  const peakI      = commitsArr.indexOf(Math.max(...commitsArr));
  const peakPt     = ptCommits[peakI];
  const annotW     = 116;
  const annotH     = 24;
  const rawAnnotX  = peakPt.x - annotW / 2;
  const annotX     = Math.max(ML, Math.min(ML + innerW - annotW, rawAnnotX));
  const annotBoxY  = peakPt.y - 60;
  const annotLineY = Math.max(MT + 4, annotBoxY + annotH + 2);

  const peakAnnot = `
    <line x1="${peakPt.x.toFixed(1)}" y1="${(peakPt.y - 15).toFixed(1)}"
          x2="${peakPt.x.toFixed(1)}" y2="${annotLineY.toFixed(1)}"
          stroke="#60a5fa" stroke-width="1.3" stroke-dasharray="4,4" opacity="0.75"/>
    <rect x="${annotX.toFixed(1)}" y="${annotBoxY.toFixed(1)}"
          width="${annotW}" height="${annotH}" rx="6"
          fill="#010e2a" stroke="#2563eb" stroke-width="1.1" opacity="0.97"/>
    <text x="${(annotX + annotW / 2).toFixed(1)}" y="${(annotBoxY + 16).toFixed(1)}"
          font-size="10.5" fill="#93c5fd" text-anchor="middle"
          font-family="'Lexend', 'Segoe UI', sans-serif" font-weight="400"
          letter-spacing="0.6">⬡  peak · ${commitsArr[peakI]} commits</text>`;

  // ── Corner brackets (Hollow Knight UI feel) ──
  const bLen = 24;
  const corners = [
    [ML, MT, 1, 1], [ML + innerW, MT, -1, 1],
    [ML, MT + innerH, 1, -1], [ML + innerW, MT + innerH, -1, -1]
  ].map(([cx, cy, dx, dy]) =>
    `<path d="M ${cx} ${(cy + dy * bLen).toFixed(1)} L ${cx} ${cy} L ${(cx + dx * bLen).toFixed(1)} ${cy}"
      fill="none" stroke="#4a90e2" stroke-width="2.2" stroke-opacity="0.9" stroke-linecap="round"/>`
  ).join("");

  // ── Wave decoration (bottom, ethereal feel) ──
  const waves = Array.from({ length: 4 }, (_, k) => {
    const yo  = H - 8 - k * 6;
    const amp = 8 - k * 1.8;
    const seg = W / 8;
    let d = `M 0 ${yo}`;
    for (let s = 0; s < 8; s++)
      d += ` Q ${(s * seg + seg * 0.5).toFixed(1)} ${(yo - amp).toFixed(1)} ${((s + 1) * seg).toFixed(1)} ${yo}`;
    return `<path d="${d}" fill="none" stroke="#1948a0"
      stroke-width="${1.4 - k * 0.28}" stroke-opacity="${0.38 - k * 0.07}"/>`;
  }).join("\n");

  // ── Separator line (header / chart boundary) ──
  const headerSep = `<line x1="24" y1="${(MT - 10).toFixed(1)}" x2="${(W - 24).toFixed(1)}" y2="${(MT - 10).toFixed(1)}"
    stroke="url(#gBorder)" stroke-width="0.9" stroke-opacity="0.55"/>`;

  // ── Left axis label (rotated) ──
  const axisLabelLeft  = `<text x="16" y="${(MT + innerH / 2).toFixed(1)}"
    font-size="10" fill="#4a90e2" text-anchor="middle"
    font-family="'Lexend', 'Segoe UI', sans-serif" letter-spacing="3" font-weight="300"
    transform="rotate(-90, 16, ${(MT + innerH / 2).toFixed(1)})">LINES</text>`;

  const axisLabelRight = `<text x="${(W - 14).toFixed(1)}" y="${(MT + innerH / 2).toFixed(1)}"
    font-size="10" fill="#ff8c42" text-anchor="middle"
    font-family="'Lexend', 'Segoe UI', sans-serif" letter-spacing="3" font-weight="300" opacity="0.85"
    transform="rotate(90, ${(W - 14).toFixed(1)}, ${(MT + innerH / 2).toFixed(1)})">COMMITS</text>`;

  // ════════════════════════════════════════════════════════
  return `<svg width="${W}" height="${H}" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}">
<defs>
  <style>@import url('https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;600;700&amp;display=swap');</style>

  <!-- ── Dark navy background ── -->
  <linearGradient id="bg" x1="0%" y1="0%" x2="55%" y2="100%">
    <stop offset="0%"   stop-color="#010c1e"/>
    <stop offset="50%"  stop-color="#010f27"/>
    <stop offset="100%" stop-color="#000a18"/>
  </linearGradient>

  <!-- ── Area fill (total lines) ── -->
  <linearGradient id="areaFill" x1="0%" y1="0%" x2="0%" y2="100%">
    <stop offset="0%"   stop-color="#1e56a8" stop-opacity="0.6"/>
    <stop offset="55%"  stop-color="#12326a" stop-opacity="0.22"/>
    <stop offset="100%" stop-color="#0a1e44" stop-opacity="0"/>
  </linearGradient>

  <!-- ── Commits line ── -->
  <linearGradient id="gCommits" x1="0%" y1="0%" x2="100%" y2="0%">
    <stop offset="0%"   stop-color="#ff6020"/>
    <stop offset="100%" stop-color="#ffaa60"/>
  </linearGradient>

  <!-- ── Changes per commit line ── -->
  <linearGradient id="gChanges" x1="0%" y1="0%" x2="100%" y2="0%">
    <stop offset="0%"   stop-color="#0090c0"/>
    <stop offset="50%"  stop-color="#00d4f5"/>
    <stop offset="100%" stop-color="#7eeeff"/>
  </linearGradient>

  <!-- ── Outer border ── -->
  <linearGradient id="gBorder" x1="0%" y1="0%" x2="100%" y2="100%">
    <stop offset="0%"   stop-color="#1840a0" stop-opacity="0.95"/>
    <stop offset="50%"  stop-color="#3b82f6" stop-opacity="0.65"/>
    <stop offset="100%" stop-color="#60a5fa" stop-opacity="0.95"/>
  </linearGradient>

  <!-- ── Header band ── -->
  <linearGradient id="gHeader" x1="0%" y1="0%" x2="100%" y2="0%">
    <stop offset="0%"   stop-color="#061530" stop-opacity="0"/>
    <stop offset="20%"  stop-color="#091c3e" stop-opacity="0.92"/>
    <stop offset="80%"  stop-color="#091c3e" stop-opacity="0.92"/>
    <stop offset="100%" stop-color="#061530" stop-opacity="0"/>
  </linearGradient>

  <!-- ── Bar fills ── -->
  <linearGradient id="gAddBar" x1="0%" y1="0%" x2="0%" y2="100%">
    <stop offset="0%"   stop-color="#34d06a"/>
    <stop offset="100%" stop-color="#16a34a"/>
  </linearGradient>
  <linearGradient id="gDelBar" x1="0%" y1="0%" x2="0%" y2="100%">
    <stop offset="0%"   stop-color="#f86060"/>
    <stop offset="100%" stop-color="#c52020"/>
  </linearGradient>

  <!-- ── Glow filters ── -->
  <filter id="glowOrange" x="-120%" y="-120%" width="340%" height="340%">
    <feGaussianBlur stdDeviation="4.5" result="b"/>
    <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
  <filter id="glowCyan" x="-120%" y="-120%" width="340%" height="340%">
    <feGaussianBlur stdDeviation="4" result="b"/>
    <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
  <filter id="glowTitle">
    <feGaussianBlur stdDeviation="5.5" result="b"/>
    <feMerge><feMergeNode in="b"/><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
  <filter id="glowLine" x="-6%" y="-120%" width="112%" height="340%">
    <feGaussianBlur stdDeviation="5" result="b"/>
    <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>

  <!-- ── Hollow Knight diamond tile (subtle depth) ── -->
  <pattern id="diamondTile" x="0" y="0" width="30" height="30" patternUnits="userSpaceOnUse">
    <path d="M15 2 L28 15 L15 28 L2 15 Z"
          fill="none" stroke="#0d2464" stroke-width="0.45" stroke-opacity="0.3"/>
  </pattern>

  <!-- ── Clip ── -->
  <clipPath id="chartClip">
    <rect x="${ML}" y="${MT}" width="${innerW}" height="${innerH}"/>
  </clipPath>
</defs>

<!-- ════ BACKGROUND ════ -->
<rect width="${W}" height="${H}" fill="url(#bg)" rx="16"/>
<rect width="${W}" height="${H}" fill="url(#diamondTile)" rx="16" opacity="1"/>

<!-- ════ BORDERS ════ -->
<rect width="${W}" height="${H}" fill="none" rx="16"
      stroke="url(#gBorder)" stroke-width="2.2"/>
<rect x="5" y="5" width="${W - 10}" height="${H - 10}" fill="none" rx="13"
      stroke="#1840a0" stroke-width="0.8" stroke-opacity="0.4"/>

<!-- ════ HEADER BAND ════ -->
<rect x="0" y="0" width="${W}" height="${MT - 8}" fill="url(#gHeader)" rx="16"/>
${headerSep}

<!-- ════ TITLE ════ -->
<text x="${(ML + innerW / 2).toFixed(1)}" y="48"
      font-size="19" font-weight="600" fill="#cce8ff"
      text-anchor="middle" font-family="'Lexend', 'Segoe UI', sans-serif"
      filter="url(#glowTitle)" letter-spacing="6">⬡  COMMIT  ANALYTICS  ⬡</text>
<text x="${(ML + innerW / 2).toFixed(1)}" y="67"
      font-size="10" fill="#3d7ed4" text-anchor="middle"
      font-family="'Lexend', 'Segoe UI', sans-serif" letter-spacing="4" font-weight="300">${REPO}</text>

<!-- ════ STATS BADGE ════ -->
${badge}

<!-- ════ LEGEND ════ -->
<g>${legendSVG}</g>

<!-- ════ AXIS LABELS ════ -->
${axisLabelLeft}
${axisLabelRight}

<!-- ════ GRID + Y-LABELS ════ -->
${yGrid}
${yLabelsLeft}
${yLabelsRight}

<!-- ════ AXIS LINES ════ -->
<line x1="${ML}" y1="${MT}" x2="${ML}" y2="${MT + innerH}"
      stroke="#1a3d7a" stroke-width="1.3" stroke-opacity="0.7"/>
<line x1="${ML}" y1="${MT + innerH}" x2="${ML + innerW}" y2="${MT + innerH}"
      stroke="#1a3d7a" stroke-width="1.6" stroke-opacity="0.85"/>
<line x1="${ML + innerW}" y1="${MT}" x2="${ML + innerW}" y2="${MT + innerH}"
      stroke="#ff8c42" stroke-width="1" stroke-opacity="0.32"/>

<!-- ════ X-AXIS DATE LABELS ════ -->
${xLabels}

<!-- ════ CHART CONTENT (clipped) ════ -->
<g clip-path="url(#chartClip)">

  <!-- Total lines: area fill + edge line -->
  <path d="${areaLines}" fill="url(#areaFill)"/>
  <path d="${pathLines}" fill="none" stroke="#1e56a8" stroke-width="2" stroke-opacity="0.75"/>

  <!-- Stacked bars (additions green / deletions red) -->
  ${bars}

  <!-- Commits line — glow layer + crisp line -->
  <path d="${pathCommits}" fill="none" stroke="#ff8c42"
        stroke-width="10" stroke-opacity="0.08" filter="url(#glowLine)"/>
  <path d="${pathCommits}" fill="none" stroke="url(#gCommits)"
        stroke-width="3" stroke-linecap="round"/>

  <!-- Changes per commit — glow layer + crisp line -->
  <path d="${pathChanges}" fill="none" stroke="#00d4f5"
        stroke-width="10" stroke-opacity="0.08" filter="url(#glowLine)"/>
  <path d="${pathChanges}" fill="none" stroke="url(#gChanges)"
        stroke-width="3" stroke-linecap="round"/>

</g>

<!-- ════ DATA-POINT CIRCLES (above clip) ════ -->
${ciCommits}
${ciChanges}

<!-- ════ PEAK ANNOTATION ════ -->
${peakAnnot}

<!-- ════ CORNER BRACKETS ════ -->
${corners}

<!-- ════ WAVE DECORATION ════ -->
${waves}

</svg>`;
}

// ─── MAIN ─────────────────────────────────────────────────────────────────────

(async () => {
  try {
    console.log("⬡ Starting Hollow-Knight-style commit chart...");
    const fetch   = await getFetch();
    const commits = await fetchAllCommits(fetch);
    console.log(`📊 Fetching per-commit stats (${commits.length} commits, ${CONCURRENCY} concurrent)...`);
    const stats   = await batchFetchStats(fetch, commits);
    const dayData = groupByDay(commits, stats);
    const svg     = generateSVG(dayData);
    fs.writeFileSync("scripts/commit-chart.svg", svg);
    console.log("✅ scripts/commit-chart.svg generated — Hollow Knight blue ⬡");
  } catch (err) {
    console.error("❌ Error:", err);
    process.exit(1);
  }
})();