const { writeFileSync } = require('fs');

const workflow = {
  steps: [
    {
      id: 'download',
      needs: [],
      tasks: [
        {
          type: 'DownloadUrl',
          url: 'https://download.geofabrik.de/europe/liechtenstein-latest.osm.pbf',
          path: 'liechtenstein-latest.osm.pbf'
        }
      ]
    },
    {
      id: 'import',
      needs: [
        'download'
      ],
      tasks: [
        {
          type: 'ImportOpenStreetMap',
          file: 'liechtenstein-latest.osm.pbf',
          database: 'jdbc:postgresql://localhost:5432/baremaps?&user=baremaps&password=baremaps',
          databaseSrid: 3857
        }
      ]
    },
    {
      id: 'index',
      needs: [
        'import'
      ],
      tasks: [
        {
          type: 'ExecuteSql',
          file: 'indexes.sql',
          database: 'jdbc:postgresql://localhost:5432/baremaps?&user=baremaps&password=baremaps'
        }
      ]
    }
  ]
};

const continents = [
  'africa',
  'antarctica',
  'asia',
  'australia-oceania',
  'central-america',
  'europe',
  'north-america',
  'south-america'
];

const { steps } = workflow;

workflow.steps = steps.map(step => {
  return {
    ...step,
    tasks: []
  };
});

for (const continent of continents) {
  workflow.steps[0].tasks.push(
    {
      ...steps[0].tasks[0],
      url: `https://download.geofabrik.de/${continent}-latest.osm.pbf`,
      path: `${continent}.osm.pbf`
    }
  );
  workflow.steps[1].tasks.push({
    ...steps[1].tasks[0],
    file: `${continent}.osm.pbf`
  });
}
workflow.steps[2].tasks.push(steps[2].tasks[0]);

writeFileSync('planet-workflow.json', JSON.stringify(workflow, null, 2));

writeFileSync('planet-download.json', JSON.stringify({
  steps: [
    {
      ...workflow.steps[0],
      needs: []
    }
  ]
}, null, 2));

writeFileSync('planet-import.json', JSON.stringify({
  steps: [
    {
      ...workflow.steps[1],
      needs: []
    }
  ]
}, null, 2));

writeFileSync('planet-index.json', JSON.stringify({
  steps: [
    {
      ...workflow.steps[2],
      needs: []
    }
  ]
}, null, 2));
