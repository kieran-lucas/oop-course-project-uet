const fs = require("fs");

async function fetchCommits() {
  let page = 1;
  let allCommits = [];

  while (true) {
    const res = await fetch(
      `https://api.github.com/repos/kieran-lucas/oop-course-project-uet/commits?per_page=100&page=${page}`
    );

    const data = await res.json();

    if (data.length === 0) break;

    allCommits = allCommits.concat(data);
    page++;
  }

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
  const max = Math.max(...Object.values(data));

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
  const commits = await fetchCommits();
  const grouped = groupByDate(commits);
  const svg = generateSVG(grouped);

  fs.writeFileSync("commit-chart.svg", svg);
})();