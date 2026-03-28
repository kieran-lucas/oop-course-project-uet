#!/usr/bin/env node
// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║  ⬡  COMMIT ANALYTICS — Hollow Knight Edition v2                           ║
// ║  Adaptive · Multi-mode · Outlier-aware · Professional-grade SVG charts     ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

const fs = require("fs");

async function getFetch() {
  const { default: fetch } = await import("node-fetch");
  return fetch;
}

// ─── CLI CONFIGURATION ──────────────────────────────────────────────────────

const DEFAULTS = {
  repo: "kieran-lucas/oop-course-project-uet",
  concurrency: 6,
  outputDir: "scripts",
  // Aggregation: "day" | "week" | "month"
  aggregation: "day",
  // Scale: "linear" | "log" | "adaptive"
  scaleMode: "adaptive",
  // Moving average window (0 = disabled)
  maWindow: 7,
  // Max data points before auto-aggregation kicks in
  maxDataPoints: 90,
  // Enable heatmap calendar view
  heatmap: true,
  // Enable mini-map overview
  minimap: true,
  // Outlier threshold (multiples of IQR above Q3)
  outlierIQRMultiplier: 1.5,
  // Peak height ratio: peak values reach at most this fraction of chart height (0.0–1.0)
  peakHeightRatio: 0.7,
  // Annotations: auto-detect peaks, streaks, milestones
  annotations: true,
  // Theme: "hollow-knight" | "ocean-depth" | "aurora"
  theme: "hollow-knight",
};

function parseArgs() {
  const cfg = { ...DEFAULTS };
  const args = process.argv.slice(2);
  for (let i = 0; i < args.length; i++) {
    const [key, val] = args[i].replace(/^--/, "").split("=");
    if (key === "repo") cfg.repo = val;
    else if (key === "agg" || key === "aggregation") cfg.aggregation = val;
    else if (key === "scale") cfg.scaleMode = val;
    else if (key === "ma") cfg.maWindow = parseInt(val, 10);
    else if (key === "max-points") cfg.maxDataPoints = parseInt(val, 10);
    else if (key === "no-heatmap") cfg.heatmap = false;
    else if (key === "no-minimap") cfg.minimap = false;
    else if (key === "no-annotations") cfg.annotations = false;
    else if (key === "theme") cfg.theme = val;
    else if (key === "output" || key === "out") cfg.outputDir = val;
    else if (key === "concurrency") cfg.concurrency = parseInt(val, 10);
    else if (key === "outlier-iqr") cfg.outlierIQRMultiplier = parseFloat(val);
    else if (key === "peak-ratio") cfg.peakHeightRatio = parseFloat(val);
  }
  return cfg;
}

const CFG = parseArgs();
const token = process.env.GITHUB_TOKEN;
if (!token) {
  console.error("❌ Set GITHUB_TOKEN environment variable before running.");
  process.exit(1);
}

// ═══════════════════════════════════════════════════════════════════════════════
// §1  THEMES
// ═══════════════════════════════════════════════════════════════════════════════

const THEMES = {
  "hollow-knight": {
    bg: ["#010c1e", "#010f27", "#000a18"],
    primary: "#4a90e2",
    primaryLight: "#5aa8ff",
    primaryDim: "#1a3f7a",
    accent1: "#ff8c42",       // commits — orange
    accent1Light: "#ffd0a0",
    accent2: "#00d4f5",       // changes — cyan
    accent2Light: "#80eeff",
    positive: "#22c55e",      // additions — green
    negative: "#ef4444",      // deletions — red
    text: "#cce8ff",
    textDim: "#3d7ed4",
    textMuted: "#1948a0",
    grid: "#1a3f7a",
    border: ["#1840a0", "#3b82f6", "#60a5fa"],
    headerBand: "#091c3e",
    surface: "#030e26",
    patternStroke: "#0d2464",
    annotation: "#2563eb",
    annotationText: "#93c5fd",
    heatEmpty: "#0a1630",
    heatLow: "#0d3068",
    heatMid: "#1e56a8",
    heatHigh: "#3b82f6",
    heatMax: "#93c5fd",
    maLine: "#a78bfa",
    maLineLight: "#c4b5fd",
    outlierFill: "#fbbf24",
    outlierStroke: "#f59e0b",
    milestoneColor: "#f472b6",
  },
  "ocean-depth": {
    bg: ["#020e12", "#031820", "#011018"],
    primary: "#0ea5e9",
    primaryLight: "#38bdf8",
    primaryDim: "#0c4a6e",
    accent1: "#f97316",
    accent1Light: "#fdba74",
    accent2: "#06b6d4",
    accent2Light: "#67e8f9",
    positive: "#10b981",
    negative: "#f43f5e",
    text: "#e0f2fe",
    textDim: "#0284c7",
    textMuted: "#075985",
    grid: "#0c4a6e",
    border: ["#0369a1", "#0ea5e9", "#38bdf8"],
    headerBand: "#082f49",
    surface: "#0c1a2e",
    patternStroke: "#0c4a6e",
    annotation: "#0284c7",
    annotationText: "#7dd3fc",
    heatEmpty: "#0a1a28",
    heatLow: "#0c4a6e",
    heatMid: "#0284c7",
    heatHigh: "#0ea5e9",
    heatMax: "#7dd3fc",
    maLine: "#8b5cf6",
    maLineLight: "#a78bfa",
    outlierFill: "#f59e0b",
    outlierStroke: "#d97706",
    milestoneColor: "#ec4899",
  },
  "aurora": {
    bg: ["#0a0a1a", "#0f0e24", "#08081a"],
    primary: "#8b5cf6",
    primaryLight: "#a78bfa",
    primaryDim: "#3b1f7e",
    accent1: "#f472b6",
    accent1Light: "#f9a8d4",
    accent2: "#34d399",
    accent2Light: "#6ee7b7",
    positive: "#22d3ee",
    negative: "#fb7185",
    text: "#e2e8f0",
    textDim: "#7c3aed",
    textMuted: "#4c1d95",
    grid: "#2e1065",
    border: ["#6d28d9", "#8b5cf6", "#a78bfa"],
    headerBand: "#1e1045",
    surface: "#130d30",
    patternStroke: "#2e1065",
    annotation: "#7c3aed",
    annotationText: "#c4b5fd",
    heatEmpty: "#120e28",
    heatLow: "#3b1f7e",
    heatMid: "#6d28d9",
    heatHigh: "#8b5cf6",
    heatMax: "#c4b5fd",
    maLine: "#fbbf24",
    maLineLight: "#fde68a",
    outlierFill: "#fb923c",
    outlierStroke: "#f97316",
    milestoneColor: "#22d3ee",
  },
};

function T() { return THEMES[CFG.theme] || THEMES["hollow-knight"]; }

// ═══════════════════════════════════════════════════════════════════════════════
// §2  GITHUB API — Fetch commits + stats
// ═══════════════════════════════════════════════════════════════════════════════

async function fetchAllCommits(fetch) {
  let page = 1, all = [];
  while (true) {
    console.log(`📦 Fetching commits page ${page}...`);
    const res = await fetch(
      `https://api.github.com/repos/${CFG.repo}/commits?per_page=100&page=${page}`,
      { headers: { Authorization: `token ${token}`, "User-Agent": "commit-chart-v2" } }
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
      `https://api.github.com/repos/${CFG.repo}/commits/${sha}`,
      { headers: { Authorization: `token ${token}`, "User-Agent": "commit-chart-v2" } }
    );
    if (!res.ok) return { additions: 0, deletions: 0, files: 0 };
    const data = await res.json();
    return {
      additions: data.stats?.additions || 0,
      deletions: data.stats?.deletions || 0,
      files: data.files?.length || 0,
    };
  } catch {
    return { additions: 0, deletions: 0, files: 0 };
  }
}

