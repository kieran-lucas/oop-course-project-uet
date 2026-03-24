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

async function fetchCommits() {
  const fetch = await getFetch();
  let page = 1;
  let allCommits = [];

  while (true) {
    console.log(`Fetching commits page ${page}...`);
    const res = await fetch(
      `https://api.github.com/repos/kieran-lucas/oop-course-project-uet/commits?per_page=100&page=${page}`,
      {
        headers: {
          Authorization: `token ${token}`,
          "User-Agent": "anime-commit-chart"
        }
      }
    );
    if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
    const data = await res.json();
    if (data.length === 0) break;
    allCommits = allCommits.concat(data);
    page++;
  }

  console.log(`✅ Total commits fetched: ${allCommits.length}`);
  return allCommits;
}

function groupByDay(commits) {
  const map = {};
  commits.forEach(c => {
    const day = c.commit.author.date.slice(0, 10);
    map[day] = (map[day] || 0) + 1;
  });
  return map;
}

// Catmull-Rom → Cubic Bezier để tạo smooth curve
function catmullRomToBezier(points) {
  if (points.length < 2) return "";
  let d = `M ${points[0].x} ${points[0].y}`;
  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[Math.max(i - 1, 0)];
    const p1 = points[i];
    const p2 = points[i + 1];
    const p3 = points[Math.min(i + 2, points.length - 1)];
    const cp1x = p1.x + (p2.x - p0.x) / 6;
    const cp1y = p1.y + (p2.y - p0.y) / 6;
    const cp2x = p2.x - (p3.x - p1.x) / 6;
    const cp2y = p2.y - (p3.y - p1.y) / 6;
    d += ` C ${cp1x} ${cp1y}, ${cp2x} ${cp2y}, ${p2.x} ${p2.y}`;
  }
  return d;
}

function formatDateLabel(dateStr) {
  const d = new Date(dateStr + "T00:00:00");
  const months = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];
  return `${d.getDate()} ${months[d.getMonth()]}`;
}

