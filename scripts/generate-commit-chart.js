// scripts/generate-commit-chart.js
const fs = require("fs");

async function getFetch() {
  const { default: fetch } = await import("node-fetch");
  return fetch;
}

// Token GitHub
const token = process.env.GITHUB_TOKEN;
if (!token) {
  console.error("❌ Vui lòng đặt biến môi trường GITHUB_TOKEN trước khi chạy script.");
  process.exit(1);
}

// Lấy commit
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

// Nhóm commit theo ngày
function groupByDay(commits) {
  const map = {};
  commits.forEach(c => {
    const day = c.commit.author.date.slice(0, 10); // YYYY-MM-DD
    map[day] = (map[day] || 0) + 1;
  });
  return map;
}

// Tạo SVG line chart kiểu anime
function generateSVGAnime(data) {
  const days = Object.keys(data).sort();
  const values = Object.values(data);
  const max = Math.max(...values, 1);

  const width = Math.max(1200, days.length*15);
  const height = 500;
  const margin = 60;

  // Trục X/Y
  let axes = `
    <line x1="${margin}" y1="${height-margin}" x2="${width-margin}" y2="${height-margin}" stroke="#333" stroke-width="2"/>
    <line x1="${margin}" y1="${margin}" x2="${margin}" y2="${height-margin}" stroke="#333" stroke-width="2"/>
  `;

  // Gridline Y
  let yGrid = '';
  const step = Math.ceil(max/10) || 1;
  for (let i=0;i<=max;i+=step){
    const y = height - margin - (i/max)*(height-margin*2);
    yGrid += `<line x1="${margin}" y1="${y}" x2="${width-margin}" y2="${y}" stroke="#ddd" stroke-width="1"/>
              <text x="${margin-10}" y="${y+4}" font-size="12" text-anchor="end">${i}</text>`;
  }

  // Các điểm commit và đường nối
  let points = '';
  let path = '';
  days.forEach((day, i) => {
    const value = data[day];
    const x = margin + i*((width-margin*2)/(days.length-1 || 1));
    const y = height - margin - (value/max)*(height-margin*2);
    points += `<circle cx="${x}" cy="${y}" r="5" fill="#ff69b4">
                 <title>${day}: ${value} commits</title>
               </circle>`;
    path += i===0 ? `M ${x} ${y} ` : `L ${x} ${y} `;
  });

  // Path màu gradient kiểu anime
  const svg = `
<svg width="${width}" height="${height}" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="animeGradient" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#ff69b4"/>
      <stop offset="50%" stop-color="#ffb347"/>
      <stop offset="100%" stop-color="#1e90ff"/>
    </linearGradient>
  </defs>
  ${yGrid}
  ${axes}
  <path d="${path}" fill="none" stroke="url(#animeGradient)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
  ${points}
</svg>
  `;
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