async function batchFetchStats(fetch, commits) {
  const results = new Array(commits.length);
  for (let i = 0; i < commits.length; i += CFG.concurrency) {
    const batch = commits.slice(i, i + CFG.concurrency);
    process.stdout.write(`\r🔍 Stats ${i + 1}–${Math.min(i + CFG.concurrency, commits.length)} / ${commits.length}   `);
    const stats = await Promise.all(batch.map(c => fetchCommitStats(fetch, c.sha)));
    stats.forEach((s, j) => (results[i + j] = s));
    if (i + CFG.concurrency < commits.length) await new Promise(r => setTimeout(r, 180));
  }
  console.log("\n✅ Stats fetched.");
  return results;
}

// ═══════════════════════════════════════════════════════════════════════════════
// §3  DATA PROCESSING — Aggregation, Statistics, Outlier Detection
// ═══════════════════════════════════════════════════════════════════════════════

function extractAuthorMap(commits) {
  const map = {};
  commits.forEach(c => {
    const name = c.commit.author.name || c.author?.login || "unknown";
    map[name] = (map[name] || 0) + 1;
  });
  return Object.entries(map)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 8)
    .map(([name, count]) => ({ name, count }));
}

function groupByDay(commits, statsArr) {
  const map = {};
  commits.forEach((c, i) => {
    const day = c.commit.author.date.slice(0, 10);
    if (!map[day]) map[day] = { commits: 0, additions: 0, deletions: 0, files: 0, authors: new Set() };
    map[day].commits++;
    map[day].additions += statsArr[i].additions;
    map[day].deletions += statsArr[i].deletions;
    map[day].files += statsArr[i].files;
    map[day].authors.add(c.commit.author.name || c.author?.login || "unknown");
  });
  // Convert author sets to counts
  for (const k of Object.keys(map)) {
    map[k].authorCount = map[k].authors.size;
    delete map[k].authors;
  }
  return map;
}

function fillMissingDays(dayData) {
  const days = Object.keys(dayData).sort();
  if (days.length === 0) return dayData;
  const start = new Date(days[0] + "T00:00:00Z");
  const end = new Date(days[days.length - 1] + "T00:00:00Z");
  const filled = {};
  for (let d = new Date(start); d <= end; d.setUTCDate(d.getUTCDate() + 1)) {
    const key = d.toISOString().slice(0, 10);
    filled[key] = dayData[key] || { commits: 0, additions: 0, deletions: 0, files: 0, authorCount: 0 };
  }
  return filled;
}

function aggregateByWeek(dayData) {
  const result = {};
  Object.entries(dayData).forEach(([day, data]) => {
    const d = new Date(day + "T00:00:00Z");
    // ISO week start (Monday)
    const weekStart = new Date(d);
    const dow = d.getUTCDay() || 7; // Mon=1 ... Sun=7
    weekStart.setUTCDate(d.getUTCDate() - dow + 1);
    const key = weekStart.toISOString().slice(0, 10);
    if (!result[key]) result[key] = { commits: 0, additions: 0, deletions: 0, files: 0, authorCount: 0 };
    result[key].commits += data.commits;
    result[key].additions += data.additions;
    result[key].deletions += data.deletions;
    result[key].files += data.files;
    result[key].authorCount = Math.max(result[key].authorCount, data.authorCount);
  });
  return result;
}

function aggregateByMonth(dayData) {
  const result = {};
  Object.entries(dayData).forEach(([day, data]) => {
    const key = day.slice(0, 7) + "-01";
    if (!result[key]) result[key] = { commits: 0, additions: 0, deletions: 0, files: 0, authorCount: 0 };
    result[key].commits += data.commits;
    result[key].additions += data.additions;
    result[key].deletions += data.deletions;
    result[key].files += data.files;
    result[key].authorCount = Math.max(result[key].authorCount, data.authorCount);
  });
  return result;
}

function autoSelectAggregation(dayCount) {
  if (CFG.aggregation !== "day") return CFG.aggregation;
  if (dayCount > CFG.maxDataPoints * 4) return "month";
  if (dayCount > CFG.maxDataPoints) return "week";
  return "day";
}

// ─── Statistics ──────────────────────────────────────────────────────────────

function computeStats(arr) {
  if (arr.length === 0) return { min: 0, max: 0, mean: 0, median: 0, q1: 0, q3: 0, iqr: 0, stddev: 0 };
  const sorted = [...arr].sort((a, b) => a - b);
  const n = sorted.length;
  const sum = sorted.reduce((a, b) => a + b, 0);
  const mean = sum / n;
  const median = n % 2 === 0 ? (sorted[n / 2 - 1] + sorted[n / 2]) / 2 : sorted[Math.floor(n / 2)];
  const q1 = sorted[Math.floor(n * 0.25)];
  const q3 = sorted[Math.floor(n * 0.75)];
  const iqr = q3 - q1;
  const variance = sorted.reduce((acc, v) => acc + (v - mean) ** 2, 0) / n;
  const stddev = Math.sqrt(variance);
  return { min: sorted[0], max: sorted[n - 1], mean, median, q1, q3, iqr, stddev };
}

function movingAverage(arr, window) {
  if (window <= 1) return arr;
  const result = [];
  for (let i = 0; i < arr.length; i++) {
    const start = Math.max(0, i - Math.floor(window / 2));
    const end = Math.min(arr.length, i + Math.ceil(window / 2));
    const slice = arr.slice(start, end);
    result.push(slice.reduce((a, b) => a + b, 0) / slice.length);
  }
  return result;
}

function detectOutliers(arr, multiplier) {
  const stats = computeStats(arr);
  const upperFence = stats.q3 + multiplier * stats.iqr;
  const lowerFence = Math.max(0, stats.q1 - multiplier * stats.iqr);
  return arr.map((v, i) => ({
    index: i,
    value: v,
    isOutlier: v > upperFence || v < lowerFence,
    isUpperOutlier: v > upperFence,
  }));
}

// ─── d3-inspired Scale: niceScaleMax ────────────────────────────────────────
// Approach from d3-scale .nice() + headroom:
// 1. Divide absMax by peakHeightRatio to add headroom
// 2. Round UP to next "nice" number (power of 10 × {1, 2, 2.5, 5})
// 3. Result: peak always sits at ≤ peakHeightRatio of chart height
//
// Example: absMax=50, peakRatio=0.7
//   → target = 50/0.7 = 71.4
//   → nice step for 5 ticks = 20
//   → niced max = ceil(71.4/20)*20 = 80
//   → peak renders at 50/80 = 62.5% ✓

function niceScaleMax(absMax, tickCount = 5) {
  if (absMax <= 0) return 1;

  // Step 1: add headroom so the tallest data point reaches at most peakHeightRatio
  const target = absMax / CFG.peakHeightRatio;

  // Step 2: d3-style "nice" — find nice step, ceil to next grid line
  const rawStep = target / tickCount;
  const magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));
  const residual = rawStep / magnitude;

  let niceStep;
  if (residual <= 1) niceStep = 1 * magnitude;
  else if (residual <= 2) niceStep = 2 * magnitude;
  else if (residual <= 5) niceStep = 5 * magnitude;
  else niceStep = 10 * magnitude;

  const niceMax = Math.ceil(target / niceStep) * niceStep;

  return Math.max(niceMax, 1);
}

// ═══════════════════════════════════════════════════════════════════════════════
// §4  SMART ANNOTATIONS — Peaks, Streaks, Milestones
// ═══════════════════════════════════════════════════════════════════════════════