function generateSVGAnime(data) {
  const days = Object.keys(data).sort();
  const values = days.map(d => data[d]);
  const max = Math.max(...values, 1);
  const totalCommits = values.reduce((a, b) => a + b, 0);

  const marginL = 70, marginR = 40, marginT = 80, marginB = 90;
  const innerW = Math.max(900, days.length * 60);
  const width = innerW + marginL + marginR;
  const height = 480;
  const innerH = height - marginT - marginB;

  // Tính tọa độ điểm
  const pts = days.map((day, i) => ({
    x: marginL + i * (innerW / Math.max(days.length - 1, 1)),
    y: marginT + innerH - (data[day] / max) * innerH,
    value: data[day],
    day
  }));

  // Path smooth
  const linePath = catmullRomToBezier(pts);

  // Area fill path (đóng xuống baseline)
  const areaPath = linePath +
    ` L ${pts[pts.length - 1].x} ${marginT + innerH}` +
    ` L ${pts[0].x} ${marginT + innerH} Z`;

  // Grid lines Y
  let yGridLines = '';
  const gridCount = 5;
  for (let i = 0; i <= gridCount; i++) {
    const val = Math.round((i / gridCount) * max);
    const y = marginT + innerH - (val / max) * innerH;
    yGridLines += `
    <line x1="${marginL}" y1="${y}" x2="${marginL + innerW}" y2="${y}"
          stroke="#ffffff" stroke-width="0.5" stroke-opacity="${i === 0 ? 0.25 : 0.08}"
          stroke-dasharray="${i === 0 ? 'none' : '4,4'}"/>
    <text x="${marginL - 10}" y="${y + 4}" font-size="11" fill="#a0a8c8"
          text-anchor="end" font-family="'Courier New', monospace">${val}</text>`;
  }

  // X-axis labels — hiện tất cả hoặc mỗi N ngày tùy số lượng
  let xLabels = '';
  const labelStep = Math.ceil(days.length / 20);
  pts.forEach((pt, i) => {
    if (i % labelStep === 0 || i === days.length - 1) {
      xLabels += `
      <text x="${pt.x}" y="${marginT + innerH + 20}" font-size="10" fill="#7a85b0"
            text-anchor="middle" font-family="'Courier New', monospace"
            transform="rotate(-40, ${pt.x}, ${marginT + innerH + 20})">${formatDateLabel(pt.day)}</text>`;
    }
  });

  // Các điểm dữ liệu với glow
  let circles = '';
  pts.forEach(pt => {
    const isMax = pt.value === max;
    circles += `
    <circle cx="${pt.x}" cy="${pt.y}" r="${isMax ? 8 : 5}"
            fill="${isMax ? '#ffe066' : '#e040fb'}"
            filter="url(#neonGlow)">
      <title>${pt.day}: ${pt.value} commits</title>
    </circle>
    <circle cx="${pt.x}" cy="${pt.y}" r="${isMax ? 4 : 3}" fill="#fff" opacity="0.9"/>`;
  });

  // Sakura petals trang trí random
  function sakuraPetal(cx, cy, size, angle, opacity) {
    return `<ellipse cx="${cx}" cy="${cy}" rx="${size}" ry="${size * 0.5}"
                     fill="#ffadd2" opacity="${opacity}"
                     transform="rotate(${angle}, ${cx}, ${cy})"/>`;
  }
  let sakura = '';
  const petalData = [
    [95, 35, 7, 20, 0.18], [180, 18, 5, 55, 0.12], [width-80, 28, 6, -30, 0.15],
    [width-150, 45, 4, 70, 0.1], [width/2-60, 22, 8, 10, 0.13], [width/2+80, 38, 5, -60, 0.11],
    [130, 55, 4, 45, 0.09], [width-200, 15, 6, 30, 0.14]
  ];
  petalData.forEach(([cx, cy, size, angle, opacity]) => {
    sakura += sakuraPetal(cx, cy, size, angle, opacity);
    sakura += sakuraPetal(cx + size * 0.8, cy - size * 0.4, size * 0.7, angle + 60, opacity * 0.9);
    sakura += sakuraPetal(cx - size * 0.6, cy - size * 0.5, size * 0.6, angle - 40, opacity * 0.8);
  });

  // Stars trang trí
  let stars = '';
  const starPositions = [
    [50, 22], [width-60, 20], [width/3, 15], [2*width/3, 30],
    [width/4, 45], [3*width/4, 12], [width-100, 55], [80, 50]
  ];
  starPositions.forEach(([sx, sy]) => {
    const r = Math.random() * 1.5 + 0.8;
    stars += `<circle cx="${sx}" cy="${sy}" r="${r.toFixed(1)}" fill="#fff" opacity="0.4"/>`;
  });

  // Annotation cho peak
  const peakPt = pts.reduce((a, b) => a.value > b.value ? a : b);
  const peakAnnotation = `
  <line x1="${peakPt.x}" y1="${peakPt.y - 12}" x2="${peakPt.x}" y2="${peakPt.y - 40}"
        stroke="#ffe066" stroke-width="1" stroke-dasharray="3,3" opacity="0.7"/>
  <rect x="${peakPt.x - 42}" y="${peakPt.y - 58}" width="84" height="20"
        rx="4" fill="#1a1040" stroke="#ffe066" stroke-width="0.8" opacity="0.9"/>
  <text x="${peakPt.x}" y="${peakPt.y - 44}" font-size="10.5" fill="#ffe066"
        text-anchor="middle" font-family="'Courier New', monospace">⭐ peak: ${peakPt.value}</text>`;

  const svg = `<svg width="${width}" height="${height}" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}">
  <defs>
    <!-- Background gradient -->
    <linearGradient id="bgGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#0d0625"/>
      <stop offset="60%" stop-color="#0f0a2e"/>
      <stop offset="100%" stop-color="#0a1628"/>
    </linearGradient>

    <!-- Line gradient -->
    <linearGradient id="lineGrad" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%"   stop-color="#ff6ec7"/>
      <stop offset="35%"  stop-color="#b060ff"/>
      <stop offset="65%"  stop-color="#60c4ff"/>
      <stop offset="100%" stop-color="#00f0c8"/>
    </linearGradient>

    <!-- Area fill gradient (vertical) -->
    <linearGradient id="areaGrad" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%"   stop-color="#b060ff" stop-opacity="0.35"/>
      <stop offset="60%"  stop-color="#ff6ec7" stop-opacity="0.08"/>
      <stop offset="100%" stop-color="#ff6ec7" stop-opacity="0"/>
    </linearGradient>

    <!-- Neon glow filter -->
    <filter id="neonGlow" x="-60%" y="-60%" width="220%" height="220%">
      <feGaussianBlur stdDeviation="3" result="blur"/>
      <feMerge>
        <feMergeNode in="blur"/>
        <feMergeNode in="blur"/>
        <feMergeNode in="SourceGraphic"/>
      </feMerge>
    </filter>

    <!-- Soft glow for line -->
    <filter id="lineGlow" x="-5%" y="-60%" width="110%" height="220%">
      <feGaussianBlur stdDeviation="4" result="blur"/>
      <feMerge>
        <feMergeNode in="blur"/>
        <feMergeNode in="SourceGraphic"/>
      </feMerge>
    </filter>

    <!-- Text glow -->
    <filter id="textGlow">
      <feGaussianBlur stdDeviation="2.5" result="blur"/>
      <feMerge>
        <feMergeNode in="blur"/>
        <feMergeNode in="SourceGraphic"/>
      </feMerge>
    </filter>

    <!-- Clip path for chart area -->
    <clipPath id="chartClip">
      <rect x="${marginL}" y="${marginT}" width="${innerW}" height="${innerH}"/>
    </clipPath>

    <!-- Border gradient -->
    <linearGradient id="borderGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%"   stop-color="#ff6ec7" stop-opacity="0.6"/>
      <stop offset="50%"  stop-color="#b060ff" stop-opacity="0.4"/>
      <stop offset="100%" stop-color="#60c4ff" stop-opacity="0.6"/>
    </linearGradient>
  </defs>

  <!-- Background -->
  <rect width="${width}" height="${height}" fill="url(#bgGrad)" rx="12"/>

  <!-- Border glow -->
  <rect width="${width}" height="${height}" fill="none" rx="12"
        stroke="url(#borderGrad)" stroke-width="1.5"/>

  <!-- Stars -->
  ${stars}

  <!-- Sakura petals -->
  ${sakura}

  <!-- Title -->
  <text x="${marginL + innerW / 2}" y="38" font-size="17" font-weight="700"
        fill="#e8d5ff" text-anchor="middle"
        font-family="Georgia, 'Times New Roman', serif"
        filter="url(#textGlow)" letter-spacing="2">
    ✦ COMMIT HISTORY ✦
  </text>
  <text x="${marginL + innerW / 2}" y="56" font-size="10" fill="#6a5a90"
        text-anchor="middle" font-family="'Courier New', monospace" letter-spacing="3">
    kieran-lucas / oop-course-project-uet
  </text>

  <!-- Stats badge -->
  <rect x="${width - marginR - 130}" y="14" width="126" height="42" rx="6"
        fill="#ffffff" fill-opacity="0.04" stroke="#b060ff" stroke-width="0.8" stroke-opacity="0.5"/>
  <text x="${width - marginR - 67}" y="31" font-size="9" fill="#a07ac8"
        text-anchor="middle" font-family="'Courier New', monospace" letter-spacing="1">TOTAL COMMITS</text>
  <text x="${width - marginR - 67}" y="49" font-size="18" font-weight="bold"
        fill="#e0c0ff" text-anchor="middle" font-family="Georgia, serif"
        filter="url(#textGlow)">${totalCommits}</text>

  <!-- Y-axis label -->
  <text x="15" y="${marginT + innerH / 2}" font-size="10" fill="#6a5a90"
        text-anchor="middle" font-family="'Courier New', monospace" letter-spacing="2"
        transform="rotate(-90, 15, ${marginT + innerH / 2})">COMMITS / DAY</text>

  <!-- Grid -->
  ${yGridLines}

  <!-- X-axis baseline -->
  <line x1="${marginL}" y1="${marginT + innerH}" x2="${marginL + innerW}" y2="${marginT + innerH}"
        stroke="#ffffff" stroke-width="0.8" stroke-opacity="0.2"/>

  <!-- Y-axis line -->
  <line x1="${marginL}" y1="${marginT}" x2="${marginL}" y2="${marginT + innerH}"
        stroke="#ffffff" stroke-width="0.8" stroke-opacity="0.15"/>

  <!-- X labels -->
  ${xLabels}

  <!-- Area fill (clipped) -->
  <g clip-path="url(#chartClip)">
    <path d="${areaPath}" fill="url(#areaGrad)"/>

    <!-- Glow duplicate of line -->
    <path d="${linePath}" fill="none" stroke="url(#lineGrad)"
          stroke-width="6" stroke-linecap="round" stroke-linejoin="round"
          opacity="0.3" filter="url(#lineGlow)"/>

    <!-- Main line -->
    <path d="${linePath}" fill="none" stroke="url(#lineGrad)"
          stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
  </g>

  <!-- Data points (on top of clip) -->
  ${circles}

  <!-- Peak annotation -->
  ${peakAnnotation}

  <!-- Bottom decoration line -->
  <line x1="${marginL + 20}" y1="${height - 14}" x2="${marginL + innerW - 20}" y2="${height - 14}"
        stroke="url(#lineGrad)" stroke-width="0.5" opacity="0.3"/>
</svg>`;

  return svg;
}

(async () => {
  try {
    console.log("🚀 Starting anime commit chart...");
    const commits = await fetchCommits();
    const grouped = groupByDay(commits);
    const svg = generateSVGAnime(grouped);

    fs.writeFileSync("scripts/commit-chart.svg", svg);
    console.log("✅ commit-chart.svg anime đã được tạo trong scripts!");
  } catch (err) {
    console.error("❌ Lỗi:", err);
  }
})();