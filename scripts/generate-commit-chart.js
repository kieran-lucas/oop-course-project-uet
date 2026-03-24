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

// Nhóm theo tuần để nhiều cột hơn
function groupByWeek(commits) {
  const map = {};
  commits.forEach(c => {
    const d = new Date(c.commit.author.date);
    const year = d.getFullYear();
    const week = Math.ceil(((d - new Date(year,0,1))/86400000 + new Date(year,0,1).getDay()+1)/7);
    const key = `${year}-W${week}`;
    map[key] = (map[key] || 0) + 1;
  });
  return map;
}

function generateSVG(data) {
  const weeks = Object.keys(data).sort();
  const values = Object.values(data);
  const max = Math.max(...values, 1);
  const avg = values.reduce((a,b)=>a+b,0)/values.length;

  const width = Math.max(1200, weeks.length*25);
  const height = 400;
  const margin = 50;
  const barWidth = (width - margin*2) / weeks.length;

  // Gridline Y
  let yGrid = '';
  const step = Math.ceil(max/10) || 1;
  for (let i=0;i<=max;i+=step){
    const y = height - margin - (i/max)*(height-margin*2);
    yGrid += `<line x1="${margin}" y1="${y}" x2="${width-margin}" y2="${y}" stroke="#ddd" stroke-width="1"/>
              <text x="${margin-10}" y="${y+4}" font-size="12" text-anchor="end">${i}</text>`;
  }

  // Các cột
  let bars = '';
  weeks.forEach((week, i) => {
    const value = data[week];
    const barHeight = (value/max)*(height-margin*2);
    const color = value>=avg ? "#ff6b6b" : "#7aa2f7";
    const x = margin + i*barWidth;
    const y = height - margin - barHeight;
    bars += `
      <rect x="${x}" y="${y}" width="${barWidth*0.8}" height="${barHeight}" fill="${color}" />
      <text x="${x+barWidth*0.4}" y="${height-5}" font-size="10" text-anchor="middle" transform="rotate(45 ${x+barWidth*0.4},${height-5})">${week}</text>
      <title>${week}: ${value} commits</title>
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

(async () => {
  try {
    console.log("🚀 Starting commit chart generation...");
    const commits = await fetchCommits();
    const grouped = groupByWeek(commits);
    const svg = generateSVG(grouped);

    fs.writeFileSync("scripts/commit-chart.svg", svg);
    console.log("✅ commit-chart.svg đã được tạo thành công trong scripts!");
  } catch (err) {
    console.error("❌ Lỗi:", err);
  }
})();