function detectAnnotations(days, seriesData) {
  if (!CFG.annotations) return [];
  const annots = [];
  const { commits, additions, deletions } = seriesData;
  const n = days.length;
  if (n === 0) return annots;

  // ─── Peak commit day ──
  const maxCommits = Math.max(...commits);
  const peakIdx = commits.indexOf(maxCommits);
  if (maxCommits > 0) {
    annots.push({
      type: "peak",
      index: peakIdx,
      label: `Peak · ${maxCommits} commits`,
      series: "commits",
    });
  }

  // ─── Longest streak ──
  let maxStreak = 0, curStreak = 0, streakEnd = -1;
  for (let i = 0; i < n; i++) {
    if (commits[i] > 0) {
      curStreak++;
      if (curStreak > maxStreak) { maxStreak = curStreak; streakEnd = i; }
    } else {
      curStreak = 0;
    }
  }
  if (maxStreak >= 3) {
    const streakStart = streakEnd - maxStreak + 1;
    annots.push({
      type: "streak",
      startIndex: streakStart,
      endIndex: streakEnd,
      label: `${maxStreak}-day streak`,
      series: "commits",
    });
  }

  // ─── Biggest single-day code change ──
  const changes = additions.map((a, i) => a + deletions[i]);
  const maxChange = Math.max(...changes);
  const bigIdx = changes.indexOf(maxChange);
  if (maxChange > 0 && bigIdx !== peakIdx) {
    annots.push({
      type: "milestone",
      index: bigIdx,
      label: `${fmtK(maxChange)} lines changed`,
      series: "changes",
    });
  }

  // ─── Cumulative milestones (1k, 5k, 10k, 50k, 100k lines) ──
  let cumulative = 0;
  const milestones = [1000, 5000, 10000, 50000, 100000];
  const hit = new Set();
  for (let i = 0; i < n; i++) {
    cumulative += additions[i] - deletions[i];
    cumulative = Math.max(0, cumulative);
    for (const m of milestones) {
      if (cumulative >= m && !hit.has(m)) {
        hit.add(m);
        annots.push({
          type: "cumulative-milestone",
          index: i,
          label: `${fmtK(m)} lines`,
          series: "totalLines",
        });
      }
    }
  }

  return annots;
}

// ═══════════════════════════════════════════════════════════════════════════════
// §5  SVG UTILITIES — Paths, Labels, Ticks, Splines
// ═══════════════════════════════════════════════════════════════════════════════

function catmullRomToBezier(points, clampMinY, clampMaxY) {
  if (points.length === 0) return "";
  if (points.length === 1) return `M ${points[0].x} ${points[0].y}`;

  const clamp = (val) => {
    if (clampMinY !== undefined && val < clampMinY) return clampMinY;
    if (clampMaxY !== undefined && val > clampMaxY) return clampMaxY;
    return val;
  };

  let d = `M ${points[0].x.toFixed(2)} ${clamp(points[0].y).toFixed(2)}`;
  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[Math.max(i - 1, 0)];
    const p1 = points[i];
    const p2 = points[i + 1];
    const p3 = points[Math.min(i + 2, points.length - 1)];
    const cp1x = p1.x + (p2.x - p0.x) / 6;
    const cp1y = clamp(p1.y + (p2.y - p0.y) / 6);
    const cp2x = p2.x - (p3.x - p1.x) / 6;
    const cp2y = clamp(p2.y - (p3.y - p1.y) / 6);
    d += ` C ${cp1x.toFixed(2)} ${cp1y.toFixed(2)}, ${cp2x.toFixed(2)} ${cp2y.toFixed(2)}, ${p2.x.toFixed(2)} ${clamp(p2.y).toFixed(2)}`;
  }
  return d;
}

function formatLabel(dateStr, agg) {
  const d = new Date(dateStr + "T00:00:00Z");
  const mon = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];
  if (agg === "month") return `${mon[d.getUTCMonth()]} ${d.getUTCFullYear()}`;
  if (agg === "week") return `${d.getUTCDate()} ${mon[d.getUTCMonth()]}`;
  return `${d.getUTCDate()} ${mon[d.getUTCMonth()]}`;
}

function fmtK(v) {
  if (v >= 1_000_000) return `${(v / 1_000_000).toFixed(1)}M`;
  if (v >= 1000) return `${(v / 1000).toFixed(1)}k`;
  return String(Math.round(v));
}

function niceTicks(maxVal, count = 5) {
  if (maxVal <= 0) return [0];
  const raw = maxVal / count;
  const mag = Math.pow(10, Math.floor(Math.log10(raw)));
  const nice = [1, 2, 2.5, 5, 10].find(f => f * mag >= raw) || 10;
  const step = nice * mag;
  const ticks = [];
  for (let v = 0; v <= maxVal * 1.05; v += step) ticks.push(Math.round(v * 1000) / 1000);
  return ticks;
}

function escXml(s) { return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;"); }

// ═══════════════════════════════════════════════════════════════════════════════
// §6  HEATMAP CALENDAR — GitHub-style contribution grid
// ═══════════════════════════════════════════════════════════════════════════════

function generateHeatmapSVG(dayData, yOffset, chartWidth) {
  if (!CFG.heatmap) return { svg: "", height: 0 };
  const t = T();

  const days = Object.keys(dayData).sort();
  if (days.length === 0) return { svg: "", height: 0 };

  const values = days.map(d => dayData[d].commits);
  const maxVal = Math.max(...values, 1);

  // Compute grid dimensions
  const cellSize = 12;
  const cellGap = 3;
  const cellStep = cellSize + cellGap;
  const labelW = 32;

  // Start from the first Sunday on or before the first day
  const startDate = new Date(days[0] + "T00:00:00Z");
  const startDow = startDate.getUTCDay();
  const gridStart = new Date(startDate);
  gridStart.setUTCDate(gridStart.getUTCDate() - startDow);

  const endDate = new Date(days[days.length - 1] + "T00:00:00Z");
  const totalDays = Math.ceil((endDate - gridStart) / (1000 * 60 * 60 * 24)) + 7;
  const totalWeeks = Math.ceil(totalDays / 7);

  // Heatmap color quantization
  const quantize = (v) => {
    if (v === 0) return t.heatEmpty;
    const ratio = v / maxVal;
    if (ratio < 0.25) return t.heatLow;
    if (ratio < 0.5) return t.heatMid;
    if (ratio < 0.75) return t.heatHigh;
    return t.heatMax;
  };

  // Calculate actual needed width and center within chartWidth
  const gridW = totalWeeks * cellStep;
  const heatContentW = labelW + gridW;
  const heatOffsetX = Math.max(0, (chartWidth - heatContentW) / 2);

  let svg = "";
  const heatHeight = 7 * cellStep + 36; // 7 rows + labels

  // Section title
  svg += `<text x="${chartWidth / 2}" y="${yOffset + 16}" font-size="11" fill="${t.textDim}"
    text-anchor="middle" font-family="'Lexend','Segoe UI',sans-serif"
    letter-spacing="3" font-weight="400">CONTRIBUTION  CALENDAR</text>`;

  const gridY = yOffset + 30;

  // Day-of-week labels
  const dowLabels = ["", "M", "", "W", "", "F", ""];
  dowLabels.forEach((lbl, i) => {
    if (lbl) {
      svg += `<text x="${heatOffsetX + labelW - 6}" y="${gridY + i * cellStep + cellSize - 1}"
        font-size="9" fill="${t.textDim}" text-anchor="end"
        font-family="'Lexend','Segoe UI',sans-serif" font-weight="300">${lbl}</text>`;
    }
  });

  // Cells
  const dateLookup = {};
  days.forEach(d => { dateLookup[d] = dayData[d].commits; });

  const cur = new Date(gridStart);
  for (let w = 0; w < totalWeeks && w < 60; w++) {
    for (let d = 0; d < 7; d++) {
      const key = cur.toISOString().slice(0, 10);
      const val = dateLookup[key] || 0;
      const x = heatOffsetX + labelW + w * cellStep;
      const y = gridY + d * cellStep;
      const color = quantize(val);

      svg += `<rect x="${x.toFixed(1)}" y="${y.toFixed(1)}"
        width="${cellSize}" height="${cellSize}" rx="2.5" fill="${color}"
        stroke="${t.patternStroke}" stroke-width="0.5" stroke-opacity="0.4">
        <title>${key}: ${val} commits</title></rect>`;

      cur.setUTCDate(cur.getUTCDate() + 1);
    }

    // Month labels at week boundaries
    if (cur.getUTCDate() <= 7) {
      const mon = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];
      const x = heatOffsetX + labelW + w * cellStep;
      svg += `<text x="${x.toFixed(1)}" y="${gridY - 4}" font-size="8.5" fill="${t.textDim}"
        font-family="'Lexend','Segoe UI',sans-serif" font-weight="300">${mon[cur.getUTCMonth()]}</text>`;
    }
  }

  return { svg, height: heatHeight + 10 };
}

