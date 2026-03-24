// scripts/generate-commit-chart.js

const fs = require("fs");

// Lấy fetch động từ node-fetch
async function getFetch() {
  const { default: fetch } = await import("node-fetch");
  return fetch;
}

// Lấy token từ biến môi trường (an toàn)
const token = process.env.GITHUB_TOKEN; 
if (!token) {
  console.error("❌ Vui lòng đặt biến môi trường GITHUB_TOKEN trước khi chạy script.");
  process.exit(1);
}

// Lấy tất cả commit
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

    if (!res.ok) {
      throw new Error(`HTTP error! status: ${res.status}`);
    }

    const data = await res.json();
    if (data.length === 0) break;

    allCommits = allCommits.concat(data);
    page++;
  }

  console.log(`Total commits fetched: ${allCommits.length}`);
  return allCommits;
}

// Nhóm commit theo tháng YYYY-MM
function groupByMonth(commits) {
  const map = {};
  commits.forEach(c => {
    const month = c.commit.author.date.slice(0, 7); // YYYY-MM
    map[month] = (map[month] || 0) + 1;
  });
  return map;
}

// Tạo SVG với trục X hiển thị tháng
function generateSVG(data) {
  const months = Object.keys(data).sort();
  const max = Math.max(...Object.values(data), 1);
  const width = 1200;
  const height = 400;
  const barWidth = width / months.length;

  let bars = "";
  const avg = Object.values(data).reduce((a,b) => a+b,0)/months.length;

  months.forEach((month, i) => {
    const value = data[month];
    const barHeight = (value / max) * (height - 50);
    const color = value >= avg ? "#ff6b6b" : "#7aa2f7"; // highlight cao hơn trung bình

    bars += `
      <rect 
        x="${i * barWidth}" 
        y="${height - barHeight - 30}" 
        width="${barWidth - 2}" 
        height="${barHeight}" 
        fill="${color}"
      />
      <text x="${i * barWidth + barWidth/2}" y="${height - 10}" font-size="12" text-anchor="middle" transform="rotate(45 ${i * barWidth + barWidth/2},${height-10})">
        ${month}
      </text>
    `;
  });

  return `
    <svg width="${width}" height="${height}" xmlns="http://www.w3.org/2000/svg">
      <line x1="0" y1="${height-30}" x2="${width}" y2="${height-30}" stroke="#000" stroke-width="1"/>
      ${bars}
    </svg>
  `;
}

// Main
(async () => {
  try {
    console.log("🚀 Starting commit chart generation...");
    const commits = await fetchCommits();
    const grouped = groupByMonth(commits);
    const svg = generateSVG(grouped);

    fs.writeFileSync("scripts/commit-chart.svg", svg);
    console.log("✅ commit-chart.svg đã được tạo thành công trong scripts!");
  } catch (err) {
    console.error("❌ Lỗi khi tạo chart:", err);
  }
})();