// scripts/generate-commit-chart-html.js
const fs = require("fs");

async function getFetch() {
  const { default: fetch } = await import("node-fetch");
  return fetch;
}

// Token GitHub từ biến môi trường
const token = process.env.GITHUB_TOKEN;
if (!token) {
  console.error("❌ Vui lòng đặt biến môi trường GITHUB_TOKEN trước khi chạy script.");
  process.exit(1);
}

// Lấy toàn bộ commit
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
          "User-Agent": "commit-chart-script"
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

// Nhóm commit theo tháng YYYY-MM
function groupByMonth(commits) {
  const map = {};
  commits.forEach(c => {
    const month = c.commit.author.date.slice(0, 7);
    map[month] = (map[month] || 0) + 1;
  });
  return map;
}

// Tạo SVG chart với trục X/Y, gridline, highlight
function generateSVGChart(data) {
  const months = Object.keys(data).sort();
  const values = Object.values(data);
  const max = Math.max(...values, 1);
  const avg = values.reduce((a,b)=>a+b,0)/values.length;

  const width = Math.max(1200, months.length*40);
  const height = 400;
  const margin = 50;
  const barWidth = (width - margin*2) / months.length;

  // Tạo gridline Y
  let yGrid = '';
  const step = Math.ceil(max/10) || 1;
  for (let i=0;i<=max;i+=step){
    const y = height - margin - (i/max)*(height-margin*2);
    yGrid += `<line x1="${margin}" y1="${y}" x2="${width-margin}" y2="${y}" stroke="#ddd" stroke-width="1"/>
              <text x="${margin-10}" y="${y+4}" font-size="12" text-anchor="end">${i}</text>`;
  }

  // Tạo các cột
  let bars = '';
  months.forEach((month, i) => {
    const value = data[month];
    const barHeight = (value/max)*(height-margin*2);
    const color = value>=avg ? "#ff6b6b" : "#7aa2f7";
    const x = margin + i*barWidth;
    const y = height - margin - barHeight;
    bars += `
      <rect x="${x}" y="${y}" width="${barWidth*0.8}" height="${barHeight}" fill="${color}" />
      <text x="${x+barWidth*0.4}" y="${height-5}" font-size="12" text-anchor="middle" transform="rotate(45 ${x+barWidth*0.4},${height-5})">${month}</text>
      <title>${month}: ${value} commits</title>
    `;
  });

  return `
<svg width="${width}" height="${height}" xmlns="http://www.w3.org/2000/svg">
  ${yGrid}
  <line x1="${margin}" y1="${height-margin}" x2="${width-margin}" y2="${height-margin}" stroke="#000"/>
  ${bars}
</svg>
  `;
}

// Tạo HTML chứa SVG + tiêu đề
function generateHTML(svgContent) {
  return `
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Commit History Chart</title>
<style>
  body { font-family: Arial, sans-serif; padding: 20px; }
  h1 { text-align: center; }
  svg { border: 1px solid #ccc; display: block; margin: auto; }
</style>
</head>
<body>
<h1>Commit History Chart (Monthly)</h1>
${svgContent}
</body>
</html>
  `;
}

// Main
(async () => {
  try {
    console.log("🚀 Starting commit history generation...");
    const commits = await fetchCommits();
    const grouped = groupByMonth(commits);
    const svg = generateSVGChart(grouped);
    const html = generateHTML(svg);
    fs.writeFileSync("scripts/commit-history.html", html);
    console.log("✅ commit-history.html đã được tạo thành công trong scripts!");
  } catch (err) {
    console.error("❌ Lỗi:", err);
  }
})();