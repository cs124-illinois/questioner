import { MongoClient as mongo } from "mongodb"
import minimist from "minimist"
import log4js from "log4js"
import { Number, String } from "runtypes"
import seedRandom from "seedrandom"
import assert from "assert"

const args = minimist(process.argv.slice(2))

const count = Number.check(args._[0])
const random = seedRandom(String.check(args.seed ?? "seed"))

const logger = log4js.getLogger()
logger.level = "debug"

const _client = mongo.connect("mongodb://localhost:27017")
const _db = _client.then(c => c.db("test"))

Promise.resolve()
.then(async () => {
  const jspCollection = await _db.then(d => d.collection("stumperd_fixtures"))

  await jspCollection.createIndex({ rand: 1 })
  await jspCollection.updateMany(
    { rand: { $eq: null } },
    [{ $set: { rand: { $rand: {} } } }]
  )

  await jspCollection.createIndex({ semester: 1 })
  await jspCollection.createIndex({ semester: 1, rand: 1 })
  await jspCollection.createIndex({ semester: 1, type: 1, rand: 1 })
  await jspCollection.createIndex({ export: 1 })
  await jspCollection.createIndex({ export: 1, type: 1 })

  const semesters = await jspCollection.distinct("semester")

  await jspCollection.updateMany({ export: true }, { $unset: { export: 1 }})

  for (const semester of semesters) {
    while (true) {
      const threshold = random()
      const query = {
        semester,
        rand: { $gte: threshold },
        ...(!args.all && {
          type: "results"
        })
      }
      const ids = (await jspCollection.find(query).project({ _id: 1 }).limit(count).toArray()).map(({ _id }) => _id)
      if (ids.length === count) {
        await jspCollection.updateMany({ _id: { $in: ids }}, { $set: { export: true }})
        break
      }
    }
  }

  const exportCount = await jspCollection.countDocuments({ export: true })
  assert(exportCount === semesters.length * count)
})
.finally(async () => {
  const client = await _client
  try {
    client.close()
  } catch (err) {}
})