// ═══════════════════════════════════════════════════════════════════════════════
// §7  MINI-MAP OVERVIEW — Compact full-range sparkline
// ═══════════════════════════════════════════════════════════════════════════════

function generateMinimapSVG(commitsArr, xStart, yOffset, width, height) {
  if (!CFG.minimap || commitsArr.length < 3) return "";
  const t = T();
  const n = commitsArr.length;
  const maxVal = Math.max(...commitsArr, 1);

  const pts = commitsArr.map((v, i) => ({
    x: xStart + (i / (n - 1)) * width,
    y: yOffset + height - (v / maxVal) * height * 0.85,
  }));

  const path = catmullRomToBezier(pts);
  const areaPath = `${path} L ${pts[n - 1].x.toFixed(2)} ${yOffset + height} L ${pts[0].x.toFixed(2)} ${yOffset + height} Z`;

  return `
    <rect x="${xStart}" y="${yOffset}" width="${width}" height="${height}"
      rx="4" fill="${t.surface}" fill-opacity="0.6" stroke="${t.primaryDim}" stroke-width="0.8"/>
    <text x="${xStart + width / 2}" y="${yOffset - 4}" font-size="8" fill="${t.textDim}"
      text-anchor="middle" font-family="'Lexend','Segoe UI',sans-serif"
      letter-spacing="2" font-weight="300">OVERVIEW</text>
    <g clip-path="url(#minimapClip)">
      <path d="${areaPath}" fill="${t.primary}" fill-opacity="0.15"/>
      <path d="${path}" fill="none" stroke="${t.primary}" stroke-width="1.5" stroke-opacity="0.7"/>
    </g>
    <clipPath id="minimapClip">
      <rect x="${xStart}" y="${yOffset}" width="${width}" height="${height}" rx="4"/>
    </clipPath>`;
}

// ═══════════════════════════════════════════════════════════════════════════════
// §8  AUTHOR DISTRIBUTION — Donut chart
// ═══════════════════════════════════════════════════════════════════════════════

function generateAuthorDonutSVG(authorData, cx, cy, radius) {
  if (authorData.length === 0) return "";
  const t = T();
  const total = authorData.reduce((s, a) => s + a.count, 0);
  const colors = [t.primary, t.accent1, t.accent2, t.positive, t.negative, t.maLine, t.milestoneColor, t.textDim];

  let svg = "";
  let startAngle = -Math.PI / 2;
  const innerR = radius * 0.55;

  authorData.forEach((author, i) => {
    const sliceAngle = (author.count / total) * 2 * Math.PI;
    const endAngle = startAngle + sliceAngle;
    const largeArc = sliceAngle > Math.PI ? 1 : 0;

    const x1 = cx + radius * Math.cos(startAngle);
    const y1 = cy + radius * Math.sin(startAngle);
    const x2 = cx + radius * Math.cos(endAngle);
    const y2 = cy + radius * Math.sin(endAngle);
    const ix1 = cx + innerR * Math.cos(startAngle);
    const iy1 = cy + innerR * Math.sin(startAngle);
    const ix2 = cx + innerR * Math.cos(endAngle);
    const iy2 = cy + innerR * Math.sin(endAngle);

    const color = colors[i % colors.length];
    svg += `<path d="M ${ix1.toFixed(2)} ${iy1.toFixed(2)}
      L ${x1.toFixed(2)} ${y1.toFixed(2)}
      A ${radius} ${radius} 0 ${largeArc} 1 ${x2.toFixed(2)} ${y2.toFixed(2)}
      L ${ix2.toFixed(2)} ${iy2.toFixed(2)}
      A ${innerR} ${innerR} 0 ${largeArc} 0 ${ix1.toFixed(2)} ${iy1.toFixed(2)} Z"
      fill="${color}" fill-opacity="0.8" stroke="${t.surface}" stroke-width="1.5">
      <title>${escXml(author.name)}: ${author.count} commits (${Math.round(author.count / total * 100)}%)</title></path>`;

    startAngle = endAngle;
  });

  // Center label
  svg += `<text x="${cx}" y="${cy - 4}" font-size="9" fill="${t.textDim}" text-anchor="middle"
    font-family="'Lexend','Segoe UI',sans-serif" letter-spacing="1.5" font-weight="300">AUTHORS</text>`;
  svg += `<text x="${cx}" y="${cy + 14}" font-size="16" fill="${t.text}" text-anchor="middle"
    font-family="'Lexend','Segoe UI',sans-serif" font-weight="600">${authorData.length}</text>`;

  // Legend (right side)
  const legendX = cx + radius + 16;
  authorData.slice(0, 5).forEach((author, i) => {
    const ly = cy - radius + i * 18;
    const color = colors[i % colors.length];
    const name = author.name.length > 14 ? author.name.slice(0, 12) + "…" : author.name;
    svg += `<rect x="${legendX}" y="${ly - 6}" width="8" height="8" rx="2" fill="${color}" opacity="0.85"/>`;
    svg += `<text x="${legendX + 14}" y="${ly + 2}" font-size="9.5" fill="${t.textDim}"
      font-family="'Lexend','Segoe UI',sans-serif" font-weight="300">${escXml(name)} (${author.count})</text>`;
  });

  return svg;
}

// ═══════════════════════════════════════════════════════════════════════════════
// §9  MAIN SVG GENERATION — Adaptive, multi-section layout
// ═══════════════════════════════════════════════════════════════════════════════

