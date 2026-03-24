const fs = require("fs");

// Dynamic import node-fetch (ESM module)
async function getFetch() {
  const { default: fetch } = await import("node-fetch");
  return fetch;
}

const token = "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"; // Dán token của bạn ở đây

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

function groupByDate(commits) {
  const map = {};
  commits.forEach(c => {
    const date = c.commit.author.date.slice(0, 10);
    map[date] = (map[date] || 0) + 1;
  });
  return map;
}

function generateSVG(data) {
  const dates = Object.keys(data).sort();
  const max = Math.max(...Object.values(data), 1);
  const width = 800;
  const height = 200;
  const barWidth = width / dates.length;

  let bars = "";
  dates.forEach((date, i) => {
    const value = data[date];
    const barHeight = (value / max) * height;
    bars += `
      <rect 
        x="${i * barWidth}" 
        y="${height - barHeight}" 
        width="${barWidth - 1}" 
        height="${barHeight}" 
        fill="#7aa2f7"
      />
    `;
  });

  return `
    <svg width="${width}" height="${height}" xmlns="http://www.w3.org/2000/svg">
      ${bars}
    </svg>
  `;
}

(async () => {
  try {
    console.log("🚀 Starting commit chart generation...");
    const commits = await fetchCommits();
    const grouped = groupByDate(commits);
    const svg = generateSVG(grouped);

    fs.writeFileSync("scripts/commit-chart.svg", svg);
    console.log("✅ commit-chart.svg đã được tạo thành công!");
  } catch (err) {
    console.error("❌ Lỗi khi tạo chart:", err);
  }
})();