function generateSVG(rawDayData, authorData) {
  const t = T();

  // ─── Fill gaps & auto-aggregate ──
  const filledDayData = fillMissingDays(rawDayData);
  const allDays = Object.keys(filledDayData).sort();
  const effectiveAgg = autoSelectAggregation(allDays.length);

  let aggData;
  if (effectiveAgg === "month") aggData = aggregateByMonth(filledDayData);
  else if (effectiveAgg === "week") aggData = aggregateByWeek(filledDayData);
  else aggData = filledDayData;

  const days = Object.keys(aggData).sort();
  const n = days.length;
  if (n === 0) { console.error("No data"); return ""; }

  console.log(`📊 Aggregation: ${effectiveAgg} (${n} data points)`);

  // ─── Extract series ──
  let cumulative = 0;
  const totalLines = [], commitsArr = [], addArr = [], delArr = [], changesArr = [], filesArr = [];

  days.forEach(day => {
    const d = aggData[day];
    cumulative = Math.max(0, cumulative + d.additions - d.deletions);
    totalLines.push(cumulative);
    commitsArr.push(d.commits);
    addArr.push(d.additions);
    delArr.push(d.deletions);
    changesArr.push(d.commits > 0 ? Math.round((d.additions + d.deletions) / d.commits) : 0);
    filesArr.push(d.files);
  });

  // ─── Moving averages ──
  const commitMA = movingAverage(commitsArr, CFG.maWindow);
  const changeMA = movingAverage(changesArr, CFG.maWindow);

  // ─── Smart annotations ──
  const annotations = detectAnnotations(days, { commits: commitsArr, additions: addArr, deletions: delArr });

  // ─── Scale computation (d3-inspired nice + headroom) ──
  const barValues = addArr.map((a, i) => a + delArr[i]);

  const maxLines   = niceScaleMax(Math.max(...totalLines, 0));
  const maxCommits = niceScaleMax(Math.max(...commitsArr, 0));
  const maxChanges = niceScaleMax(Math.max(...changesArr, 0));
  const maxBar     = niceScaleMax(Math.max(...barValues, 0));

  // ─── Layout computation ──
  const ML = 86;
  const MR = 78;
  const MT = 130;
  const MB = 112;
  const minInnerW = 880;
  const innerW = Math.max(minInnerW, n * Math.max(28, Math.min(58, 1400 / n)));
  const innerH = 360;
  const mainChartH = MT + innerH + MB;

  // Extra sections
  const sectionGap = 28;
  let extraH = 0;

  // Heatmap
  const heatmapResult = generateHeatmapSVG(rawDayData, mainChartH + sectionGap, innerW + ML + MR);
  extraH += heatmapResult.height > 0 ? heatmapResult.height + sectionGap : 0;

  // Author donut + Minimap + Stats panel
  const bottomPanelH = 130;
  const hasBottomPanel = authorData.length > 0 || CFG.minimap;
  if (hasBottomPanel) extraH += bottomPanelH + sectionGap;

  const W = innerW + ML + MR;
  const H = mainChartH + extraH + 30; // 30 = bottom padding with waves

  // ─── Scales ──
  const xOf = i => ML + i * (innerW / Math.max(n - 1, 1));
  const yOf = (v, max) => MT + innerH - (v / max) * innerH;

  // Clamp function for outliers exceeding adaptive max
  const clampY = (v, max) => {
    if (v > max) return MT - 8; // draw slightly above chart with special marker
    return yOf(v, max);
  };

  // ─── Point arrays (all values guaranteed within chart by niceScaleMax) ──
  const ptLines   = days.map((_, i) => ({ x: xOf(i), y: yOf(totalLines[i], maxLines) }));
  const ptCommits = days.map((_, i) => ({ x: xOf(i), y: yOf(commitsArr[i], maxCommits) }));
  const ptChanges = days.map((_, i) => ({ x: xOf(i), y: yOf(changesArr[i], maxChanges) }));

  // MA points (always within normal scale)
  const ptCommitMA = commitMA.map((v, i) => ({
    x: xOf(i),
    y: yOf(Math.min(v, maxCommits), maxCommits),
  }));
  const ptChangeMA = changeMA.map((v, i) => ({
    x: xOf(i),
    y: yOf(Math.min(v, maxChanges), maxChanges),
  }));

  // ─── Smooth paths (clamped to chart bounds as safety net) ──
  const pathLines   = catmullRomToBezier(ptLines, MT, MT + innerH);
  const pathCommits = catmullRomToBezier(ptCommits, MT, MT + innerH);
  const pathChanges = catmullRomToBezier(ptChanges, MT, MT + innerH);
  const areaLines   = `${pathLines} L ${ptLines[n - 1].x.toFixed(2)} ${MT + innerH} L ${ptLines[0].x.toFixed(2)} ${MT + innerH} Z`;

  // MA paths (clamped)
  const pathCommitMA = CFG.maWindow > 1 ? catmullRomToBezier(ptCommitMA, MT, MT + innerH) : "";
  const pathChangeMA = CFG.maWindow > 1 ? catmullRomToBezier(ptChangeMA, MT, MT + innerH) : "";

  // ─── Adaptive bar width ──
  const rawBarW = (innerW / n) * 0.55;
  const bw = Math.max(3, Math.min(24, rawBarW));

  // ─── Bars (additions + deletions) ──
  let bars = "";
  days.forEach((_, i) => {
    const total = addArr[i] + delArr[i];
    if (!total) return;
    const fullH = Math.min((total / maxBar) * innerH * 0.72, innerH);
    const addH = (addArr[i] / total) * fullH;
    const delH = fullH - addH;
    const bx = xOf(i) - bw / 2;
    const by = MT + innerH - fullH;
    if (delH > 0.5)
      bars += `<rect x="${bx.toFixed(1)}" y="${by.toFixed(1)}" width="${bw}" height="${delH.toFixed(1)}"
        fill="url(#gDelBar)" rx="${Math.min(2, bw / 3)}" opacity="0.78"/>`;
    if (addH > 0.5)
      bars += `<rect x="${bx.toFixed(1)}" y="${(by + delH).toFixed(1)}" width="${bw}" height="${addH.toFixed(1)}"
        fill="url(#gAddBar)" rx="${Math.min(2, bw / 3)}" opacity="0.82"/>`;
  });

  // ─── Data-point circles (simple — scale guarantees all points fit) ──
  let ciCommits = "", ciChanges = "";

  ptCommits.forEach((pt, i) => {
    if (commitsArr[i] > 0) {
      const radius = 3 + Math.min(2.5, (commitsArr[i] / maxCommits) * 2.5);
      ciCommits += `<circle cx="${pt.x.toFixed(1)}" cy="${pt.y.toFixed(1)}" r="${radius.toFixed(1)}"
        fill="${t.accent1}" stroke="${t.accent1Light}" stroke-width="1" filter="url(#glowOrange)" opacity="0.9">
        <title>${days[i]}: ${commitsArr[i]} commits</title></circle>`;
    }
  });

  ptChanges.forEach((pt, i) => {
    if (changesArr[i] > 0) {
      ciChanges += `<circle cx="${pt.x.toFixed(1)}" cy="${pt.y.toFixed(1)}" r="3.5"
        fill="${t.accent2}" stroke="${t.accent2Light}" stroke-width="0.8" filter="url(#glowCyan)" opacity="0.85">
        <title>${days[i]}: ${changesArr[i]} changes/commit</title></circle>`;
    }
  });

  // ─── Y-axis grid + labels ──
  const leftTicks = niceTicks(maxLines, 5);
  const rightTicks = niceTicks(maxCommits, 5);

  let yGrid = "", yLabelsLeft = "", yLabelsRight = "";

  leftTicks.forEach(val => {
    const y = yOf(val, maxLines);
    const isBase = val === 0;
    yGrid += `<line x1="${ML}" y1="${y.toFixed(1)}" x2="${ML + innerW}" y2="${y.toFixed(1)}"
      stroke="${t.grid}" stroke-width="${isBase ? 1.4 : 0.5}"
      stroke-opacity="${isBase ? 0.9 : 0.3}" stroke-dasharray="${isBase ? "none" : "6,6"}"/>`;
    yLabelsLeft += `<text x="${ML - 12}" y="${(y + 4).toFixed(1)}" font-size="11" fill="${t.primaryLight}"
      text-anchor="end" font-family="'Lexend','Segoe UI',sans-serif" font-weight="300">${fmtK(val)}</text>`;
  });

  rightTicks.forEach(val => {
    const y = yOf(val, maxCommits);
    yLabelsRight += `<text x="${ML + innerW + 12}" y="${(y + 4).toFixed(1)}" font-size="11" fill="${t.accent1}"
      text-anchor="start" font-family="'Lexend','Segoe UI',sans-serif" font-weight="300" opacity="0.85">${val}</text>`;
  });

  // ─── X-axis labels ──
  let xLabels = "";
  const MAX_X_LABELS = Math.min(16, n);
  const labelIndices = new Set([0, n - 1]);
  if (n > 2) {
    const step = Math.ceil((n - 1) / (MAX_X_LABELS - 1));
    for (let i = step; i < n - 1; i += step) labelIndices.add(i);
  }
  const sortedLI = [...labelIndices].sort((a, b) => a - b);
  const minLabelGap = 50;
  const filteredLI = [sortedLI[0]];
  for (let k = 1; k < sortedLI.length; k++) {
    if (xOf(sortedLI[k]) - xOf(filteredLI[filteredLI.length - 1]) >= minLabelGap) filteredLI.push(sortedLI[k]);
  }

  const labelBaseY = MT + innerH + 24;
  filteredLI.forEach(i => {
    const x = xOf(i);
    xLabels += `<text x="${x.toFixed(1)}" y="${labelBaseY}" font-size="10" fill="${t.primaryLight}"
      text-anchor="end" font-family="'Lexend','Segoe UI',sans-serif" font-weight="300"
      transform="rotate(-42, ${x.toFixed(1)}, ${labelBaseY})">${formatLabel(days[i], effectiveAgg)}</text>`;
  });

  // ─── Annotation SVG ──
  let annotSVG = "";
  const usedYZones = []; // prevent overlap

  function findFreeY(baseY, height) {
    let y = baseY;
    let attempts = 0;
    while (attempts < 10) {
      const conflict = usedYZones.some(z => y < z.bottom && y + height > z.top);
      if (!conflict) { usedYZones.push({ top: y, bottom: y + height }); return y; }
      y -= height + 6;
      attempts++;
    }
    return y;
  }

  annotations.forEach(ann => {
    if (ann.type === "peak") {
      const pt = ptCommits[ann.index];
      const baseY = pt.isOutlier ? MT - 30 : pt.y - 45;
      const annotW = 130, annotH = 24;
      const annotY = findFreeY(Math.max(MT - 60, baseY), annotH);
      const rawX = pt.x - annotW / 2;
      const annotX = Math.max(ML, Math.min(ML + innerW - annotW, rawX));

      annotSVG += `
        <line x1="${pt.x.toFixed(1)}" y1="${(pt.isOutlier ? MT : pt.y - 12).toFixed(1)}"
          x2="${pt.x.toFixed(1)}" y2="${(annotY + annotH).toFixed(1)}"
          stroke="${t.annotation}" stroke-width="1.2" stroke-dasharray="4,4" opacity="0.65"/>
        <rect x="${annotX.toFixed(1)}" y="${annotY.toFixed(1)}"
          width="${annotW}" height="${annotH}" rx="6"
          fill="${t.surface}" stroke="${t.annotation}" stroke-width="1" opacity="0.96"/>
        <text x="${(annotX + annotW / 2).toFixed(1)}" y="${(annotY + 16).toFixed(1)}"
          font-size="10" fill="${t.annotationText}" text-anchor="middle"
          font-family="'Lexend','Segoe UI',sans-serif" font-weight="400"
          letter-spacing="0.5">⬡  ${escXml(ann.label)}</text>`;
    }

    if (ann.type === "streak") {
      const x1 = xOf(ann.startIndex);
      const x2 = xOf(ann.endIndex);
      const y = MT + innerH + 4;
      annotSVG += `
        <line x1="${x1.toFixed(1)}" y1="${y}" x2="${x2.toFixed(1)}" y2="${y}"
          stroke="${t.milestoneColor}" stroke-width="2.5" stroke-linecap="round" opacity="0.7"/>
        <circle cx="${x1.toFixed(1)}" cy="${y}" r="3" fill="${t.milestoneColor}" opacity="0.9"/>
        <circle cx="${x2.toFixed(1)}" cy="${y}" r="3" fill="${t.milestoneColor}" opacity="0.9"/>
        <text x="${((x1 + x2) / 2).toFixed(1)}" y="${y + 14}" font-size="8.5" fill="${t.milestoneColor}"
          text-anchor="middle" font-family="'Lexend','Segoe UI',sans-serif"
          font-weight="400" opacity="0.85">🔥 ${escXml(ann.label)}</text>`;
    }

    if (ann.type === "milestone") {
      const x = xOf(ann.index);
      const y = MT + innerH;
      annotSVG += `
        <line x1="${x.toFixed(1)}" y1="${MT}" x2="${x.toFixed(1)}" y2="${y}"
          stroke="${t.milestoneColor}" stroke-width="0.8" stroke-dasharray="3,6" opacity="0.35"/>`;
    }
  });

  // ─── Statistical overlay: mean line for commits ──
  let statOverlaySVG = "";
  const nonZeroCommits = commitsArr.filter(v => v > 0);
  const commitMean = nonZeroCommits.length > 0 ? nonZeroCommits.reduce((a, b) => a + b, 0) / nonZeroCommits.length : 0;
  if (commitMean > 0) {
    const meanY = yOf(commitMean, maxCommits);
    statOverlaySVG += `<line x1="${ML}" y1="${meanY.toFixed(1)}" x2="${ML + innerW}" y2="${meanY.toFixed(1)}"
      stroke="${t.accent1}" stroke-width="1" stroke-dasharray="8,4" opacity="0.3"/>
    <text x="${ML + innerW + 12}" y="${(meanY - 6).toFixed(1)}" font-size="8" fill="${t.accent1}"
      text-anchor="start" font-family="'Lexend','Segoe UI',sans-serif" font-weight="300" opacity="0.6">μ=${commitMean.toFixed(1)}</text>`;
  }

  // ─── Totals ──
  const totalCommits = commitsArr.reduce((a, b) => a + b, 0);
  const totalAdditions = addArr.reduce((a, b) => a + b, 0);
  const totalDeletions = delArr.reduce((a, b) => a + b, 0);
  const totalFiles = filesArr.reduce((a, b) => a + b, 0);
  const activeDays = commitsArr.filter(v => v > 0).length;

  // ─── Stats badge (top-right) ──
  const BD_W = 142, BD_H = 92;
  const bdX = W - MR - BD_W - 6;
  const bdY = 8;

  const badge = `
    <rect x="${bdX}" y="${bdY}" width="${BD_W}" height="${BD_H}" rx="10"
      fill="${t.surface}" fill-opacity="0.94" stroke="${t.annotation}" stroke-width="1.2"/>
    <text x="${bdX + BD_W / 2}" y="${bdY + 16}" font-size="8" fill="${t.textDim}"
      text-anchor="middle" font-family="'Lexend',sans-serif" letter-spacing="2" font-weight="400">TOTAL  COMMITS</text>
    <text x="${bdX + BD_W / 2}" y="${bdY + 44}" font-size="28" font-weight="700" fill="${t.text}"
      text-anchor="middle" font-family="'Lexend',sans-serif" filter="url(#glowTitle)">${totalCommits}</text>
    <line x1="${bdX + 16}" y1="${bdY + 52}" x2="${bdX + BD_W - 16}" y2="${bdY + 52}"
      stroke="${t.primaryDim}" stroke-width="0.8" stroke-opacity="0.5"/>
    <text x="${bdX + BD_W / 2 - 24}" y="${bdY + 66}" font-size="9" fill="${t.positive}"
      text-anchor="middle" font-family="'Lexend',sans-serif" font-weight="400">+${fmtK(totalAdditions)}</text>
    <text x="${bdX + BD_W / 2 + 24}" y="${bdY + 66}" font-size="9" fill="${t.negative}"
      text-anchor="middle" font-family="'Lexend',sans-serif" font-weight="400">-${fmtK(totalDeletions)}</text>
    <text x="${bdX + BD_W / 2}" y="${bdY + 82}" font-size="8" fill="${t.textDim}"
      text-anchor="middle" font-family="'Lexend',sans-serif" font-weight="300">${activeDays} active days · ${fmtK(totalFiles)} files</text>`;

  // ─── Legend ──
  const legendDefs = [
    { sym: "area", color: t.primary, label: "Total Lines" },
    { sym: "line", color: t.accent1, label: "Commits" },
    { sym: "line", color: t.accent2, label: "Chg/Commit" },
    { sym: "bar",  color: t.positive, label: "Additions" },
    { sym: "bar",  color: t.negative, label: "Deletions" },
  ];
  if (CFG.maWindow > 1) {
    legendDefs.push({ sym: "dash", color: t.maLine, label: `${CFG.maWindow}-${effectiveAgg === "day" ? "day" : effectiveAgg} MA` });
  }

  const SYM_W = 24, SYM_GAP = 7, ITEM_GAP = 28, CH_W = 7;
  const itemTotalW = legendDefs.map(d => SYM_W + SYM_GAP + d.label.length * CH_W);
  const legendTotalW = itemTotalW.reduce((a, b) => a + b, 0) + ITEM_GAP * (legendDefs.length - 1);
  const legendY = MT - 30;
  let lx = (W - legendTotalW) / 2;
  let legendSVG = "";

  legendDefs.forEach(d => {
    const symCY = legendY - 3;
    if (d.sym === "area") {
      legendSVG += `<rect x="${lx.toFixed(1)}" y="${(legendY - 10).toFixed(1)}" width="${SYM_W}" height="12" rx="3"
        fill="${d.color}" fill-opacity="0.45" stroke="${d.color}" stroke-width="1.2"/>`;
    } else if (d.sym === "line") {
      legendSVG += `<line x1="${lx.toFixed(1)}" y1="${symCY.toFixed(1)}" x2="${(lx + SYM_W).toFixed(1)}" y2="${symCY.toFixed(1)}"
        stroke="${d.color}" stroke-width="2.5" stroke-linecap="round"/>`;
      legendSVG += `<circle cx="${(lx + SYM_W / 2).toFixed(1)}" cy="${symCY.toFixed(1)}" r="3.5"
        fill="${d.color}" stroke="#fff" stroke-width="0.6" stroke-opacity="0.3"/>`;
    } else if (d.sym === "dash") {
      legendSVG += `<line x1="${lx.toFixed(1)}" y1="${symCY.toFixed(1)}" x2="${(lx + SYM_W).toFixed(1)}" y2="${symCY.toFixed(1)}"
        stroke="${d.color}" stroke-width="2" stroke-dasharray="5,3" stroke-linecap="round"/>`;
    } else {
      legendSVG += `<rect x="${(lx + 6).toFixed(1)}" y="${(legendY - 10).toFixed(1)}" width="12" height="12" rx="2"
        fill="${d.color}" opacity="0.85"/>`;
    }
    lx += SYM_W + SYM_GAP;
    legendSVG += `<text x="${lx.toFixed(1)}" y="${legendY.toFixed(1)}" font-size="11" fill="${t.annotationText}"
      font-family="'Lexend','Segoe UI',sans-serif" font-weight="400">${d.label}</text>`;
    lx += d.label.length * CH_W + ITEM_GAP;
  });

  // ─── Corner brackets ──
  const bLen = 22;
  const corners = [
    [ML, MT, 1, 1], [ML + innerW, MT, -1, 1],
    [ML, MT + innerH, 1, -1], [ML + innerW, MT + innerH, -1, -1]
  ].map(([cx, cy, dx, dy]) =>
    `<path d="M ${cx} ${(cy + dy * bLen).toFixed(1)} L ${cx} ${cy} L ${(cx + dx * bLen).toFixed(1)} ${cy}"
      fill="none" stroke="${t.primary}" stroke-width="2" stroke-opacity="0.85" stroke-linecap="round"/>`
  ).join("");

  // ─── Wave decoration ──
  const waves = Array.from({ length: 4 }, (_, k) => {
    const yo = H - 6 - k * 5;
    const amp = 7 - k * 1.5;
    const seg = W / 8;
    let d = `M 0 ${yo}`;
    for (let s = 0; s < 8; s++)
      d += ` Q ${(s * seg + seg * 0.5).toFixed(1)} ${(yo - amp).toFixed(1)} ${((s + 1) * seg).toFixed(1)} ${yo}`;
    return `<path d="${d}" fill="none" stroke="${t.textMuted}" stroke-width="${1.2 - k * 0.2}" stroke-opacity="${0.35 - k * 0.06}"/>`;
  }).join("\n");

  // ─── Header separator ──
  const headerSep = `<line x1="24" y1="${(MT - 12).toFixed(1)}" x2="${(W - 24).toFixed(1)}" y2="${(MT - 12).toFixed(1)}"
    stroke="url(#gBorder)" stroke-width="0.8" stroke-opacity="0.5"/>`;

  // ─── Axis labels ──
  const axisLabelLeft = `<text x="16" y="${(MT + innerH / 2).toFixed(1)}" font-size="9.5" fill="${t.primary}"
    text-anchor="middle" font-family="'Lexend','Segoe UI',sans-serif" letter-spacing="3" font-weight="300"
    transform="rotate(-90, 16, ${(MT + innerH / 2).toFixed(1)})">LINES</text>`;
  const axisLabelRight = `<text x="${(W - 14).toFixed(1)}" y="${(MT + innerH / 2).toFixed(1)}" font-size="9.5"
    fill="${t.accent1}" text-anchor="middle" font-family="'Lexend','Segoe UI',sans-serif"
    letter-spacing="3" font-weight="300" opacity="0.8"
    transform="rotate(90, ${(W - 14).toFixed(1)}, ${(MT + innerH / 2).toFixed(1)})">COMMITS</text>`;

  // ─── Bottom panels ──
  let bottomPanelSVG = "";
  if (hasBottomPanel) {
    const panelY = mainChartH + (heatmapResult.height > 0 ? heatmapResult.height + sectionGap : 0) + sectionGap;

    // Minimap
    if (CFG.minimap) {
      const mmW = Math.min(320, innerW * 0.35);
      const mmH = bottomPanelH - 20;
      const mmX = ML;
      bottomPanelSVG += generateMinimapSVG(commitsArr, mmX, panelY + 14, mmW, mmH);
    }

    // Author donut
    if (authorData.length > 0) {
      const donutR = 42;
      const donutCX = W - MR - 140;
      const donutCY = panelY + bottomPanelH / 2 + 4;
      bottomPanelSVG += generateAuthorDonutSVG(authorData, donutCX, donutCY, donutR);
    }
  }



  // ════════════════════════════════════════════════════════════════════════════

  return `<svg width="${W}" height="${H}" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}">
<defs>
  <style>
    @import url('https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;600;700&amp;display=swap');
    circle:hover { opacity: 1 !important; r: 6; }
    rect.bar-segment:hover { opacity: 1 !important; }
  </style>

  <linearGradient id="bg" x1="0%" y1="0%" x2="55%" y2="100%">
    <stop offset="0%"   stop-color="${t.bg[0]}"/>
    <stop offset="50%"  stop-color="${t.bg[1]}"/>
    <stop offset="100%" stop-color="${t.bg[2]}"/>
  </linearGradient>
  <linearGradient id="areaFill" x1="0%" y1="0%" x2="0%" y2="100%">
    <stop offset="0%"   stop-color="${t.primary}" stop-opacity="0.5"/>
    <stop offset="55%"  stop-color="${t.primaryDim}" stop-opacity="0.18"/>
    <stop offset="100%" stop-color="${t.bg[2]}" stop-opacity="0"/>
  </linearGradient>
  <linearGradient id="gCommits" x1="0%" y1="0%" x2="100%" y2="0%">
    <stop offset="0%"   stop-color="${t.accent1}"/>
    <stop offset="100%" stop-color="${t.accent1Light}"/>
  </linearGradient>
  <linearGradient id="gChanges" x1="0%" y1="0%" x2="100%" y2="0%">
    <stop offset="0%"   stop-color="${t.accent2}" stop-opacity="0.8"/>
    <stop offset="50%"  stop-color="${t.accent2}"/>
    <stop offset="100%" stop-color="${t.accent2Light}"/>
  </linearGradient>
  <linearGradient id="gBorder" x1="0%" y1="0%" x2="100%" y2="100%">
    <stop offset="0%"   stop-color="${t.border[0]}" stop-opacity="0.9"/>
    <stop offset="50%"  stop-color="${t.border[1]}" stop-opacity="0.6"/>
    <stop offset="100%" stop-color="${t.border[2]}" stop-opacity="0.9"/>
  </linearGradient>
  <linearGradient id="gHeader" x1="0%" y1="0%" x2="100%" y2="0%">
    <stop offset="0%"   stop-color="${t.bg[0]}" stop-opacity="0"/>
    <stop offset="20%"  stop-color="${t.headerBand}" stop-opacity="0.92"/>
    <stop offset="80%"  stop-color="${t.headerBand}" stop-opacity="0.92"/>
    <stop offset="100%" stop-color="${t.bg[0]}" stop-opacity="0"/>
  </linearGradient>
  <linearGradient id="gAddBar" x1="0%" y1="0%" x2="0%" y2="100%">
    <stop offset="0%"   stop-color="${t.positive}" stop-opacity="0.95"/>
    <stop offset="100%" stop-color="${t.positive}" stop-opacity="0.65"/>
  </linearGradient>
  <linearGradient id="gDelBar" x1="0%" y1="0%" x2="0%" y2="100%">
    <stop offset="0%"   stop-color="${t.negative}" stop-opacity="0.9"/>
    <stop offset="100%" stop-color="${t.negative}" stop-opacity="0.6"/>
  </linearGradient>

  <filter id="glowOrange" x="-120%" y="-120%" width="340%" height="340%">
    <feGaussianBlur stdDeviation="4" result="b"/>
    <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
  <filter id="glowCyan" x="-120%" y="-120%" width="340%" height="340%">
    <feGaussianBlur stdDeviation="3.5" result="b"/>
    <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
  <filter id="glowTitle">
    <feGaussianBlur stdDeviation="5" result="b"/>
    <feMerge><feMergeNode in="b"/><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
  <filter id="glowLine" x="-6%" y="-120%" width="112%" height="340%">
    <feGaussianBlur stdDeviation="4.5" result="b"/>
    <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
  <filter id="glowMA" x="-6%" y="-120%" width="112%" height="340%">
    <feGaussianBlur stdDeviation="3" result="b"/>
    <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>

  <pattern id="diamondTile" x="0" y="0" width="30" height="30" patternUnits="userSpaceOnUse">
    <path d="M15 2 L28 15 L15 28 L2 15 Z"
      fill="none" stroke="${t.patternStroke}" stroke-width="0.4" stroke-opacity="0.25"/>
  </pattern>

  <clipPath id="chartClip">
    <rect x="${ML}" y="${MT}" width="${innerW}" height="${innerH}"/>
  </clipPath>
</defs>

<!-- ════ BACKGROUND ════ -->
<rect width="${W}" height="${H}" fill="url(#bg)" rx="16"/>
<rect width="${W}" height="${H}" fill="url(#diamondTile)" rx="16"/>

<!-- ════ BORDERS ════ -->
<rect width="${W}" height="${H}" fill="none" rx="16" stroke="url(#gBorder)" stroke-width="2.2"/>
<rect x="5" y="5" width="${W - 10}" height="${H - 10}" fill="none" rx="13"
  stroke="${t.border[0]}" stroke-width="0.7" stroke-opacity="0.35"/>

<!-- ════ HEADER ════ -->
<rect x="0" y="0" width="${W}" height="${MT - 8}" fill="url(#gHeader)" rx="16"/>
${headerSep}

<!-- ════ TITLE ════ -->
<text x="${(ML + innerW / 2).toFixed(1)}" y="44" font-size="18" font-weight="600" fill="${t.text}"
  text-anchor="middle" font-family="'Lexend','Segoe UI',sans-serif"
  filter="url(#glowTitle)" letter-spacing="5">⬡  COMMIT  ANALYTICS  ⬡</text>
<text x="${(ML + innerW / 2).toFixed(1)}" y="62" font-size="9.5" fill="${t.textDim}" text-anchor="middle"
  font-family="'Lexend','Segoe UI',sans-serif" letter-spacing="3.5" font-weight="300">${CFG.repo}</text>

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
  stroke="${t.grid}" stroke-width="1.2" stroke-opacity="0.65"/>
<line x1="${ML}" y1="${MT + innerH}" x2="${ML + innerW}" y2="${MT + innerH}"
  stroke="${t.grid}" stroke-width="1.4" stroke-opacity="0.8"/>
<line x1="${ML + innerW}" y1="${MT}" x2="${ML + innerW}" y2="${MT + innerH}"
  stroke="${t.accent1}" stroke-width="0.9" stroke-opacity="0.28"/>

<!-- ════ X-AXIS ════ -->
${xLabels}

<!-- ════ CHART CONTENT ════ -->
<g clip-path="url(#chartClip)">
  <!-- Total lines: area + stroke -->
  <path d="${areaLines}" fill="url(#areaFill)"/>
  <path d="${pathLines}" fill="none" stroke="${t.primary}" stroke-width="2" stroke-opacity="0.7"/>

  <!-- Bars -->
  ${bars}

  <!-- Commits line — glow + crisp -->
  <path d="${pathCommits}" fill="none" stroke="${t.accent1}" stroke-width="9" stroke-opacity="0.06" filter="url(#glowLine)"/>
  <path d="${pathCommits}" fill="none" stroke="url(#gCommits)" stroke-width="2.8" stroke-linecap="round"/>

  <!-- Changes/commit line -->
  <path d="${pathChanges}" fill="none" stroke="${t.accent2}" stroke-width="9" stroke-opacity="0.06" filter="url(#glowLine)"/>
  <path d="${pathChanges}" fill="none" stroke="url(#gChanges)" stroke-width="2.5" stroke-linecap="round"/>

  <!-- Moving averages -->
  ${pathCommitMA ? `<path d="${pathCommitMA}" fill="none" stroke="${t.maLine}" stroke-width="2" stroke-dasharray="6,4" stroke-opacity="0.55" filter="url(#glowMA)"/>` : ""}
  ${pathChangeMA ? `<path d="${pathChangeMA}" fill="none" stroke="${t.maLineLight}" stroke-width="1.5" stroke-dasharray="5,4" stroke-opacity="0.4"/>` : ""}

  <!-- Statistical overlay -->
  ${statOverlaySVG}

  <!-- Data point circles (inside clip for safety) -->
  ${ciCommits}
  ${ciChanges}
</g>

<!-- ════ ANNOTATIONS ════ -->
${annotSVG}

<!-- ════ CORNER BRACKETS ════ -->
${corners}

<!-- ════ HEATMAP ════ -->
${heatmapResult.svg}

<!-- ════ BOTTOM PANELS ════ -->
${bottomPanelSVG}

<!-- ════ WAVES ════ -->
${waves}

</svg>`;
}

// ═══════════════════════════════════════════════════════════════════════════════
// §10  MAIN
// ═══════════════════════════════════════════════════════════════════════════════

(async () => {
  try {
    console.log("⬡ Commit Analytics v2 — Adaptive Chart Generator");
    console.log(`  Repo: ${CFG.repo}`);
    console.log(`  Mode: ${CFG.scaleMode} scale · ${CFG.aggregation} aggregation · MA(${CFG.maWindow})`);
    console.log(`  Theme: ${CFG.theme} · Heatmap: ${CFG.heatmap} · Minimap: ${CFG.minimap}`);
    console.log("");

    const fetch = await getFetch();
    const commits = await fetchAllCommits(fetch);

    console.log(`📊 Fetching per-commit stats (${commits.length} commits, ${CFG.concurrency} concurrent)...`);
    const stats = await batchFetchStats(fetch, commits);

    const dayData = groupByDay(commits, stats);
    const authorData = extractAuthorMap(commits);

    console.log(`👥 Top contributors: ${authorData.map(a => `${a.name}(${a.count})`).join(", ")}`);

    const svg = generateSVG(dayData, authorData);

    // Ensure output directory exists
    if (!fs.existsSync(CFG.outputDir)) fs.mkdirSync(CFG.outputDir, { recursive: true });

    const outPath = `${CFG.outputDir}/commit-chart.svg`;
    fs.writeFileSync(outPath, svg);
    console.log(`\n✅ ${outPath} generated — ⬡ Hollow Knight Blue`);
    console.log(`   Size: ${(svg.length / 1024).toFixed(1)} KB`);
  } catch (err) {
    console.error("❌ Error:", err);
    process.exit(1);
  }
})();